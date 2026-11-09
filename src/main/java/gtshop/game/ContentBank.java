package gtshop.game;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
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

    /** Intrebarea zilei pentru un utilizator, cu variantele deja amestecate. */
    public record DailyQuestion(QuizQuestion source, List<String> options, int correctIndex) {}

    private final List<QuizQuestion> quiz;
    private final List<Fortune> fortunes;

    public ContentBank(ObjectMapper mapper) throws IOException {
        this.quiz = List.copyOf(read(mapper, "content/quiz.json", new TypeReference<List<QuizQuestion>>() {}));
        this.fortunes = List.copyOf(read(mapper, "content/fortunes.json", new TypeReference<List<Fortune>>() {}));
        validate();
    }

    private static <T> T read(ObjectMapper mapper, String path, TypeReference<T> type) throws IOException {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8), type);
        }
    }

    private void validate() {
        if (quiz.isEmpty() || fortunes.isEmpty()) {
            throw new IllegalStateException("Continutul pentru quiz/ravas lipseste");
        }
        var ids = new HashSet<String>();
        for (QuizQuestion q : quiz) {
            if (!ids.add(q.id()) || q.options() == null || q.options().size() < 2
                    || q.answer() < 0 || q.answer() >= q.options().size()
                    || new HashSet<>(q.options()).size() != q.options().size()) {
                throw new IllegalStateException("Intrebare invalida in quiz.json: " + q.id());
            }
        }
    }

    public int quizSize() {
        return quiz.size();
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
