package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.game.ContentBank;
import gtshop.service.TimeService;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class QuizHistoryIntegrationTest extends AbstractIntegrationTest {
    @MockitoSpyBean ContentBank content;
    @Autowired TimeService time;
    private static final String HISTORY = "/api/gamification/quiz/history";

    private JsonNode answer(TestUser user, boolean correct) throws Exception {
        var q = content.questionFor(user.id(), time.today());
        int selected = correct ? q.correctIndex() : (q.correctIndex() + 1) % q.options().size();
        return readJson(mvc.perform(postJson("/api/gamification/quiz/submit", user,
                Map.of("questionId", q.source().id(), "questionVersion", q.version(), "answerIndex", selected)))
                .andExpect(status().isOk()).andReturn());
    }

    private void fixture(TestUser user, int daysAgo, String category, boolean correct) {
        jdbc.update("""
                INSERT INTO quiz_attempts (user_id, game_date, question_id, category, question, options,
                    selected_index, correct_index, explanation, reward_points, answered_at)
                VALUES (?, ?, 'synthetic', ?, 'Întrebare sintetică?', '["Prima", "A doua"]', ?, 0, 'Explicație sintetică.', ?, ?)
                """, user.id(), time.today().minusDays(daysAgo), category, correct ? 0 : 1, correct ? 100 : 0, Timestamp.from(time.now()));
    }

    @Test
    void previewsAndRejectedAnswersNeverSaveOrLeakASolution() throws Exception {
        TestUser user = register();
        JsonNode preview = readJson(mvc.perform(getAs("/api/gamification/quiz", user)).andExpect(status().isOk()).andReturn());
        assertThat(preview.path("questionVersion").asText()).hasSize(64);
        assertThat(preview.has("explanation") || preview.has("correctIndex") || preview.has("selectedIndex")).isFalse();
        mvc.perform(getAs(HISTORY, null)).andExpect(status().isUnauthorized());
        mvc.perform(getAs(HISTORY + "/" + time.todayIso(), null)).andExpect(status().isUnauthorized());
        mvc.perform(getAs(HISTORY, user)).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.summary.answered").value(0));
        mvc.perform(getAs(HISTORY + "/" + time.todayIso(), user)).andExpect(status().isNotFound());
        mvc.perform(postJson("/api/gamification/quiz/submit", user, Map.of("answerIndex", 100))).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/gamification/quiz/submit", user, Map.of("answerIndex", 0, "questionVersion", "obsolete")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("QUIZ_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quiz_attempts WHERE user_id = ?", Integer.class, user.id())).isZero();
        assertThat(points(user)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT last_quiz_date FROM users WHERE id = ?", String.class, user.id())).isNull();
    }

    @Test
    void savedResultSurvivesChangesToContentAndRestoresSelectedOption() throws Exception {
        TestUser user = register();
        var q = content.questionFor(user.id(), time.today());
        int picked = (q.correctIndex() + 1) % q.options().size();
        answer(user, false);
        var changed = new ContentBank.DailyQuestion(new ContentBank.QuizQuestion(q.source().id(), "Modificată", "Altă întrebare?",
                List.of("Unu", "Doi"), 0, "Explicație nouă"), List.of("Doi", "Unu"), 1);
        doReturn(changed).when(content).questionFor(user.id(), time.today());
        JsonNode saved = readJson(mvc.perform(getAs("/api/gamification/quiz", user)).andExpect(status().isOk()).andReturn());
        assertThat(saved.path("question").asText()).isEqualTo(q.source().question());
        assertThat(saved.path("selectedIndex").asInt()).isEqualTo(picked);
        assertThat(saved.path("correctIndex").asInt()).isEqualTo(q.correctIndex());
        assertThat(saved.path("explanation").asText()).isEqualTo(q.source().explanation());
        assertThat(saved.path("alreadyAnswered").asBoolean()).isTrue();
        assertThat(saved.path("wasCorrect").asBoolean()).isFalse();
        assertThat(saved.path("rewardPoints").asInt()).isZero();
        JsonNode detail = readJson(mvc.perform(getAs(HISTORY + "/" + time.todayIso(), user)).andExpect(status().isOk()).andReturn());
        assertThat(detail.path("question")).isEqualTo(saved.path("question"));
        assertThat(detail.path("options")).isEqualTo(saved.path("options"));
    }

    @Test
    void changedVisibleOptionsAreRejectedEvenWhenTheQuestionIdIsUnchanged() throws Exception {
        TestUser user = register();
        var q = content.questionFor(user.id(), time.today());
        var options = new ArrayList<>(q.options());
        java.util.Collections.swap(options, 0, 1);
        var changed = new ContentBank.DailyQuestion(q.source(), options, q.correctIndex() == 0 ? 1 : q.correctIndex() == 1 ? 0 : q.correctIndex());
        doReturn(changed).when(content).questionFor(user.id(), time.today());
        assertThat(changed.version()).isNotEqualTo(q.version());
        mvc.perform(postJson("/api/gamification/quiz/submit", user,
                Map.of("questionId", q.source().id(), "questionVersion", q.version(), "answerIndex", 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("QUIZ_CHANGED"));
        assertThat(points(user)).isEqualTo(1000);
        mvc.perform(getAs(HISTORY, user)).andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void filtersPaginationAndSummaryUseOnlyTheOwnersNonFutureAttempts() throws Exception {
        TestUser user = register();
        TestUser other = register();
        fixture(user, 1, "Java", true);
        fixture(user, 2, "Java", false);
        fixture(user, 3, "Web", false);
        fixture(user, -1, "Future", true);
        fixture(other, 1, "Private", true);
        JsonNode first = readJson(mvc.perform(getAs(HISTORY + "?size=2", user)).andExpect(status().isOk()).andReturn());
        assertThat(first.path("totalItems").asInt()).isEqualTo(3);
        assertThat(first.path("totalPages").asInt()).isEqualTo(2);
        assertThat(first.path("items")).hasSize(2);
        assertThat(first.at("/items/0/date").asText()).isEqualTo(time.today().minusDays(1).toString());
        assertThat(first.at("/summary/answered").asInt()).isEqualTo(3);
        assertThat(first.at("/summary/correct").asInt()).isEqualTo(1);
        assertThat(first.at("/summary/rewardPoints").asInt()).isEqualTo(100);
        assertThat(first.at("/summary/categories")).hasSize(2);
        assertThat(first.toString()).doesNotContain("Future", "Private", "options", "correctIndex", "explanation", other.id());
        mvc.perform(getAs(HISTORY + "?size=2&page=1", user)).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(getAs(HISTORY + "?category=Java&result=wrong", user)).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.summary.answered").value(3));
        mvc.perform(getAs(HISTORY + "?result=CORRECT", user)).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(getAs(HISTORY + "?category='OR'1'='1", user)).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(getAs(HISTORY + "?result=INVALID", user)).andExpect(status().isBadRequest());
        mvc.perform(getAs(HISTORY + "?category=" + "a".repeat(101), user)).andExpect(status().isBadRequest());
        mvc.perform(getAs(HISTORY + "?page=2147483647&size=2147483647", user)).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.size").value(20));
        mvc.perform(getAs(HISTORY + "?page=-1&size=0", user)).andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(1));
    }

    @Test
    void reviewingCannotAccessOtherAccountsOrGrantPointsAndQuestsAgain() throws Exception {
        TestUser user = register();
        TestUser other = register();
        answer(user, true);
        String path = HISTORY + "/" + time.todayIso();
        int balance = points(user);
        int ledger = jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE user_id = ?", Integer.class, user.id());
        long questProgress = jdbc.queryForObject("SELECT COALESCE(sum(current_progress), 0) FROM user_quests WHERE user_id = ?", Long.class, user.id());
        for (int i = 0; i < 5; i++) mvc.perform(getAs(path, user)).andExpect(status().isOk()).andExpect(jsonPath("$.rewardPoints").value(100));
        mvc.perform(getAs(path + "?userId=" + user.id(), other)).andExpect(status().isNotFound());
        mvc.perform(getAs(HISTORY + "/2026-02-30", user)).andExpect(status().isBadRequest());
        fixture(user, -1, "Future", true);
        mvc.perform(getAs(HISTORY + "/" + time.today().plusDays(1), user)).andExpect(status().isNotFound());
        assertThat(points(user)).isEqualTo(balance);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE user_id = ?", Integer.class, user.id())).isEqualTo(ledger);
        assertThat(jdbc.queryForObject("SELECT COALESCE(sum(current_progress), 0) FROM user_quests WHERE user_id = ?", Long.class, user.id())).isEqualTo(questProgress);
    }

    @Test
    void concurrentSubmissionsCreateOneSnapshotAndOneReward() throws Exception {
        TestUser user = register();
        var q = content.questionFor(user.id(), time.today());
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) calls.add(() -> mvc.perform(postJson("/api/gamification/quiz/submit", user,
                    Map.of("questionVersion", q.version(), "answerIndex", q.correctIndex()))).andReturn().getResponse().getStatus());
            var futures = pool.invokeAll(calls, 30, TimeUnit.SECONDS);
            List<Integer> statuses = new ArrayList<>();
            for (var future : futures) statuses.add(future.get());
            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(7);
        } finally { pool.shutdownNow(); }
        assertThat(points(user)).isEqualTo(1125);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quiz_attempts WHERE user_id = ?", Integer.class, user.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE user_id = ? AND type = 'QUIZ_REWARD'", Integer.class, user.id())).isEqualTo(1);
    }

    @Test
    void exportContainsTheRecordedQuestionAndAccountDeletionCascades() throws Exception {
        TestUser user = register();
        answer(user, false);
        JsonNode exported = readJson(mvc.perform(getAs("/api/user/export", user)).andExpect(status().isOk()).andReturn());
        JsonNode attempt = exported.path("quiz_attempts").get(0);
        assertThat(attempt.has("answered_at")).isTrue();
        assertThat(json.readTree(attempt.path("options").asText())).hasSize(4);
        assertThat(attempt.path("explanation").asText()).isNotBlank();
        assertThat(attempt.has("user_id")).isFalse();
        mvc.perform(postJson("/api/user/account/delete", user, Map.of("password", "Parola-Sigura-123", "confirmation", "STERGE")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quiz_attempts WHERE user_id = ?", Integer.class, user.id())).isZero();
    }

    @Test
    void legacyAnswersRemainConsumedWithoutInventingHistoricalAttempts() throws Exception {
        TestUser user = register();
        jdbc.update("UPDATE users SET last_quiz_date = ? WHERE id = ?", time.todayIso(), user.id());
        mvc.perform(getAs("/api/gamification/quiz", user)).andExpect(jsonPath("$.alreadyAnswered").value(true));
        mvc.perform(getAs(HISTORY, user)).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(postJson("/api/gamification/quiz/submit", user, Map.of("answerIndex", 0)))
                .andExpect(status().isConflict());
        clock.set(START.plus(Duration.ofDays(1)));
        answer(user, false);
        mvc.perform(getAs(HISTORY, user)).andExpect(jsonPath("$.totalItems").value(1));
    }
}
