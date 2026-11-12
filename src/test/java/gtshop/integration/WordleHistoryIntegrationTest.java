package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.service.TimeService;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WordleHistoryIntegrationTest extends AbstractIntegrationTest {
    @Autowired TimeService time;

    @BeforeEach
    void isolatedCalendar() { clock.set(START.plus(Duration.ofDays(200))); }

    private String round(TestUser user, int daysAgo, String state, boolean snapshot) {
        String day = time.today().minusDays(daysAgo).toString();
        if (snapshot) jdbc.update("""
                INSERT INTO wordle_puzzles (game_date, answer, topic, hint, created_at)
                VALUES (?, 'CACHE', 'Memorie', 'Stocare temporară', ?) ON CONFLICT DO NOTHING
                """, day, Timestamp.from(time.now()));
        String guesses = state.equals("WON") ? "ZZZZZ,CACHE" : state.equals("LOST") ? "ZZZZZ,QQQQQ,XXXXX,JJJJJ,VVVVV,WWWWW" : "ZZZZZ";
        jdbc.update("""
                INSERT INTO wordle_games (user_id, game_date, guesses, status, reward, updated_at, puzzle_date)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, user.id(), day, guesses, state, state.equals("WON") ? 120 : 0,
                Timestamp.from(time.now()), snapshot ? day : null);
        return day;
    }

    @Test
    void historyAndReplayRequireAuthentication() throws Exception {
        mvc.perform(getAs("/api/games/wordle/history", null)).andExpect(status().isUnauthorized());
        mvc.perform(getAs("/api/games/wordle/history/2026-11-09", null)).andExpect(status().isUnauthorized());
    }

    @Test
    void paginationAndFiltersReturnOnlyOwnCompletedRoundsUpToToday() throws Exception {
        TestUser user = register();
        TestUser other = register();
        String newest = round(user, 1, "WON", true);
        round(user, 2, "LOST", true);
        round(user, 3, "WON", true);
        round(user, 0, "PLAYING", true);
        round(user, -1, "WON", true);
        round(other, 4, "WON", true);
        JsonNode first = readJson(mvc.perform(getAs("/api/games/wordle/history?size=2", user))
                .andExpect(status().isOk()).andReturn());
        assertThat(first.path("totalItems").asLong()).isEqualTo(3);
        assertThat(first.path("totalPages").asLong()).isEqualTo(2);
        assertThat(first.path("items")).hasSize(2);
        assertThat(first.at("/items/0/date").asText()).isEqualTo(newest);
        assertThat(first.toString()).doesNotContain("CACHE", "ZZZZZ", user.email(), other.id(), "\"answer\"", "\"guesses\"");
        mvc.perform(getAs("/api/games/wordle/history?size=2&page=1", user)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(getAs("/api/games/wordle/history?status=won", user)).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(getAs("/api/games/wordle/history?status=LOST", user)).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(getAs("/api/games/wordle/history?status=PLAYING", user)).andExpect(status().isBadRequest());
        mvc.perform(getAs("/api/games/wordle", user)).andExpect(jsonPath("$.stats.played").value(3))
                .andExpect(jsonPath("$.stats.wins").value(2));
    }

    @Test
    void hugePageNumbersDoNotOverflowAndPageSizeIsBounded() throws Exception {
        TestUser user = register();
        round(user, 1, "WON", true);
        mvc.perform(getAs("/api/games/wordle/history?page=2147483647&size=2147483647", user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.size").value(30)).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(getAs("/api/games/wordle/history?page=-1&size=0", user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void replayIsPrivateAndCannotGrantPointsOrQuestsAgain() throws Exception {
        TestUser user = register();
        TestUser other = register();
        String day = round(user, 1, "WON", true);
        int before = points(user);
        int ledger = jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE user_id = ?", Integer.class, user.id());
        for (int i = 0; i < 5; i++) {
            JsonNode replay = readJson(mvc.perform(getAs("/api/games/wordle/history/" + day, user))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.readOnly").value(true)).andReturn());
            assertThat(replay.path("answer").asText()).isEqualTo("CACHE");
            assertThat(replay.path("guesses")).hasSize(2);
            assertThat(replay.path("shareText").asText()).contains("2/6", "🟩🟩🟩🟩🟩").doesNotContain("CACHE", "ZZZZZ");
        }
        mvc.perform(getAs("/api/games/wordle/history/" + day + "?userId=" + user.id(), other)).andExpect(status().isNotFound());
        assertThat(points(user)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE user_id = ?", Integer.class, user.id())).isEqualTo(ledger);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_quests WHERE user_id = ?", Integer.class, user.id())).isZero();
    }

    @Test
    void unfinishedFutureMissingAndInvalidDatesNeverRevealSolutions() throws Exception {
        TestUser user = register();
        String today = round(user, 0, "PLAYING", true);
        String future = round(user, -1, "WON", true);
        for (String day : new String[]{today, future, time.today().minusDays(50).toString()}) {
            mvc.perform(getAs("/api/games/wordle/history/" + day, user)).andExpect(status().isNotFound());
        }
        mvc.perform(getAs("/api/games/wordle/history/2026-02-30", user)).andExpect(status().isBadRequest());
        mvc.perform(getAs("/api/games/wordle/history/not-a-date", user)).andExpect(status().isBadRequest());
    }

    @Test
    void legacyDefeatKeepsTheResultButDoesNotBorrowAnotherRoundsSolution() throws Exception {
        TestUser user = register();
        TestUser other = register();
        String day = round(user, 2, "LOST", false);
        round(other, 2, "WON", true);
        JsonNode replay = readJson(mvc.perform(getAs("/api/games/wordle/history/" + day, user)).andExpect(status().isOk()).andReturn());
        assertThat(replay.path("gridAvailable").asBoolean()).isFalse();
        assertThat(replay.has("answer")).isFalse();
        assertThat(replay.path("shareText").asText()).endsWith("X/6").doesNotContain("CACHE", "ZZZZZ", "⬛");
        mvc.perform(getAs("/api/games/wordle/history", user)).andExpect(jsonPath("$.items[0].gridAvailable").value(false));
    }

    @Test
    void personalExportIncludesRoundMetadataWithoutTheUnfinishedSolution() throws Exception {
        TestUser user = register();
        String day = round(user, 0, "PLAYING", true);
        JsonNode exported = readJson(mvc.perform(getAs("/api/user/export", user)).andExpect(status().isOk()).andReturn());
        JsonNode game = exported.path("wordle_games").get(0);
        assertThat(game.path("puzzle_date").asText()).isEqualTo(day);
        assertThat(game.has("updated_at")).isTrue();
        assertThat(game.toString()).doesNotContain("CACHE", "answer", "hint");
    }
}
