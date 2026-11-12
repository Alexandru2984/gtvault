package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.service.SavingGoalAlerts;
import gtshop.service.SavingGoalService;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class SavingGoalIntegrationTest extends AbstractIntegrationTest {
    @Autowired SavingGoalService goals;
    @Autowired SavingGoalAlerts alerts;

    private String reward(int price, int stock) {
        String id = "goal-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rewards (id, name, description, price, category, in_stock, stock_count, is_new,
                    active, fulfillment, max_per_order, created_at)
                VALUES (?, 'Premiu pentru obiectiv', 'Detalii test', ?, 'Digital', ?, ?, false, true, 'DIGITAL', 5, ?)
                """, id, price, stock > 0, stock, Timestamp.from(clock.instant()));
        return id;
    }

    private JsonNode choose(TestUser user, String rewardId) throws Exception {
        return readJson(mvc.perform(authed(put("/api/user/saving-goal").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("rewardId", rewardId))), user))
                .andExpect(status().isOk()).andReturn()).path("goal");
    }

    private int notifications(TestUser user) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'GOAL'", Integer.class, user.id());
    }

    @Test
    void anonymousRequestsAndInvalidRewardsCannotCreateGoals() throws Exception {
        mvc.perform(get("/api/user/saving-goal")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/user/saving-goal").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/user/saving-goal")).andExpect(status().isUnauthorized());
        TestUser user = register();
        mvc.perform(authed(put("/api/user/saving-goal").contentType(MediaType.APPLICATION_JSON).content("{}"), user))
                .andExpect(status().isBadRequest());
        mvc.perform(authed(put("/api/user/saving-goal").contentType(MediaType.APPLICATION_JSON)
                .content("{\"rewardId\":\"missing-reward\"}"), user)).andExpect(status().isNotFound());
        mvc.perform(getAs("/api/user/saving-goal", user)).andExpect(jsonPath("$.goal").isEmpty());
    }

    @Test
    void goalIsPrivateAndDoesNotReservePointsOrStock() throws Exception {
        TestUser user = register();
        TestUser other = register();
        String rewardId = reward(1500, 7);
        JsonNode goal = choose(user, rewardId);
        assertThat(goal.path("missingPoints").asInt()).isEqualTo(500);
        assertThat(goal.path("progressPercent").asInt()).isEqualTo(66);
        assertThat(goal.path("status").asText()).isEqualTo("SAVING");
        assertThat(points(user)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT stock_count FROM rewards WHERE id = ?", Integer.class, rewardId)).isEqualTo(7);
        mvc.perform(getAs("/api/user/saving-goal?userId=" + user.id(), other)).andExpect(jsonPath("$.goal").isEmpty());
        mvc.perform(authed(delete("/api/user/saving-goal"), other)).andExpect(status().isOk());
        mvc.perform(getAs("/api/user/saving-goal", user)).andExpect(jsonPath("$.goal.reward.id").value(rewardId));
        assertThat(goal.toString()).doesNotContain(user.email(), user.token(), "password");
    }

    @Test
    void estimateMatchesCheckoutIncludingTierRoundingAndConsumedDealLimit() throws Exception {
        TestUser user = register();
        String rewardId = reward(1001, 10);
        setPoints(user, 10000);
        jdbc.update("UPDATE users SET total_points_earned = 2500 WHERE id = ?", user.id());
        jdbc.update("""
                INSERT INTO flash_deals (id, reward_id, discount_percent, starts_at, ends_at, max_quantity,
                    sold_quantity, per_user_limit, auto_generated, created_at) VALUES (?, ?, 20, ?, ?, 10, 0, 1, false, ?)
                """, "deal-" + UUID.randomUUID().toString().substring(0, 20), rewardId, Timestamp.from(clock.instant().minusSeconds(60)),
                Timestamp.from(clock.instant().plus(Duration.ofDays(10))), Timestamp.from(clock.instant()));
        JsonNode goal = choose(user, rewardId);
        assertThat(goal.path("targetPrice").asInt()).isEqualTo(760);
        assertThat(goal.path("flashDiscount").asInt()).isEqualTo(201);
        assertThat(goal.path("tierDiscount").asInt()).isEqualTo(40);
        mvc.perform(postJson("/api/cart/items", user, Map.of("rewardId", rewardId, "quantity", 1))).andExpect(status().isOk());
        mvc.perform(postJson("/api/checkout/quote", user, null)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(760));
        mvc.perform(postJson("/api/checkout", user, Map.of("expectedTotal", 760)).header("Idempotency-Key", "goal-" + UUID.randomUUID()))
                .andExpect(status().isOk());
        mvc.perform(postJson("/api/cart/items", user, Map.of("rewardId", rewardId, "quantity", 1))).andExpect(status().isOk());
        mvc.perform(postJson("/api/checkout/quote", user, null)).andExpect(jsonPath("$.total").value(951));
        mvc.perform(getAs("/api/user/saving-goal", user))
                .andExpect(jsonPath("$.goal.targetPrice").value(951)).andExpect(jsonPath("$.goal.flashDiscount").value(0));
    }

    @Test
    void concurrentChecksEmitOnlyOneAlertAndIdempotentSelectionPreservesIt() throws Exception {
        TestUser user = register();
        String rewardId = reward(1500, 5);
        JsonNode initial = choose(user, rewardId);
        setPoints(user, 2000);
        var requests = new ArrayList<Callable<Void>>();
        for (int i = 0; i < 8; i++) requests.add(() -> { goals.check(user.id()); return null; });
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var result : executor.invokeAll(requests)) result.get();
        }
        assertThat(notifications(user)).isEqualTo(1);
        clock.advance(Duration.ofDays(1));
        JsonNode repeated = choose(user, rewardId);
        assertThat(repeated.path("selectedAt")).isEqualTo(initial.path("selectedAt"));
        assertThat(repeated.path("ready").asBoolean()).isTrue();
        assertThat(notifications(user)).isEqualTo(1);
        assertThat(points(user)).isEqualTo(2000);
    }

    @Test
    void stockTierAndActivationPreventMisleadingReadyAlerts() throws Exception {
        TestUser user = register();
        String rewardId = reward(100, 5);
        jdbc.update("UPDATE rewards SET min_tier = 'GOLD' WHERE id = ?", rewardId);
        assertThat(choose(user, rewardId).path("status").asText()).isEqualTo("BLOCKED");
        assertThat(notifications(user)).isZero();
        jdbc.update("UPDATE users SET total_points_earned = 2500 WHERE id = ?", user.id());
        jdbc.update("UPDATE rewards SET stock_count = 0, in_stock = false WHERE id = ?", rewardId);
        goals.check(user.id());
        assertThat(notifications(user)).isZero();
        jdbc.update("UPDATE rewards SET stock_count = 5, in_stock = true WHERE id = ?", rewardId);
        goals.check(user.id());
        assertThat(notifications(user)).isEqualTo(1);
        jdbc.update("UPDATE rewards SET active = false WHERE id = ?", rewardId);
        mvc.perform(getAs("/api/user/saving-goal", user)).andExpect(jsonPath("$.goal.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.goal.targetPrice").isEmpty()).andExpect(jsonPath("$.goal.ready").value(false));
    }

    @Test
    void disabledProgressNotificationsAreRetriedAfterOptingBackIn() throws Exception {
        TestUser user = register();
        String rewardId = reward(100, 5);
        jdbc.update("INSERT INTO notification_preferences (user_id, progress, updated_at) VALUES (?, false, ?)",
                user.id(), Timestamp.from(clock.instant()));
        assertThat(choose(user, rewardId).path("ready").asBoolean()).isTrue();
        assertThat(notifications(user)).isZero();
        jdbc.update("UPDATE notification_preferences SET progress = true WHERE user_id = ?", user.id());
        goals.check(user.id());
        assertThat(notifications(user)).isEqualTo(1);
    }

    @Test
    void scheduledChecksDetectPriceChangesAndSkipGoalsWithInsufficientBalance() throws Exception {
        TestUser poor = register();
        TestUser rich = register();
        String rewardId = reward(1500, 5);
        setPoints(poor, 0);
        choose(poor, rewardId);
        choose(rich, rewardId);
        jdbc.update("UPDATE rewards SET price = 100 WHERE id = ?", rewardId);
        alerts.checkPending();
        assertThat(notifications(poor)).isZero();
        assertThat(notifications(rich)).isEqualTo(1);
    }

    @Test
    void exportIncludesGoalAndAccountDeletionCascadesToIt() throws Exception {
        TestUser user = register();
        String rewardId = reward(1500, 5);
        choose(user, rewardId);
        mvc.perform(getAs("/api/user/export", user)).andExpect(jsonPath("$.saving_goal[0].reward_id").value(rewardId));
        mvc.perform(postJson("/api/user/account/delete", user, Map.of("password", "Parola-Sigura-123", "confirmation", "STERGE")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM saving_goals WHERE user_id = ?", Integer.class, user.id())).isZero();
        goals.check(user.id());
    }
}
