package gtshop.game;

import static gtshop.game.Wordle.Mark.HIT;
import static gtshop.game.Wordle.Mark.MISS;
import static gtshop.game.Wordle.Mark.NEAR;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WordleRulesTest {
    private static final byte[] SECRET = "secret-de-test-suficient-de-lung".getBytes(StandardCharsets.UTF_8);

    @Test
    void scoresExactAndMisplacedLetters() {
        assertThat(Wordle.score("CACHE", "CACHE")).containsExactly(HIT, HIT, HIT, HIT, HIT);
        assertThat(Wordle.score("STACK", "TASKS")).containsExactly(NEAR, NEAR, NEAR, MISS, NEAR);
        assertThat(Wordle.score("ZZZZZ", "ARRAY")).containsExactly(MISS, MISS, MISS, MISS, MISS);
    }

    @Test
    void duplicateLettersAreLimitedToOccurrencesInAnswer() {
        // ARRAY are doi R: ambii sunt deja nimeriti exact, deci al treilea R nu mai primeste NEAR
        assertThat(Wordle.score("RRRZZ", "ARRAY")).containsExactly(MISS, HIT, HIT, MISS, MISS);
        // Doi R pe pozitii gresite primesc ambii NEAR; un al treilea nu
        assertThat(Wordle.score("RZZRR", "ARRAY")).containsExactly(NEAR, MISS, MISS, NEAR, MISS);
        // Litera pe pozitia corecta are prioritate fata de aceeasi litera pe pozitie gresita
        assertThat(Wordle.score("EERIE", "CACHE")).containsExactly(MISS, MISS, MISS, MISS, HIT);
        assertThat(Wordle.score("LLAMA", "LOCAL")).containsExactly(HIT, NEAR, NEAR, MISS, MISS);
    }

    @Test
    void normalizesOnlyFiveAsciiLetters() {
        assertThat(Wordle.normalize(" cache ")).isEqualTo("CACHE");
        assertThat(Wordle.normalize("cach")).isNull();
        assertThat(Wordle.normalize("caches")).isNull();
        assertThat(Wordle.normalize("ca he")).isNull();
        assertThat(Wordle.normalize("ștack")).isNull();
        assertThat(Wordle.normalize("12345")).isNull();
        assertThat(Wordle.normalize(null)).isNull();
    }

    @Test
    void everyWordAppearsOncePerCycleAndOrderDependsOnSecret() {
        int size = 37;
        LocalDate cycleStart = LocalDate.ofEpochDay(size * 600L);
        Set<Integer> seen = new HashSet<>();
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int idx = Wordle.dailyIndex(cycleStart.plusDays(i), size, SECRET);
            assertThat(idx).isBetween(0, size - 1);
            seen.add(idx);
            order.add(idx);
        }
        assertThat(seen).hasSize(size);

        List<Integer> other = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            other.add(Wordle.dailyIndex(cycleStart.plusDays(i), size, "alt-secret-complet-diferit-123456".getBytes(StandardCharsets.UTF_8)));
        }
        assertThat(other).isNotEqualTo(order);
        // Determinist pentru acelasi secret
        assertThat(Wordle.dailyIndex(cycleStart, size, SECRET)).isEqualTo(order.get(0));
    }

    @Test
    void shareGridHasNoLetters() {
        assertThat(Wordle.emojiRow(List.of(HIT, NEAR, MISS, MISS, HIT))).isEqualTo("🟩🟨⬛⬛🟩");
    }

    @Test
    void bundledWordBankIsValid() throws Exception {
        ContentBank bank = new ContentBank(new ObjectMapper());
        assertThat(bank.words()).hasSizeGreaterThan(100);
        assertThat(bank.words()).allSatisfy(w -> assertThat(Wordle.normalize(w.word())).isEqualTo(w.word()));
    }
}
