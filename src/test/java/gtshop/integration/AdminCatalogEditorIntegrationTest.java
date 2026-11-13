package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class AdminCatalogEditorIntegrationTest extends AbstractIntegrationTest {
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

    private Map<String, Object> form() {
        return new HashMap<>(Map.of("name", "Produs editor sintetic", "description", "Descriere sintetică pentru editor",
                "fullDescription", "Detalii sintetice complete", "price", 100, "category", "Teste", "stockCount", 3,
                "image", "images/products/cv.svg", "fulfillment", "DIGITAL", "active", true, "maxPerOrder", 5));
    }

    private String create() throws Exception {
        JsonNode created = readJson(mvc.perform(postJson("/api/admin/rewards", admin, form())).andExpect(status().isCreated()).andReturn());
        String id = created.path("id").asText();
        products.add(id);
        return id;
    }

    private JsonNode editor(String id) throws Exception {
        return readJson(mvc.perform(getAs("/api/admin/rewards/" + id, admin)).andExpect(status().isOk()).andReturn());
    }

    private ResultActions update(String id, Map<String, Object> body) throws Exception {
        return mvc.perform(authed(put("/api/admin/rewards/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)), admin));
    }

    private int edits(String id) {
        return jdbc.queryForObject("SELECT count(*) FROM admin_audit_log WHERE action = 'REWARD_UPDATE' AND target_id = ?", Integer.class, id);
    }

    @Test
    void detailAndMutationRequireCurrentAdministratorPrivileges() throws Exception {
        String id = create();
        TestUser user = register();
        mvc.perform(getAs("/api/admin/rewards/" + id, null)).andExpect(status().isUnauthorized());
        mvc.perform(getAs("/api/admin/rewards/" + id, user)).andExpect(status().isForbidden());
        mvc.perform(authed(put("/api/admin/rewards/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"), user))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET role = 'USER' WHERE id = ?", admin.id());
        mvc.perform(getAs("/api/admin/rewards/" + id, admin)).andExpect(status().isForbidden());
        assertThat(edits(id)).isZero();
    }

    @Test
    void freshMetadataCanBeSavedAndStaleOrMissingVersionsCannotOverwriteIt() throws Exception {
        String id = create();
        String version = editor(id).path("version").asText();
        assertThat(version).hasSize(64).isEqualTo(editor(id).path("version").asText());
        update(id, Map.of("price", 200)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PRODUCT_VERSION_REQUIRED"));
        update(id, Map.of("version", version, "price", 200, "isNew", true, "minTier", "GOLD")).andExpect(status().isOk());
        update(id, Map.of("version", version, "price", 300)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PRODUCT_CHANGED"));
        JsonNode saved = editor(id);
        assertThat(saved.at("/product/price").asInt()).isEqualTo(200);
        assertThat(saved.at("/product/new").asBoolean()).isTrue();
        assertThat(saved.path("version").asText()).isNotEqualTo(version);
        update(id, Map.of("version", saved.path("version").asText(), "minTier", "", "isNew", false)).andExpect(status().isOk());
        assertThat(editor(id).at("/product/minTier").isNull()).isTrue();
        assertThat(edits(id)).isEqualTo(2);
    }

    @Test
    void aRealPurchaseCannotBeUndoneByAMetadataEditorOrAnOldAbsoluteStockValue() throws Exception {
        String id = create();
        TestUser buyer = register();
        String version = editor(id).path("version").asText();
        mvc.perform(postJson("/api/cart/items", buyer, Map.of("rewardId", id, "quantity", 1))).andExpect(status().isOk());
        mvc.perform(postJson("/api/checkout", buyer, Map.of()).header("Idempotency-Key", "editor-" + UUID.randomUUID()))
                .andExpect(status().isOk());
        assertThat(editor(id).path("version").asText()).isEqualTo(version);
        update(id, Map.of("version", version, "description", "Descriere editată după vânzare")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stockCount").value(2));
        String current = editor(id).path("version").asText();
        update(id, Map.of("version", current, "stockCount", 3, "expectedStock", 3)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("STOCK_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT stock_count FROM rewards WHERE id = ?", Integer.class, id)).isEqualTo(2);
        assertThat(edits(id)).isEqualTo(1);
    }

    @Test
    void absoluteStockNeedsItsOwnExpectedValueWhileDeltaDoesNotInvalidateMetadata() throws Exception {
        String id = create();
        String version = editor(id).path("version").asText();
        update(id, Map.of("version", version, "stockCount", 0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("STOCK_VERSION_REQUIRED"));
        mvc.perform(postJson("/api/admin/rewards/" + id + "/stock", admin, Map.of("delta", 2, "reason", "Test reaprovizionare")))
                .andExpect(status().isOk());
        assertThat(editor(id).path("version").asText()).isEqualTo(version);
        update(id, Map.of("version", version, "stockCount", 0, "expectedStock", 5)).andExpect(status().isOk());
        assertThat(editor(id).at("/product/stockCount").asInt()).isZero();
        assertThat(editor(id).at("/product/inStock").asBoolean()).isFalse();
        mvc.perform(postJson("/api/admin/rewards/" + id + "/stock", admin, Map.of("delta", Integer.MIN_VALUE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void concurrentEditsWithOneVersionCannotLoseAnAdministratorsChange() throws Exception {
        String id = create();
        String version = editor(id).path("version").asText();
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                int price = 101 + i;
                calls.add(() -> update(id, Map.of("version", version, "price", price)).andReturn().getResponse().getStatus());
            }
            List<Integer> statuses = new ArrayList<>();
            for (var result : pool.invokeAll(calls, 30, TimeUnit.SECONDS)) statuses.add(result.get());
            assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(7);
        } finally { pool.shutdownNow(); }
        assertThat(edits(id)).isEqualTo(1);
    }

    @Test
    void simultaneousCreationsOfTheSameNameHaveDifferentStableIdentifiers() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        List<String> ids = new ArrayList<>();
        try {
            List<Callable<JsonNode>> calls = new ArrayList<>();
            for (int i = 0; i < 8; i++) calls.add(() -> readJson(mvc.perform(postJson("/api/admin/rewards", admin, form()))
                    .andExpect(status().isCreated()).andReturn()));
            for (var result : pool.invokeAll(calls, 30, TimeUnit.SECONDS)) {
                String id = result.get().path("id").asText();
                ids.add(id);
                products.add(id);
            }
        } finally { pool.shutdownNow(); }
        assertThat(ids).doesNotHaveDuplicates().hasSize(8);
        assertThat(ids).allSatisfy(id -> assertThat(id).startsWith("rew-produs-editor-sintetic-").hasSizeLessThanOrEqualTo(64));
    }

    @Test
    void aVersionFromAnIdenticalProductCannotBeUsedForAnotherProduct() throws Exception {
        String first = create();
        String second = create();
        update(second, Map.of("version", editor(first).path("version").asText(), "price", 1)).andExpect(status().isConflict());
        assertThat(editor(second).at("/product/price").asInt()).isEqualTo(100);
        assertThat(edits(second)).isZero();
    }

    @Test
    void validationAndTheSpecialBadgeRulesStillApplyAfterTheVersionCheck() throws Exception {
        String id = create();
        String version = editor(id).path("version").asText();
        for (Map<String, Object> change : List.of(Map.<String, Object>of("price", 0), Map.<String, Object>of("maxPerOrder", 100),
                Map.<String, Object>of("image", "images/../../secret.svg"), Map.<String, Object>of("image", "https://remote.test/a.svg"),
                Map.<String, Object>of("fulfillment", "BADGE"))) {
            Map<String, Object> body = new HashMap<>(change);
            body.put("version", version);
            update(id, body).andExpect(status().isBadRequest());
        }
        String badge = editor("rew-badge").path("version").asText();
        update("rew-badge", Map.of("version", badge, "fulfillment", "DIGITAL")).andExpect(status().isBadRequest());
        update("rew-badge", Map.of("version", badge, "maxPerOrder", 2)).andExpect(status().isBadRequest());
        assertThat(edits(id)).isZero();
    }
}
