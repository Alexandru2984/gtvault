package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.config.AppProperties;
import gtshop.game.ContentBank;
import gtshop.service.BadgeService;
import gtshop.service.QuestService;
import gtshop.service.TimeService;
import gtshop.service.WalletService;
import gtshop.service.WordleService;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class WordleSnapshotIntegrationTest extends AbstractIntegrationTest {
    @Autowired WordleService wordle;
    @Autowired TimeService time;
    @Autowired ApplicationContext context;
    @Autowired PlatformTransactionManager transactions;

    private void day(int offset) { clock.set(START.plus(Duration.ofDays(offset))); }
    private JsonNode guess(TestUser user, String word) throws Exception {
        return readJson(mvc.perform(postJson("/api/games/wordle/guess", user, Map.of("word", word)))
                .andExpect(status().isOk()).andReturn());
    }
    private int puzzles() {
        return jdbc.queryForObject("SELECT count(*) FROM wordle_puzzles WHERE game_date = ?", Integer.class, time.todayIso());
    }
    private WordleService rotatedService(LocalDate day, String original) {
        for (int i = 0; i < 100; i++) {
            WordleService candidate = new WordleService(jdbc, context.getBean(WalletService.class),
                    context.getBean(BadgeService.class), context.getBean(QuestService.class), context.getBean(ContentBank.class),
                    time, new AppProperties(), ("isolated-rotation-" + i).repeat(3));
            if (!candidate.wordFor(day).word().equals(original)) return candidate;
        }
        throw new AssertionError("Could not construct a different candidate for the isolated day");
    }

    @Test
    void readsAndInvalidGuessesDoNotCreatePuzzlesAndPlayingNeverRevealsTheAnswer() throws Exception {
        day(100);
        TestUser user = register();
        String answer = wordle.wordFor(time.today()).word();
        mvc.perform(getAs("/api/games/wordle", user)).andExpect(status().isOk());
        assertThat(puzzles()).isZero();
        mvc.perform(postJson("/api/games/wordle/guess", user, Map.of("word", "bad"))).andExpect(status().isBadRequest());
        assertThat(puzzles()).isZero();
        JsonNode playing = guess(user, "ZZZZZ");
        assertThat(puzzles()).isEqualTo(1);
        assertThat(playing.has("answer")).isFalse();
        assertThat(playing.toString()).doesNotContain("\"" + answer + "\"");
        JsonNode anonymous = readJson(mvc.perform(getAs("/api/games/wordle", null)).andReturn());
        assertThat(anonymous.has("answer")).isFalse();
        assertThat(anonymous.path("guesses")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT puzzle_date FROM wordle_games WHERE user_id = ?", String.class, user.id())).isEqualTo(time.todayIso());
    }

    @Test
    void rotatingTheSecretPreservesTheCurrentRoundAndItsCompletedShareGrid() throws Exception {
        day(101);
        TestUser user = register();
        String answer = wordle.wordFor(time.today()).word();
        WordleService rotated = rotatedService(time.today(), answer);
        guess(user, "ZZZZZ");
        assertThat(rotated.wordFor(time.today()).word()).isEqualTo(answer);
        int before = points(user);
        Map<String, Object> result = new TransactionTemplate(transactions).execute(tx -> rotated.guess(user.id(), answer));
        assertThat(result.get("status")).isEqualTo("WON");
        assertThat(result.get("answer")).isEqualTo(answer);
        assertThat((String) result.get("shareText")).contains("2/6", "🟩🟩🟩🟩🟩").doesNotContain(answer);
        assertThat(points(user)).isEqualTo(before + 120);
        assertThat(rotated.state(Optional.of(user.id())).get("answer")).isEqualTo(answer);
    }

    @Test
    void concurrentFirstPlayersWithDifferentSecretsFreezeOneCommonPuzzle() throws Exception {
        day(102);
        TestUser first = register();
        TestUser second = register();
        String candidate = wordle.wordFor(time.today()).word();
        WordleService rotated = rotatedService(time.today(), candidate);
        var tx = new TransactionTemplate(transactions);
        List<Callable<Map<String, Object>>> requests = List.of(
                () -> tx.execute(t -> wordle.guess(first.id(), "ZZZZZ")),
                () -> tx.execute(t -> rotated.guess(second.id(), "ZZZZZ")));
        try (var executor = Executors.newFixedThreadPool(2)) {
            for (var response : executor.invokeAll(requests)) assertThat(response.get().get("status")).isEqualTo("PLAYING");
        }
        assertThat(puzzles()).isEqualTo(1);
        assertThat(wordle.wordFor(time.today()).word()).isEqualTo(rotated.wordFor(time.today()).word());
        assertThat(points(first)).isEqualTo(1000);
        assertThat(points(second)).isEqualTo(1000);
    }

    @Test
    void legacyVictoriesKeepTheirActualAnswerAndSeedTheDayForNewPlayers() throws Exception {
        day(103);
        TestUser old = register();
        TestUser newcomer = register();
        jdbc.update("INSERT INTO wordle_games (user_id, game_date, guesses, status, reward, updated_at) VALUES (?, ?, 'ZZZZZ,CACHE', 'WON', 120, ?)",
                old.id(), time.todayIso(), Timestamp.from(time.now()));
        assertThat(wordle.wordFor(time.today()).word()).isEqualTo("CACHE");
        JsonNode state = readJson(mvc.perform(getAs("/api/games/wordle", old)).andReturn());
        assertThat(state.path("answer").asText()).isEqualTo("CACHE");
        assertThat(state.path("shareText").asText()).contains("🟩🟩🟩🟩🟩").doesNotContain("CACHE");
        guess(newcomer, "ZZZZZ");
        assertThat(jdbc.queryForObject("SELECT answer FROM wordle_puzzles WHERE game_date = ?", String.class, time.todayIso())).isEqualTo("CACHE");
    }

    @Test
    void legacyDefeatsKeepTheirScoreWithoutInventingAnAnswerOrGrid() throws Exception {
        day(104);
        TestUser user = register();
        jdbc.update("INSERT INTO wordle_games (user_id, game_date, guesses, status, reward, updated_at) VALUES (?, ?, 'ZZZZZ,QQQQQ,XXXXX,JJJJJ,VVVVV,WWWWW', 'LOST', 0, ?)",
                user.id(), time.todayIso(), Timestamp.from(time.now()));
        JsonNode state = readJson(mvc.perform(getAs("/api/games/wordle", user)).andReturn());
        assertThat(state.path("status").asText()).isEqualTo("LOST");
        assertThat(state.path("gridAvailable").asBoolean()).isFalse();
        assertThat(state.has("answer")).isFalse();
        assertThat(state.at("/guesses/0/marks/0").asText()).isEqualTo("UNKNOWN");
        assertThat(state.path("shareText").asText()).endsWith("X/6").doesNotContain("ZZZZZ", "⬛", "🟩", "🟨");
        assertThat(puzzles()).isZero();
    }
}
