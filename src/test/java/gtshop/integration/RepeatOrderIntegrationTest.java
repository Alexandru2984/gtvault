package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RepeatOrderIntegrationTest extends AbstractIntegrationTest {
    private final List<String> rewardIds = new ArrayList<>();

    @AfterEach
    void retireOwnCatalogFixtures() {
        for (String id : rewardIds) {
            jdbc.update("UPDATE flash_deals SET starts_at = ?, ends_at = ? WHERE reward_id = ?", Timestamp.from(START.minusSeconds(60)), Timestamp.from(START.minusSeconds(1)), id);
            jdbc.update("UPDATE rewards SET active = false WHERE id = ?", id);
        }
    }

    private String reward(int price, int stock, int maximum) {
        String id = "repeat-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO rewards (id, name, description, price, category, in_stock, stock_count, is_new,
                    active, fulfillment, max_per_order, created_at)
                VALUES (?, 'Premiu actual', 'Date sintetice', ?, 'Digital', ?, ?, false, true, 'DIGITAL', ?, ?)
                """, id, price, stock > 0, stock, maximum, Timestamp.from(START));
        rewardIds.add(id);
        return id;
    }

    private String order(TestUser user, String state, String rewardId, int quantity) {
        String id = "ORD-R" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update("""
                INSERT INTO orders (id, user_id, status, subtotal, total, created_at, updated_at, promo_code,
                    shipping_name, shipping_address, shipping_phone, notes)
                VALUES (?, ?, ?, 10, 10, ?, ?, 'OLD-PROMO', 'Client sintetic', 'Adresă privată', '0700000000', 'Notă privată')
                """, id, user.id(), state, Timestamp.from(START), Timestamp.from(START));
        item(user, id, rewardId, quantity);
        return id;
    }

    private void item(TestUser user, String orderId, String rewardId, int quantity) {
        jdbc.update("""
                INSERT INTO purchase_records (id, order_id, user_id, reward_id, reward_name, quantity, unit_price,
                    points_spent, purchase_date, status, fulfillment, voucher_code)
                VALUES (?, ?, ?, ?, 'Nume istoric', ?, 1, 10, ?, 'FULFILLED', 'DIGITAL', ?)
                """, "repeat-" + UUID.randomUUID(), orderId, user.id(), rewardId, quantity, Timestamp.from(START), "SYNTHETIC-" + UUID.randomUUID());
    }

    private String path(String orderId) { return "/api/cart/repeat-order/" + orderId; }
    private JsonNode preview(TestUser user, String orderId) throws Exception {
        return readJson(mvc.perform(getAs(path(orderId), user)).andExpect(status().isOk()).andReturn());
    }
    private JsonNode apply(TestUser user, String orderId, JsonNode preview) throws Exception {
        return readJson(mvc.perform(postJson(path(orderId), user, Map.of("version", preview.path("version").asText())))
                .andExpect(status().isOk()).andReturn());
    }
    private void cartLine(TestUser user, String rewardId, int quantity) throws Exception {
        mvc.perform(postJson("/api/cart/items", user, Map.of("rewardId", rewardId, "quantity", quantity))).andExpect(status().isOk());
    }
    private int cartQuantity(TestUser user, String rewardId) {
        return jdbc.queryForObject("SELECT COALESCE(sum(quantity), 0) FROM cart_items WHERE user_id = ? AND reward_id = ?", Integer.class, user.id(), rewardId);
    }

    @Test
    void authenticationOwnershipAndConfirmationAreRequired() throws Exception {
        TestUser owner = register();
        TestUser other = register();
        String id = order(owner, "FULFILLED", reward(200, 10, 5), 1);
        JsonNode plan = preview(owner, id);
        mvc.perform(getAs(path(id), null)).andExpect(status().isUnauthorized());
        mvc.perform(postJson(path(id), null, Map.of("version", plan.path("version").asText()))).andExpect(status().isUnauthorized());
        mvc.perform(getAs(path(id) + "?userId=" + owner.id(), other)).andExpect(status().isNotFound());
        mvc.perform(postJson(path(id), other, Map.of("version", plan.path("version").asText()))).andExpect(status().isNotFound());
        mvc.perform(postJson(path(id), owner, Map.of())).andExpect(status().isBadRequest());
        mvc.perform(postJson(path(id), owner, Map.of("version", "not-a-version"))).andExpect(status().isBadRequest());
        assertThat(plan.toString()).doesNotContain("OLD-PROMO", "SYNTHETIC-", "Adresă privată", "0700000000", "Notă privată", owner.email());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE user_id = ?", Integer.class, owner.id())).isZero();
    }

    @Test
    void previewAndAdditionUseCurrentDealsTierAndPricesWithoutSpendingOrReservingAnything() throws Exception {
        TestUser user = register();
        jdbc.update("UPDATE users SET total_points_earned = 2500 WHERE id = ?", user.id());
        String product = reward(400, 10, 5);
        String deal = "deal-r" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update("""
                INSERT INTO flash_deals (id, reward_id, discount_percent, starts_at, ends_at, max_quantity,
                    sold_quantity, per_user_limit, auto_generated, created_at) VALUES (?, ?, 25, ?, ?, 10, 0, 1, false, ?)
                """, deal, product, Timestamp.from(START.minusSeconds(1)), Timestamp.from(START.plusSeconds(3600)), Timestamp.from(START));
        String id = order(user, "CANCELLED", product, 2);
        JsonNode plan = preview(user, id);
        assertThat(plan.path("canAdd").asBoolean()).isTrue();
        assertThat(plan.at("/quote/subtotal").asInt()).isEqualTo(800);
        assertThat(plan.at("/quote/flashDiscount").asInt()).isEqualTo(100);
        assertThat(plan.at("/quote/tierDiscount").asInt()).isEqualTo(35);
        assertThat(plan.at("/quote/total").asInt()).isEqualTo(665);
        assertThat(plan.at("/quote/promo").isNull()).isTrue();
        assertThat(plan.path("version")).isEqualTo(preview(user, id).path("version"));
        assertThat(cartQuantity(user, product)).isZero();
        JsonNode result = apply(user, id, plan);
        assertThat(result.path("quote")).isEqualTo(plan.path("quote"));
        assertThat(cartQuantity(user, product)).isEqualTo(2);
        assertThat(points(user)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("SELECT stock_count FROM rewards WHERE id = ?", Integer.class, product)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT sold_quantity FROM flash_deals WHERE id = ?", Integer.class, deal)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE user_id = ? AND type <> 'WELCOME_BONUS'", Integer.class, user.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_quests WHERE user_id = ?", Integer.class, user.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, id)).isEqualTo("CANCELLED");
    }

    @Test
    void existingProductsArePreservedAndRepeatedSourceLinesAreAggregated() throws Exception {
        TestUser user = register();
        String product = reward(200, 30, 10);
        String other = reward(300, 30, 10);
        cartLine(user, product, 1);
        cartLine(user, other, 2);
        String id = order(user, "FULFILLED", product, 2);
        item(user, id, product, 3);
        JsonNode plan = preview(user, id);
        assertThat(plan.path("items")).hasSize(1);
        assertThat(plan.at("/items/0/quantity").asInt()).isEqualTo(5);
        assertThat(plan.at("/items/0/inCart").asInt()).isEqualTo(1);
        assertThat(plan.at("/items/0/afterAddition").asInt()).isEqualTo(6);
        assertThat(plan.at("/quote/missingPoints").asInt()).isEqualTo(800);
        apply(user, id, plan);
        assertThat(cartQuantity(user, product)).isEqualTo(6);
        assertThat(cartQuantity(user, other)).isEqualTo(2);
        assertThat(points(user)).isEqualTo(1000);
    }

    @Test
    void stockOrderLimitsTierAndOwnedBadgesBlockTheWholeOperation() throws Exception {
        TestUser user = register();
        String valid = reward(100, 20, 5);
        String blocked = reward(100, 1, 5);
        String id = order(user, "FULFILLED", valid, 1);
        item(user, id, blocked, 2);
        cartLine(user, valid, 1);
        for (String configuration : List.of("stock", "limit", "tier", "badge")) {
            jdbc.update("UPDATE rewards SET stock_count = ?, max_per_order = ?, min_tier = ?, fulfillment = ? WHERE id = ?",
                    configuration.equals("stock") ? 1 : 20, configuration.equals("limit") ? 1 : 5,
                    configuration.equals("tier") ? "GOLD" : null, configuration.equals("badge") ? "BADGE" : "DIGITAL", blocked);
            if (configuration.equals("badge")) jdbc.update("""
                    INSERT INTO user_badges (id, user_id, badge_code, badge_title, badge_icon, unlocked_at)
                    VALUES (?, ?, 'HERO', 'Hero', 'hero', ?)""", UUID.randomUUID().toString(), user.id(), Timestamp.from(START));
            JsonNode plan = preview(user, id);
            assertThat(plan.path("canAdd").asBoolean()).as(configuration).isFalse();
            assertThat(plan.path("issues")).isNotEmpty();
            mvc.perform(postJson(path(id), user, Map.of("version", plan.path("version").asText())))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REPEAT_UNAVAILABLE"));
            assertThat(cartQuantity(user, valid)).isEqualTo(1);
            assertThat(cartQuantity(user, blocked)).isZero();
        }
    }

    @Test
    void inactiveMissingAndUnidentifiedHistoricalProductsNeverCausePartialAddition() throws Exception {
        TestUser user = register();
        String valid = reward(100, 10, 5);
        String inactive = reward(100, 10, 5);
        jdbc.update("UPDATE rewards SET active = false WHERE id = ?", inactive);
        for (String unavailable : new String[]{inactive, "missing-product", null}) {
            String id = order(user, "FULFILLED", valid, 1);
            item(user, id, unavailable, 1);
            JsonNode plan = preview(user, id);
            assertThat(plan.path("canAdd").asBoolean()).isFalse();
            mvc.perform(postJson(path(id), user, Map.of("version", plan.path("version").asText()))).andExpect(status().isConflict());
            assertThat(cartQuantity(user, valid)).isZero();
        }
    }

    @Test
    void aChangedPriceStockOrCartRequiresANewConfirmationAndVersionsAreBoundToTheOrder() throws Exception {
        TestUser user = register();
        String product = reward(200, 20, 5);
        String id = order(user, "FULFILLED", product, 1);
        JsonNode original = preview(user, id);
        jdbc.update("UPDATE rewards SET price = 300 WHERE id = ?", product);
        mvc.perform(postJson(path(id), user, Map.of("version", original.path("version").asText())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REPEAT_CHANGED"));
        JsonNode price = preview(user, id);
        jdbc.update("UPDATE rewards SET stock_count = 19 WHERE id = ?", product);
        mvc.perform(postJson(path(id), user, Map.of("version", price.path("version").asText())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REPEAT_CHANGED"));
        JsonNode stock = preview(user, id);
        cartLine(user, product, 1);
        mvc.perform(postJson(path(id), user, Map.of("version", stock.path("version").asText())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REPEAT_CHANGED"));
        JsonNode current = preview(user, id);
        String anotherOrder = order(user, "FULFILLED", product, 1);
        mvc.perform(postJson(path(anotherOrder), user, Map.of("version", current.path("version").asText())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REPEAT_CHANGED"));
        assertThat(cartQuantity(user, product)).isEqualTo(1);
        apply(user, id, current);
        assertThat(cartQuantity(user, product)).isEqualTo(2);
    }

    @Test
    void quantitiesAndLineCountAreCheckedBeforePricingEvenForOversizedLegacyOrders() throws Exception {
        TestUser user = register();
        String product = reward(1_000_000, 9999, 99);
        cartLine(user, product, 1);
        for (int quantity : new int[]{99, Integer.MAX_VALUE}) {
            String id = order(user, "FULFILLED", product, quantity);
            JsonNode plan = preview(user, id);
            assertThat(plan.path("quote").isNull()).isTrue();
            assertThat(plan.path("canAdd").asBoolean()).isFalse();
            mvc.perform(postJson(path(id), user, Map.of("version", plan.path("version").asText()))).andExpect(status().isConflict());
        }
        for (int i = 1; i < 20; i++) cartLine(user, reward(100, 10, 5), 1);
        String id = order(user, "FULFILLED", reward(100, 10, 5), 1);
        JsonNode full = preview(user, id);
        assertThat(full.path("canAdd").asBoolean()).isFalse();
        assertThat(full.path("quote").isNull()).isTrue();
        mvc.perform(postJson(path(id), user, Map.of("version", full.path("version").asText()))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cart_items WHERE user_id = ?", Integer.class, user.id())).isEqualTo(20);
    }

    @Test
    void concurrentConfirmationsApplyTheReviewedCartOnlyOnce() throws Exception {
        TestUser user = register();
        String product = reward(100, 30, 20);
        String id = order(user, "FULFILLED", product, 2);
        JsonNode plan = preview(user, id);
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) calls.add(() -> mvc.perform(postJson(path(id), user, Map.of("version", plan.path("version").asText())))
                    .andReturn().getResponse().getStatus());
            List<Integer> statuses = new ArrayList<>();
            for (var result : pool.invokeAll(calls, 30, TimeUnit.SECONDS)) statuses.add(result.get());
            assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(7);
        } finally { pool.shutdownNow(); }
        assertThat(cartQuantity(user, product)).isEqualTo(2);
        assertThat(points(user)).isEqualTo(1000);
        mvc.perform(postJson(path(id), user, Map.of("version", plan.path("version").asText())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("REPEAT_CHANGED"));
    }

    @Test
    void callerSuppliedQuantitiesPricesAndOwnerCannotOverrideServerSideSelection() throws Exception {
        TestUser user = register();
        TestUser other = register();
        String product = reward(200, 50, 99);
        String kept = reward(300, 50, 99);
        cartLine(user, kept, 1);
        String id = order(user, "FULFILLED", product, 2);
        JsonNode plan = preview(user, id);
        mvc.perform(postJson(path(id), user, Map.of("version", plan.path("version").asText(), "quantity", 99,
                "price", 1, "rewardId", kept, "userId", other.id()))).andExpect(status().isOk());
        assertThat(cartQuantity(user, product)).isEqualTo(2);
        assertThat(cartQuantity(user, kept)).isEqualTo(1);
        assertThat(cartQuantity(other, product)).isZero();
        assertThat(points(user)).isEqualTo(1000);
        assertThat(points(other)).isEqualTo(1000);
    }

    @Test
    void theLargestValidRepeatedCartKeepsPricesWithinTheirSupportedIntegerRange() throws Exception {
        TestUser user = register();
        jdbc.update("UPDATE users SET total_points_earned = 10000 WHERE id = ?", user.id());
        String first = reward(1_000_000, 9999, 99);
        String id = order(user, "FULFILLED", first, 99);
        for (int i = 1; i < 20; i++) item(user, id, reward(1_000_000, 9999, 99), 99);
        JsonNode plan = preview(user, id);
        assertThat(plan.path("canAdd").asBoolean()).isTrue();
        assertThat(plan.at("/quote/subtotal").asInt()).isEqualTo(1_980_000_000);
        assertThat(plan.at("/quote/total").asInt()).isEqualTo(1_782_000_000);
        apply(user, id, plan);
        assertThat(jdbc.queryForObject("SELECT sum(quantity) FROM cart_items WHERE user_id = ?", Integer.class, user.id())).isEqualTo(1980);
        assertThat(points(user)).isEqualTo(1000);
    }
}
