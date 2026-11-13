package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.service.CatalogAlerts;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminPromotionsIntegrationTest extends AbstractIntegrationTest {
    @Autowired CatalogAlerts alerts;
    @Autowired PlatformTransactionManager transactions;
    private TestUser admin;
    private final List<String> products = new ArrayList<>();

    @BeforeEach
    void administrator() throws Exception {
        admin = register();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", admin.id());
    }

    @AfterEach
    void retireOwnFixtures() {
        for (String id : products) {
            jdbc.update("UPDATE flash_deals SET starts_at = ?, ends_at = ? WHERE reward_id = ?", Timestamp.from(START.minusSeconds(60)), Timestamp.from(START.minusSeconds(1)), id);
            jdbc.update("UPDATE rewards SET active = false WHERE id = ?", id);
        }
    }

    private JsonNode promo(Map<String, Object> changes) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("code", "TEST" + UUID.randomUUID().toString().substring(0, 8), "discountPercent", 15, "maxUses", 10));
        body.putAll(changes);
        return readJson(mvc.perform(postJson("/api/admin/promo-codes", admin, body)).andExpect(status().isCreated()).andReturn());
    }

    private JsonNode editor(String id) throws Exception {
        return readJson(mvc.perform(getAs("/api/admin/promo-codes/" + id, admin)).andExpect(status().isOk()).andReturn());
    }

    private ResultActions edit(String id, Map<String, Object> body) throws Exception {
        return mvc.perform(authed(put("/api/admin/promo-codes/" + id).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), admin));
    }

    private String product() throws Exception {
        String id = readJson(mvc.perform(postJson("/api/admin/rewards", admin, Map.of("name", "Promo sintetic", "description", "Produs de test pentru promoții", "fullDescription", "Descriere de test completă", "price", 100,
                "category", "Teste", "stockCount", 3, "active", true, "fulfillment", "DIGITAL"))).andExpect(status().isCreated()).andReturn()).path("id").asText();
        products.add(id);
        return id;
    }

    @Test
    void editorRequiresCurrentAdminRoleAndVersionsCannotOverwriteOrCrossCodes() throws Exception {
        String id = promo(Map.of()).path("id").asText();
        TestUser user = register();
        mvc.perform(getAs("/api/admin/promo-codes/" + id, null)).andExpect(status().isUnauthorized());
        mvc.perform(getAs("/api/admin/promo-codes/" + id, user)).andExpect(status().isForbidden());
        edit(id, Map.of("active", false)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PROMO_VERSION_REQUIRED"));
        String v = editor(id).path("version").asText();
        assertThat(v).hasSize(64);
        edit(id, Map.of("version", v, "description", "Actualizat")).andExpect(status().isOk());
        edit(id, Map.of("version", v, "active", false)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PROMO_CHANGED"));
        String other = promo(Map.of()).path("id").asText();
        edit(other, Map.of("version", editor(id).path("version").asText(), "active", false)).andExpect(status().isConflict());
        jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", admin.id());
        mvc.perform(getAs("/api/admin/promo-codes/" + id, admin)).andExpect(status().isForbidden());
        edit(id, Map.of("version", v, "active", false)).andExpect(status().isForbidden());
    }

    @Test
    void aPurchaseChangesUsageWithoutInvalidatingMetadataOrAllowingTheUsageToBeReset() throws Exception {
        JsonNode p = promo(Map.of());
        String id = p.path("id").asText();
        String v = editor(id).path("version").asText();
        TestUser buyer = register();
        mvc.perform(postJson("/api/cart/items", buyer, Map.of("rewardId", product(), "quantity", 1))).andExpect(status().isOk());
        mvc.perform(postJson("/api/checkout", buyer, Map.of("promoCode", p.path("code").asText())).header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        assertThat(editor(id).at("/promo/currentUses").asInt()).isEqualTo(1);
        assertThat(editor(id).path("version").asText()).isEqualTo(v);
        edit(id, Map.of("version", v, "description", "După cumpărare", "currentUses", 0)).andExpect(status().isOk()).andExpect(jsonPath("$.currentUses").value(1));
        // O limită care ignoră utilizările curente este respinsă în aceeași tranzacție.
        jdbc.update("UPDATE promo_codes SET current_uses = 2 WHERE id = ?", id);
        edit(id, Map.of("version", editor(id).path("version").asText(), "maxUses", 1)).andExpect(status().isBadRequest());
        assertThat(editor(id).at("/promo/maxUses").asInt()).isEqualTo(10);
    }

    @Test
    void expiryCanBeClearedAndExpiredCodesCanBeDisabledWithoutChangingTheirDate() throws Exception {
        String expiry = START.plusSeconds(60).toString();
        String id = promo(Map.of("expiresAt", expiry, "maxDiscount", 20)).path("id").asText();
        clock.advance(java.time.Duration.ofMinutes(2));
        edit(id, Map.of("version", editor(id).path("version").asText(), "expiresAt", expiry, "active", false)).andExpect(status().isOk());
        edit(id, Map.of("version", editor(id).path("version").asText(), "expiresAt", START.toString())).andExpect(status().isBadRequest());
        edit(id, Map.of("version", editor(id).path("version").asText(), "clearExpiry", true, "expiresAt", START.plusSeconds(300).toString())).andExpect(status().isBadRequest());
        edit(id, Map.of("version", editor(id).path("version").asText(), "clearExpiry", true, "maxDiscount", 0)).andExpect(status().isOk());
        assertThat(editor(id).at("/promo/expiresAt").isNull()).isTrue();
        assertThat(editor(id).at("/promo/maxDiscount").isNull()).isTrue();
    }

    @Test
    void concurrentEditsWithTheSameVersionHaveOneWinner() throws Exception {
        String id = promo(Map.of()).path("id").asText();
        String version = editor(id).path("version").asText();
        var calls = new ArrayList<Callable<Integer>>();
        for (int i = 0; i < 8; i++) {
            String description = "Descriere " + i;
            calls.add(() -> edit(id, Map.of("version", version, "description", description)).andReturn().getResponse().getStatus());
        }
        assertThat(statuses(calls)).containsOnly(200, 409).filteredOn(code -> code == 200).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_audit_log WHERE action = 'PROMO_UPDATE' AND target_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void simultaneousCreationByDifferentAdminsReturnsConflictInsteadOfServerError() throws Exception {
        TestUser other = register();
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", other.id());
        String code = "RACE" + UUID.randomUUID().toString().substring(0, 8);
        var calls = new ArrayList<Callable<Integer>>();
        for (int i = 0; i < 8; i++) {
            TestUser actor = i % 2 == 0 ? admin : other;
            String input = i % 2 == 0 ? " " + code.toLowerCase() + " " : code.toUpperCase();
            calls.add(() -> mvc.perform(postJson("/api/admin/promo-codes", actor, Map.of("code", input, "discountPoints", 5))).andReturn().getResponse().getStatus());
        }
        assertThat(statuses(calls)).containsOnly(201, 409).filteredOn(s -> s == 201).hasSize(1);
    }

    @Test
    void concurrentManualDealsDoNotOverlapAndOnlyCurrentActiveProductsCanBeSelected() throws Exception {
        String id = product();
        Map<String, Object> body = Map.of("rewardId", id, "discountPercent", 20, "endsAt", START.plusSeconds(3600).toString(), "maxQuantity", 2);
        var calls = new ArrayList<Callable<Integer>>();
        for (int i = 0; i < 8; i++) calls.add(() -> mvc.perform(postJson("/api/admin/flash-deals", admin, body)).andReturn().getResponse().getStatus());
        assertThat(statuses(calls)).containsOnly(201, 409).filteredOn(s -> s == 201).hasSize(1);
        jdbc.update("UPDATE rewards SET active = false WHERE id = ?", id);
        mvc.perform(postJson("/api/admin/flash-deals", admin, body)).andExpect(status().isNotFound());
    }

    @Test
    void wishlistFanoutWaitsForCommitAndIsDiscardedOnRollback() throws Exception {
        String id = product();
        TestUser watcher = register();
        mvc.perform(authed(put("/api/wishlist/" + id), watcher)).andExpect(status().isOk());
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> {
            alerts.backInStock(id, "Produs sintetic");
            assertThat(notificationCount(watcher)).isZero();
            status.setRollbackOnly();
        });
        assertThat(notificationCount(watcher)).isZero();
        tx.executeWithoutResult(status -> {
            alerts.dealStarted(id, "Produs sintetic", 20);
            assertThat(notificationCount(watcher)).isZero();
        });
        assertThat(notificationCount(watcher)).isEqualTo(1);
    }

    private int notificationCount(TestUser user) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = 'WISHLIST'", Integer.class, user.id());
    }

    private List<Integer> statuses(List<Callable<Integer>> calls) throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Integer> result = new ArrayList<>();
            for (var future : pool.invokeAll(calls, 30, TimeUnit.SECONDS)) result.add(future.get());
            return result;
        } finally { pool.shutdownNow(); }
    }
}
