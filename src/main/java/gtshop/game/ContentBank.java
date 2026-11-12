package gtshop.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Banca de intrebari pentru quiz si citatele pentru „răvașul zilei”, incarcate din resources/content.
 *
 * <p>Fiecare utilizator primeste zilnic alta intrebare (parcurge toata banca inainte de repetari),
 * iar ordinea variantelor este amestecata determinist per utilizator si zi, deci pozitia raspunsului
 * corect nu poate fi ghicita.</p>
 */
@Component
public class ContentBank {

    public record QuizQuestion(String id, String category, String question, List<String> options, int answer, String explanation) {}

    public record Fortune(String id, String quote, String author, String tip) {}

    /** Un cuvant din Tech Wordle: tema (indiciu dupa 3 incercari) si explicatia afisata la final. */
    public record WordleWord(String word, String topic, String hint) {}

    /** Intrebarea zilei pentru un utilizator, cu variantele deja amestecate. */
    public record DailyQuestion(QuizQuestion source, List<String> options, int correctIndex) {
        /** Amprenta exclusiv a conținutului vizibil; nu include răspunsul sau explicația ascunsă. */
        public String version() {
            try {
                var digest = java.security.MessageDigest.getInstance("SHA-256");
                List<String> fields = new ArrayList<>(List.of(source.id(), source.category(), source.question()));
                fields.addAll(options);
                for (String field : fields) {
                    byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                    digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                    digest.update(bytes);
                }
                return java.util.HexFormat.of().formatHex(digest.digest());
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static final Logger log = LoggerFactory.getLogger(ContentBank.class);

    private final List<QuizQuestion> quiz;
    private final List<Fortune> fortunes;
    private final List<WordleWord> words;

    public ContentBank(ObjectMapper mapper) throws IOException {
        this(mapper, "");
    }

    @Autowired
    public ContentBank(ObjectMapper mapper, gtshop.config.AppProperties props) throws IOException {
        this(mapper, props.getContentDir());
    }

    private ContentBank(ObjectMapper mapper, String contentDir) throws IOException {
        this.quiz = List.copyOf(read(mapper, contentDir, "quiz.json", new TypeReference<List<QuizQuestion>>() {}));
        this.fortunes = List.copyOf(read(mapper, contentDir, "fortunes.json", new TypeReference<List<Fortune>>() {}));
        this.words = List.copyOf(read(mapper, contentDir, "wordle.json", new TypeReference<List<WordleWord>>() {}));
        validate();
    }

    /** Fisierul din directorul privat are prioritate; altfel se foloseste continutul implicit din aplicatie. */
    private static <T> T read(ObjectMapper mapper, String contentDir, String name, TypeReference<T> type) throws IOException {
        if (contentDir != null && !contentDir.isBlank()) {
            Path external = Path.of(contentDir, name);
            if (Files.isRegularFile(external)) {
                log.info("Continut incarcat din {}", external);
                return mapper.readValue(Files.readString(external, StandardCharsets.UTF_8), type);
            }
        }
        try (InputStream in = new ClassPathResource("content/" + name).getInputStream()) {
            return mapper.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
        }
    }

    private void validate() {
        if (quiz.isEmpty() || fortunes.isEmpty() || words.isEmpty()) {
            throw new IllegalStateException("Continutul pentru quiz/ravas/wordle lipseste");
        }
        var wordSet = new HashSet<String>();
        for (WordleWord w : words) {
            if (Wordle.normalize(w.word()) == null || !w.word().equals(Wordle.normalize(w.word())) || !wordSet.add(w.word())
                    || w.topic() == null || w.topic().isBlank() || w.hint() == null || w.hint().isBlank()) {
                throw new IllegalStateException("Cuvant invalid in wordle.json: " + w.word());
            }
        }
        var ids = new HashSet<String>();
        for (QuizQuestion q : quiz) {
            if (q.id() == null || q.id().isBlank() || !ids.add(q.id())
                    || q.category() == null || q.category().isBlank() || q.category().length() > 100
                    || q.question() == null || q.question().isBlank() || q.explanation() == null || q.explanation().isBlank()
                    || q.options() == null || q.options().size() < 2 || q.options().size() > 10
                    || q.options().stream().anyMatch(o -> o == null || o.isBlank())
                    || q.answer() < 0 || q.answer() >= q.options().size()
                    || new HashSet<>(q.options()).size() != q.options().size()) {
                throw new IllegalStateException("Intrebare invalida in quiz.json: " + q.id());
            }
        }
    }

    public int quizSize() {
        return quiz.size();
    }

    public List<WordleWord> words() {
        return words;
    }

    public DailyQuestion questionFor(String seedKey, LocalDate day) {
        int offset = Math.floorMod(stableHash(seedKey), quiz.size());
        QuizQuestion q = quiz.get(Math.floorMod(day.toEpochDay() + offset, quiz.size()));
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < q.options().size(); i++) {
            order.add(i);
        }
        java.util.Collections.shuffle(order, new Random(stableHash(seedKey + "|" + day + "|" + q.id())));
        List<String> shuffled = new ArrayList<>();
        int correct = -1;
        for (int i = 0; i < order.size(); i++) {
            shuffled.add(q.options().get(order.get(i)));
            if (order.get(i) == q.answer()) {
                correct = i;
            }
        }
        return new DailyQuestion(q, List.copyOf(shuffled), correct);
    }

    public Fortune fortuneFor(String seedKey, LocalDate day) {
        int offset = Math.floorMod(stableHash("fortune|" + seedKey), fortunes.size());
        return fortunes.get(Math.floorMod(day.toEpochDay() + offset, fortunes.size()));
    }

    /** Hash stabil intre reporniri (String.hashCode este specificat), amestecat pentru distributie mai buna. */
    public static int stableHash(String s) {
        int h = s.hashCode();
        h ^= (h >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        h *= 0xc2b2ae35;
        h ^= (h >>> 16);
        return h;
    }
}
