package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.service.WordleService;
import gtshop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class WordleIntegrationTest extends AbstractIntegrationTest {
    private static final List<String> WRONG = List.of("ZZZZZ", "QQQQQ", "XXXXX", "JJJJJ", "VVVVV", "WWWWW");

    @Autowired
    private WordleService wordle;

    private String answer() {
        return wordle.wordFor(java.time.LocalDate.ofInstant(clock.instant(), java.time.ZoneId.of("Europe/Bucharest"))).word();
    }

    private ResultActions guess(TestUser u, String word) throws Exception {
        return mvc.perform(postJson("/api/games/wordle/guess", u, Map.of("word", word)));
    }

    @Test
    void anonymousVisitorsSeeRulesButNeverTheAnswer() throws Exception {
        JsonNode r = readJson(mvc.perform(getAs("/api/games/wordle", null)).andExpect(status().isOk()).andReturn());
        assertThat(r.path("requiresLogin").asBoolean()).isTrue();
        assertThat(r.path("length").asInt()).isEqualTo(5);
        assertThat(r.has("answer")).isFalse();
        assertThat(r.toString()).doesNotContain("\"" + answer() + "\"");
        guess(null, "CACHE").andExpect(status().isUnauthorized());
    }

    @Test
    void winningAwardsPointsByAttemptsAndEndsTheRound() throws Exception {
        TestUser u = register();
        int before = points(u);
        JsonNode first = readJson(guess(u, WRONG.get(0)).andExpect(status().isOk()).andReturn());
        assertThat(first.path("status").asText()).isEqualTo("PLAYING");
        assertThat(first.has("answer")).isFalse();
        assertThat(first.path("lastMarks")).hasSize(5);
        assertThat(first.path("guessesLeft").asInt()).isEqualTo(5);
        assertThat(first.path("nextReward").asInt()).isEqualTo(120);
        assertThat(first.path("topicHint").isNull()).isTrue();

        guess(u, WRONG.get(0).toLowerCase()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("DUPLICATE_GUESS"));
        guess(u, "abc").andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_WORD"));
        guess(u, "ȘTACK").andExpect(status().isBadRequest());

        JsonNode win = readJson(guess(u, answer().toLowerCase()).andExpect(status().isOk()).andReturn());
        assertThat(win.path("status").asText()).isEqualTo("WON");
        assertThat(win.path("rewardPoints").asInt()).isEqualTo(120);
        assertThat(win.path("answer").asText()).isEqualTo(answer());
        assertThat(win.path("hint").asText()).isNotBlank();
        assertThat(win.path("shareText").asText()).contains("2/6").contains("🟩🟩🟩🟩🟩").doesNotContain(answer());
        assertThat(win.at("/stats/wins").asInt()).isEqualTo(1);
        assertThat(win.at("/stats/currentStreak").asInt()).isEqualTo(1);
        assertThat(win.at("/stats/distribution/1").asInt()).isEqualTo(1);
        assertThat(win.at("/community/solvers").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(points(u)).isEqualTo(before + 120);

        guess(u, "CLOUD").andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("GAME_OVER"));
        assertThat(points(u)).isEqualTo(before + 120);

        JsonNode state = readJson(mvc.perform(getAs("/api/games/wordle", u)).andExpect(status().isOk()).andReturn());
        assertThat(state.path("status").asText()).isEqualTo("WON");
        assertThat(state.path("guesses")).hasSize(2);
        assertThat(state.at("/keyboard/" + answer().charAt(0)).asText()).isEqualTo("HIT");
    }

    @Test
    void losingRevealsTheAnswerWithoutPoints() throws Exception {
        TestUser u = register();
        int before = points(u);
        JsonNode r = null;
        for (int i = 0; i < 6; i++) {
            r = readJson(guess(u, WRONG.get(i)).andExpect(status().isOk()).andReturn());
            if (i < 5) {
                assertThat(r.has("answer")).isFalse();
            }
            if (i >= 2 && i < 5) {
                assertThat(r.path("topicHint").asText()).isNotBlank();
            }
        }
        assertThat(r.path("status").asText()).isEqualTo("LOST");
        assertThat(r.path("answer").asText()).isEqualTo(answer());
        assertThat(r.path("shareText").asText()).contains("X/6");
        assertThat(r.path("rewardPoints").asInt()).isZero();
        assertThat(r.at("/stats/played").asInt()).isEqualTo(1);
        assertThat(r.at("/stats/currentStreak").asInt()).isZero();
        assertThat(points(u)).isEqualTo(before);
        guess(u, answer()).andExpect(status().isConflict());
    }

    @Test
    void sameWordForEveryoneAndStreakAcrossDaysUnlocksWordsmith() throws Exception {
        TestUser a = register();
        TestUser b = register();
        String today = answer();
        guess(b, today).andExpect(status().isOk()).andExpect(jsonPath("$.rewardPoints").value(150));

        int before = points(a);
        int expected = 0;
        for (int day = 0; day < 5; day++) {
            guess(a, answer()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WON"));
            expected += 150;
            if (day < 4) {
                advanceDays(1);
            }
        }
        JsonNode state = readJson(mvc.perform(getAs("/api/games/wordle", a)).andReturn());
        assertThat(state.at("/stats/wins").asInt()).isEqualTo(5);
        assertThat(state.at("/stats/currentStreak").asInt()).isEqualTo(5);
        assertThat(state.at("/stats/maxStreak").asInt()).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from user_badges where user_id = ? and badge_code = 'WORDSMITH'",
                Integer.class, a.id())).isEqualTo(1);
        // 5 victorii la prima incercare + insigna RARE (75 AP)
        assertThat(points(a)).isEqualTo(before + expected + 75);

        // O zi sarita rupe seria curenta, dar nu si maximul
        advanceDays(2);
        JsonNode later = readJson(mvc.perform(getAs("/api/games/wordle", a)).andReturn());
        assertThat(later.at("/stats/currentStreak").asInt()).isZero();
        assertThat(later.at("/stats/maxStreak").asInt()).isEqualTo(5);
        assertThat(later.path("status").asText()).isEqualTo("PLAYING");
    }

    @Test
    void finishingTheRoundCountsForWordleQuests() throws Exception {
        TestUser u = register();
        mvc.perform(getAs("/api/quests", u)).andExpect(status().isOk());
        guess(u, answer()).andExpect(status().isOk());
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select quest_type, current_progress from user_quests where user_id = ? and quest_key = 'PLAY_WORDLE'", u.id());
        for (Map<String, Object> row : rows) {
            assertThat(((Number) row.get("current_progress")).intValue()).isEqualTo(1);
        }
    }
}
