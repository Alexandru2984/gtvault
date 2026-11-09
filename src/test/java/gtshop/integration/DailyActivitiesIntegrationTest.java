package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.game.ContentBank;
import gtshop.service.TimeService;
import gtshop.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DailyActivitiesIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    ContentBank content;
    @Autowired
    TimeService time;

    private JsonNode claim(TestUser u) throws Exception {
        return readJson(mvc.perform(postJson("/api/gamification/daily-claim", u, null)).andExpect(status().isOk()).andReturn());
    }

    @Test
    void streakGrowsOnConsecutiveDaysAndResetsAfterGap() throws Exception {
        TestUser u = register();
        assertThat(claim(u).path("bonusPoints").asInt()).isEqualTo(50);
        mvc.perform(postJson("/api/gamification/daily-claim", u, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALREADY_CLAIMED"));

        advanceDays(1);
        JsonNode day2 = claim(u);
        assertThat(day2.path("streakDays").asInt()).isEqualTo(2);
        assertThat(day2.path("bonusPoints").asInt()).isEqualTo(75);

        advanceDays(2); // a ratat o zi, fara freeze
        JsonNode reset = claim(u);
        assertThat(reset.path("streakDays").asInt()).isEqualTo(1);
        assertThat(reset.path("streakReset").asBoolean()).isTrue();
        // 1000 bun venit + 50 + 75 + 50 + insigna nu (streak maxim 2)
        assertThat(points(u)).isEqualTo(1175);
        // streak-ul conteaza ca XP, bonusul de bun venit nu
        assertThat(xp(u)).isEqualTo(175);
    }

    @Test
    void streakDayUsesRomanianMidnight() throws Exception {
        TestUser u = register();
        clock.set(java.time.Instant.parse("2026-11-09T21:30:00Z")); // 23:30 in Romania
        claim(u);
        clock.set(java.time.Instant.parse("2026-11-09T22:30:00Z")); // 00:30 a doua zi in Romania
        assertThat(claim(u).path("streakDays").asInt()).isEqualTo(2);
    }

    @Test
    void streakFreezeProtectsMissedDay() throws Exception {
        TestUser u = register();
        claim(u);
        mvc.perform(postJson("/api/gamification/streak-freeze", u, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.streakFreezes").value(1));
        mvc.perform(postJson("/api/gamification/streak-freeze", u, null)).andExpect(status().isOk());
        mvc.perform(postJson("/api/gamification/streak-freeze", u, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("MAX_FREEZES"));
        assertThat(points(u)).isEqualTo(1000 + 50 - 600);

        advanceDays(2);
        JsonNode status = readJson(mvc.perform(getAs("/api/gamification/status", u)).andExpect(status().isOk()).andReturn());
        assertThat(status.path("streakDays").asInt()).isEqualTo(1);
        assertThat(status.path("freezesWillBeUsed").asInt()).isEqualTo(1);
        assertThat(status.path("streakWillReset").asBoolean()).isFalse();

        JsonNode res = claim(u);
        assertThat(res.path("streakDays").asInt()).isEqualTo(2);
        assertThat(res.path("freezesUsed").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select streak_freezes from users where id = ?", Integer.class, u.id())).isEqualTo(1);
    }

    @Test
    void sevenDayCycleAndBadges() throws Exception {
        TestUser u = register();
        int total = 0;
        for (int d = 1; d <= 8; d++) {
            JsonNode r = claim(u);
            total += r.path("bonusPoints").asInt();
            if (d == 7) {
                assertThat(r.path("bonusPoints").asInt()).isEqualTo(500);
                assertThat(r.path("badgesUnlocked").toString()).contains("STREAK_7");
            }
            if (d == 8) {
                assertThat(r.path("bonusPoints").asInt()).isEqualTo(50);
            }
            advanceDays(1);
        }
        assertThat(total).isEqualTo(50 + 75 + 100 + 150 + 200 + 250 + 500 + 50);
        // insignele STREAK_3 (25) + STREAK_7 (75) se adauga la sold
        assertThat(points(u)).isEqualTo(1000 + total + 25 + 75);
    }

    @Test
    void concurrentClaimsPayOnlyOnce() throws Exception {
        TestUser u = register();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                calls.add(() -> mvc.perform(postJson("/api/gamification/daily-claim", u, null)).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
            assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(7);
        } finally {
            pool.shutdownNow();
        }
        assertThat(points(u)).isEqualTo(1050);
        assertThat(jdbc.queryForObject("select count(*) from point_transactions where user_id = ? and type = 'DAILY_STREAK'",
                Integer.class, u.id())).isEqualTo(1);
    }

    @Test
    void quizRewardsCorrectAnswerOncePerDay() throws Exception {
        TestUser u = register();
        JsonNode q = readJson(mvc.perform(getAs("/api/gamification/quiz", u)).andExpect(status().isOk()).andReturn());
        assertThat(q.has("correctIndex")).isFalse();
        assertThat(q.path("options")).hasSize(4);
        int correct = content.questionFor(u.id(), time.today()).correctIndex();

        JsonNode res = readJson(mvc.perform(postJson("/api/gamification/quiz/submit", u,
                Map.of("questionId", q.path("questionId").asText(), "answerIndex", correct))).andExpect(status().isOk()).andReturn());
        assertThat(res.path("isCorrect").asBoolean()).isTrue();
        assertThat(res.path("badgesUnlocked").toString()).contains("TECH_BRAIN");
        assertThat(points(u)).isEqualTo(1000 + 100 + 25);

        mvc.perform(postJson("/api/gamification/quiz/submit", u, Map.of("answerIndex", correct)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALREADY_ANSWERED"));
        JsonNode after = readJson(mvc.perform(getAs("/api/gamification/quiz", u)).andReturn());
        assertThat(after.path("alreadyAnswered").asBoolean()).isTrue();
        assertThat(after.path("wasCorrect").asBoolean()).isTrue();
        assertThat(after.path("correctIndex").asInt()).isEqualTo(correct);
    }

    @Test
    void wrongQuizAnswerGivesNothingAndInvalidIndexIsRejected() throws Exception {
        TestUser u = register();
        int correct = content.questionFor(u.id(), time.today()).correctIndex();
        mvc.perform(postJson("/api/gamification/quiz/submit", u, Map.of("answerIndex", 9))).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/gamification/quiz/submit", u, Map.of("questionId", "q-alta", "answerIndex", 0)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("QUIZ_CHANGED"));
        mvc.perform(postJson("/api/gamification/quiz/submit", u, Map.of("answerIndex", (correct + 1) % 4)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isCorrect").value(false));
        assertThat(points(u)).isEqualTo(1000);
    }

    @Test
    void anonymousCanPreviewQuizWithoutAnswer() throws Exception {
        mvc.perform(getAs("/api/gamification/quiz", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.requiresLogin").value(true))
                .andExpect(jsonPath("$.correctIndex").doesNotExist());
    }

    @Test
    void fortuneOncePerDay() throws Exception {
        TestUser u = register();
        mvc.perform(postJson("/api/gamification/fortune-cookie", u, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.quote").isNotEmpty()).andExpect(jsonPath("$.bonusPoints").value(20));
        mvc.perform(postJson("/api/gamification/fortune-cookie", u, null)).andExpect(status().isConflict());
        advanceDays(1);
        mvc.perform(postJson("/api/gamification/fortune-cookie", u, null)).andExpect(status().isOk());
        assertThat(points(u)).isEqualTo(1040);
    }

    @Test
    void statusDescribesTheDay() throws Exception {
        TestUser u = register();
        JsonNode s = readJson(mvc.perform(getAs("/api/gamification/status", u)).andExpect(status().isOk()).andReturn());
        assertThat(s.path("canClaimDaily").asBoolean()).isTrue();
        assertThat(s.path("nextDailyBonus").asInt()).isEqualTo(50);
        assertThat(s.path("freeSpinAvailable").asBoolean()).isTrue();
        assertThat(s.path("paidSpinsLeft").asInt()).isEqualTo(10);
        assertThat(s.path("cratesLeft").asInt()).isEqualTo(5);
        assertThat(s.path("weekCycle")).hasSize(7);
        claim(u);
        JsonNode s2 = readJson(mvc.perform(getAs("/api/gamification/status", u)).andReturn());
        assertThat(s2.path("canClaimDaily").asBoolean()).isFalse();
        assertThat(s2.path("streakDays").asInt()).isEqualTo(1);
        assertThat(s2.path("nextDailyBonus").asInt()).isEqualTo(75);
        assertThat(s2.at("/weekCycle/0/claimed").asBoolean()).isTrue();
        assertThat(s2.at("/weekCycle/1/claimed").asBoolean()).isFalse();
    }
}
