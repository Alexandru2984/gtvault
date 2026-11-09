package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class AdminIntegrationTest extends AbstractIntegrationTest {
    private TestUser admin;

    @BeforeEach
    void createAdmin() throws Exception {
        admin = register();
        jdbc.update("update users set role = 'ADMIN' where id = ?", admin.id());
        jdbc.update("update rewards set stock_count = 9999, in_stock = true, active = true where id in ('rew-templatecv', 'rew-udemy')");
        jdbc.update("update rewards set stock_count = 300, in_stock = true where id = 'rew-sticker'");
    }

    private MockHttpServletRequestBuilder putJson(String url, TestUser u, Object body) throws Exception {
        return authed(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)), u);
    }

    private ResultActions adminPost(String url, Object body) throws Exception {
        return mvc.perform(postJson(url, admin, body));
    }

    private int notifications(TestUser u, String type) {
        Integer c = jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = ?", Integer.class, u.id(), type);
        return c == null ? 0 : c;
    }

    private int auditCount(String action, String targetId) {
        Integer c = jdbc.queryForObject("select count(*) from admin_audit_log where action = ? and target_id = ? and admin_id = ?",
                Integer.class, action, targetId, admin.id());
        return c == null ? 0 : c;
    }

    /** Comanda reala prin API (cos + checkout). */
    private JsonNode order(TestUser u, String rewardId, boolean physical) throws Exception {
        setPoints(u, Math.max(points(u), 20_000));
        mvc.perform(postJson("/api/cart/items", u, Map.of("rewardId", rewardId, "quantity", 1))).andExpect(status().isOk());
        Map<String, Object> body = new HashMap<>();
        if (physical) {
            body.put("shipping", Map.of("name", "Ana Popescu", "phone", "0722123456", "address", "Str. Exemplu nr. 10",
                    "city", "Cluj-Napoca", "county", "Cluj", "postalCode", "400001"));
        }
        return readJson(mvc.perform(postJson("/api/checkout", u, body).header("Idempotency-Key", "k-" + UUID.randomUUID()))
                .andExpect(status().isOk()).andReturn()).path("order");
    }

    // ------------------------------------------------------------------ acces

    @Test
    void onlyAdminsCanUseTheAdminApiAndRevocationIsImmediate() throws Exception {
        TestUser user = register();
        mvc.perform(get("/api/admin/stats")).andExpect(status().isUnauthorized());
        mvc.perform(getAs("/api/admin/stats", user)).andExpect(status().isForbidden());
        mvc.perform(postJson("/api/admin/users/" + user.id() + "/points", user, Map.of("amount", 1000, "reason", "hack")))
                .andExpect(status().isForbidden());
        assertThat(points(user)).isEqualTo(points(user));

        JsonNode stats = readJson(mvc.perform(getAs("/api/admin/stats", admin)).andExpect(status().isOk()).andReturn());
        assertThat(stats.at("/users/total").asLong()).isPositive();
        assertThat(stats.at("/charts/signups")).hasSize(14);
        assertThat(stats.has("economy")).isTrue();

        jdbc.update("update users set role = 'USER' where id = ?", admin.id());
        mvc.perform(getAs("/api/admin/stats", admin)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ comenzi

    @Test
    void physicalOrderMovesThroughTheLifecycleWithNotificationsAndAudit() throws Exception {
        TestUser buyer = register();
        String id = order(buyer, "rew-sticker", true).path("id").asText();

        JsonNode found = readJson(mvc.perform(getAs("/api/admin/orders?status=PLACED&q=" + buyer.email(), admin)).andReturn());
        assertThat(found.at("/items/0/id").asText()).isEqualTo(id);
        mvc.perform(getAs("/api/admin/orders?q=50%25_", admin)).andExpect(status().isOk());
        mvc.perform(getAs("/api/admin/orders?status=HACK", admin)).andExpect(status().isBadRequest());

        adminPost("/api/admin/orders/" + id + "/status", Map.of("status", "DELIVERED")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_TRANSITION"));
        adminPost("/api/admin/orders/" + id + "/status", Map.of("status", "PROCESSING")).andExpect(status().isOk())
                .andExpect(jsonPath("$.order.status").value("PROCESSING"));
        // Clientul nu mai poate anula dupa ce comanda intra in pregatire
        mvc.perform(postJson("/api/orders/" + id + "/cancel", buyer, null)).andExpect(status().isConflict());

        adminPost("/api/admin/orders/" + id + "/status", Map.of("status", "SHIPPED", "trackingCode", "<script>"))
                .andExpect(status().isBadRequest());
        clock.advance(Duration.ofHours(5));
        adminPost("/api/admin/orders/" + id + "/status", Map.of("status", "SHIPPED", "trackingCode", "FAN 123456789"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.trackingCode").value("FAN 123456789"));
        clock.advance(Duration.ofDays(1));
        JsonNode delivered = readJson(adminPost("/api/admin/orders/" + id + "/status", Map.of("status", "DELIVERED"))
                .andExpect(status().isOk()).andReturn());
        assertThat(delivered.path("allowedTransitions")).isEmpty();

        JsonNode mine = readJson(mvc.perform(getAs("/api/orders/" + id, buyer)).andExpect(status().isOk()).andReturn());
        assertThat(mine.path("status").asText()).isEqualTo("DELIVERED");
        assertThat(mine.path("timeline")).hasSize(4);
        for (JsonNode step : mine.path("timeline")) {
            assertThat(step.path("reached").asBoolean()).isTrue();
            assertThat(step.path("at").asText()).isNotBlank();
        }
        assertThat(mine.at("/items/0/status").asText()).isEqualTo("DELIVERED");
        assertThat(notifications(buyer, "ORDER")).isGreaterThanOrEqualTo(4); // plasata + 3 schimbari de status
        assertThat(auditCount("ORDER_STATUS", id)).isEqualTo(3);

        // Produsul livrat poate primi acum o recenzie verificata
        JsonNode reviews = readJson(mvc.perform(getAs("/api/rewards/rew-sticker/reviews", buyer)).andReturn());
        assertThat(reviews.at("/eligibility/canReview").asBoolean()).isTrue();

        adminPost("/api/admin/orders/" + id + "/cancel", Map.of("reason", "Prea târziu")).andExpect(status().isConflict());
    }

    @Test
    void adminCancellationRefundsEvenForSuspendedAccountsButNotRedeemedVouchers() throws Exception {
        TestUser buyer = register();
        JsonNode o = order(buyer, "rew-templatecv", false);
        String id = o.path("id").asText();
        String voucher = o.at("/items/0/voucherCode").asText();
        int afterPurchase = points(buyer);

        adminPost("/api/admin/orders/" + id + "/cancel", Map.of("reason", "")).andExpect(status().isBadRequest());
        adminPost("/api/admin/orders/" + id + "/status", Map.of("status", "PROCESSING")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DIGITAL_ORDER"));

        jdbc.update("update users set banned = true where id = ?", buyer.id());
        adminPost("/api/admin/orders/" + id + "/cancel", Map.of("reason", "Comandă de test anulată"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("CANCELLED"));
        assertThat(points(buyer)).isEqualTo(afterPurchase + o.path("total").asInt());
        mvc.perform(getAs("/api/vouchers/verify?code=" + voucher, null)).andExpect(jsonPath("$.state").value("CANCELLED"));
        adminPost("/api/admin/vouchers/redeem", Map.of("code", voucher)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VOUCHER_CANCELLED"));
        assertThat(auditCount("ORDER_CANCEL", id)).isEqualTo(1);
        jdbc.update("update users set banned = false where id = ?", buyer.id());

        JsonNode second = order(buyer, "rew-udemy", false);
        String code = second.at("/items/0/voucherCode").asText();
        adminPost("/api/admin/vouchers/redeem", Map.of("code", code.toLowerCase())).andExpect(status().isOk());
        adminPost("/api/admin/vouchers/redeem", Map.of("code", code)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("VOUCHER_REDEEMED"));
        mvc.perform(getAs("/api/vouchers/verify?code=" + code, null)).andExpect(jsonPath("$.state").value("REDEEMED"));
        adminPost("/api/admin/orders/" + second.path("id").asText() + "/cancel", Map.of("reason", "Încercare de anulare"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CANNOT_CANCEL"));
        adminPost("/api/admin/vouchers/redeem", Map.of("code", "NU-EXISTA-123")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ catalog

    @Test
    void rewardsCanBeCreatedValidatedUpdatedAndRestocked() throws Exception {
        Map<String, Object> form = new HashMap<>(Map.of("name", "Hanorac GT Ediție Limitată", "description", "Hanorac cald, logo brodat",
                "fullDescription", "Hanorac din bumbac organic, logo Generația Tech brodat.", "price", 1200,
                "category", "Premii fizice", "stockCount", 3, "fulfillment", "PHYSICAL", "maxPerOrder", 2,
                "image", "images/hanorac.svg"));
        Map<String, Object> bad = new HashMap<>(form);
        bad.put("image", "https://evil.example/x.png");
        adminPost("/api/admin/rewards", bad).andExpect(status().isBadRequest());
        bad = new HashMap<>(form);
        bad.put("price", 0);
        adminPost("/api/admin/rewards", bad).andExpect(status().isBadRequest());
        bad = new HashMap<>(form);
        bad.put("fulfillment", "BADGE");
        adminPost("/api/admin/rewards", bad).andExpect(status().isBadRequest());
        bad = new HashMap<>(form);
        bad.put("minTier", "LEGEND");
        adminPost("/api/admin/rewards", bad).andExpect(status().isBadRequest());

        JsonNode created = readJson(adminPost("/api/admin/rewards", form).andExpect(status().isCreated()).andReturn());
        String id = created.path("id").asText();
        assertThat(id).startsWith("rew-hanorac-gt-editie-limitata");
        mvc.perform(getAs("/api/rewards/" + id, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reward.price").value(1200));
        assertThat(auditCount("REWARD_CREATE", id)).isEqualTo(1);

        TestUser fan = register();
        mvc.perform(putJson("/api/admin/rewards/" + id, admin, Map.of("stockCount", 0))).andExpect(status().isOk());
        mvc.perform(authed(put("/api/wishlist/" + id), fan)).andExpect(status().isOk());
        adminPost("/api/admin/rewards/" + id + "/stock", Map.of("delta", -1)).andExpect(status().isBadRequest());
        adminPost("/api/admin/rewards/" + id + "/stock", Map.of("delta", 10, "reason", "Lot nou")).andExpect(status().isOk())
                .andExpect(jsonPath("$.stockCount").value(10));
        assertThat(notifications(fan, "WISHLIST")).isEqualTo(1);

        mvc.perform(putJson("/api/admin/rewards/" + id, admin, Map.of("active", false))).andExpect(status().isOk());
        mvc.perform(getAs("/api/rewards/" + id, null)).andExpect(status().isNotFound());
        JsonNode all = readJson(mvc.perform(getAs("/api/admin/rewards", admin)).andReturn());
        assertThat(all.toString()).contains(id);
        mvc.perform(putJson("/api/admin/rewards/rew-badge", admin, Map.of("fulfillment", "DIGITAL"))).andExpect(status().isBadRequest());
        mvc.perform(putJson("/api/admin/rewards/nu-exista", admin, Map.of("price", 10))).andExpect(status().isNotFound());
    }

    @Test
    void flashDealsCannotOverlapAndCanBeEnded() throws Exception {
        jdbc.update("delete from flash_deals where reward_id = 'rew-meet'");
        String start = clock.instant().minus(Duration.ofMinutes(1)).toString();
        String end = clock.instant().plus(Duration.ofDays(2)).toString();
        adminPost("/api/admin/flash-deals", Map.of("rewardId", "rew-meet", "discountPercent", 90, "endsAt", end,
                "maxQuantity", 10)).andExpect(status().isBadRequest());
        adminPost("/api/admin/flash-deals", Map.of("rewardId", "rew-meet", "discountPercent", 30, "startsAt", end,
                "endsAt", start, "maxQuantity", 10)).andExpect(status().isBadRequest());
        adminPost("/api/admin/flash-deals", Map.of("rewardId", "rew-badge", "discountPercent", 30, "endsAt", end,
                "maxQuantity", 10)).andExpect(status().isBadRequest());

        JsonNode deal = readJson(adminPost("/api/admin/flash-deals", Map.of("rewardId", "rew-meet", "discountPercent", 25,
                "startsAt", start, "endsAt", end, "maxQuantity", 10, "perUserLimit", 1)).andExpect(status().isCreated()).andReturn());
        mvc.perform(getAs("/api/rewards/rew-meet", null)).andExpect(jsonPath("$.reward.deal.dealPrice").value(900));
        adminPost("/api/admin/flash-deals", Map.of("rewardId", "rew-meet", "discountPercent", 10, "endsAt", end,
                "maxQuantity", 5)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DEAL_OVERLAP"));

        adminPost("/api/admin/flash-deals/" + deal.path("id").asText() + "/end", null).andExpect(status().isOk());
        mvc.perform(getAs("/api/rewards/rew-meet", null)).andExpect(jsonPath("$.reward.deal").isEmpty());
        adminPost("/api/admin/flash-deals/" + deal.path("id").asText() + "/end", null).andExpect(status().isConflict());

        String later = clock.instant().plus(Duration.ofDays(5)).toString();
        String laterEnd = clock.instant().plus(Duration.ofDays(6)).toString();
        JsonNode scheduled = readJson(adminPost("/api/admin/flash-deals", Map.of("rewardId", "rew-meet", "discountPercent", 15,
                "startsAt", later, "endsAt", laterEnd, "maxQuantity", 5)).andExpect(status().isCreated()).andReturn());
        adminPost("/api/admin/flash-deals/" + scheduled.path("id").asText() + "/end", null).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from flash_deals where id = ?", Integer.class, scheduled.path("id").asText()))
                .isZero();
        assertThat(readJson(mvc.perform(getAs("/api/admin/flash-deals", admin)).andReturn()).toString())
                .contains(deal.path("id").asText());
    }

    @Test
    void promoCodesAreValidatedAndUsableImmediately() throws Exception {
        String code = "ADM" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        adminPost("/api/admin/promo-codes", Map.of("code", code, "discountPercent", 10, "discountPoints", 50))
                .andExpect(status().isBadRequest());
        adminPost("/api/admin/promo-codes", Map.of("code", "a b", "discountPercent", 10)).andExpect(status().isBadRequest());
        JsonNode promo = readJson(adminPost("/api/admin/promo-codes", Map.of("code", code.toLowerCase(), "discountPercent", 10,
                "maxUses", 3, "description", "Test admin")).andExpect(status().isCreated()).andReturn());
        assertThat(promo.path("code").asText()).isEqualTo(code);
        adminPost("/api/admin/promo-codes", Map.of("code", code.toLowerCase(), "discountPoints", 10)).andExpect(status().isConflict());

        TestUser u = register();
        setPoints(u, 10_000);
        mvc.perform(postJson("/api/cart/items", u, Map.of("rewardId", "rew-templatecv", "quantity", 1))).andExpect(status().isOk());
        mvc.perform(postJson("/api/promo/validate", u, Map.of("code", code))).andExpect(status().isOk())
                .andExpect(jsonPath("$.promo.valid").value(true));

        String pid = promo.path("id").asText();
        mvc.perform(putJson("/api/admin/promo-codes/" + pid, admin, Map.of("code", "ALTCOD"))).andExpect(status().isBadRequest());
        mvc.perform(putJson("/api/admin/promo-codes/" + pid, admin, Map.of("active", false))).andExpect(status().isOk());
        mvc.perform(postJson("/api/promo/validate", u, Map.of("code", code))).andExpect(jsonPath("$.promo.valid").value(false));
        jdbc.update("update promo_codes set current_uses = 2 where id = ?", pid);
        mvc.perform(putJson("/api/admin/promo-codes/" + pid, admin, Map.of("maxUses", 1))).andExpect(status().isBadRequest());
        assertThat(auditCount("PROMO_UPDATE", pid)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ comunitate

    @Test
    void reportedReviewsCanBeHiddenOrApprovedAndProposalsModerated() throws Exception {
        TestUser author = register();
        TestUser r1 = register();
        TestUser r2 = register();
        String orderId = "ORD-A" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("insert into orders (id, user_id, status, subtotal, total, created_at, updated_at) values (?, ?, 'FULFILLED', 0, 0, ?, ?)",
                orderId, author.id(), now, now);
        jdbc.update("""
                insert into purchase_records (id, order_id, user_id, reward_id, reward_name, quantity, unit_price, points_spent, status, purchase_date)
                values (?, ?, ?, 'rew-mentorat', 'Mentorat 1:1', 1, 1, 1, 'FULFILLED', ?)""", "pur-" + UUID.randomUUID(), orderId, author.id(), now);
        String reviewId = readJson(mvc.perform(postJson("/api/rewards/rew-mentorat/reviews", author,
                Map.of("rating", 1, "comment", "Text de test care va fi raportat."))).andExpect(status().isCreated()).andReturn())
                .at("/review/id").asText();
        mvc.perform(postJson("/api/reviews/" + reviewId + "/report", r1, Map.of("reason", "SPAM"))).andExpect(status().isOk());
        mvc.perform(postJson("/api/reviews/" + reviewId + "/report", r2, Map.of("reason", "OFFENSIVE"))).andExpect(status().isOk());

        JsonNode reported = readJson(mvc.perform(getAs("/api/admin/reviews/reported", admin)).andExpect(status().isOk()).andReturn());
        JsonNode mineReported = null;
        for (JsonNode r : reported) {
            if (reviewId.equals(r.path("id").asText())) {
                mineReported = r;
            }
        }
        assertThat(mineReported).isNotNull();
        assertThat(mineReported.path("reports").asInt()).isEqualTo(2);

        adminPost("/api/admin/reviews/" + reviewId + "/moderate", Map.of("hidden", true, "reason", "Limbaj nepotrivit"))
                .andExpect(status().isOk());
        assertThat(readJson(mvc.perform(getAs("/api/rewards/rew-mentorat/reviews", null)).andReturn()).toString()).doesNotContain(reviewId);
        assertThat(notifications(author, "REVIEW")).isEqualTo(1);
        adminPost("/api/admin/reviews/" + reviewId + "/moderate", Map.of("hidden", false)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reportsDismissed").value(2));
        assertThat(readJson(mvc.perform(getAs("/api/rewards/rew-mentorat/reviews", null)).andReturn()).toString()).contains(reviewId);
        assertThat(auditCount("REVIEW_HIDE", reviewId)).isEqualTo(1);

        String proposalId = readJson(mvc.perform(postJson("/api/proposals", author, Map.of("title", "Licență JetBrains " + UUID.randomUUID().toString().substring(0, 5),
                "category", "Premii digitale", "description", "O licență anuală pentru IDE-urile JetBrains."))).andReturn()).at("/proposal/id").asText();
        adminPost("/api/admin/proposals/" + proposalId + "/status", Map.of("status", "MAYBE")).andExpect(status().isBadRequest());
        adminPost("/api/admin/proposals/" + proposalId + "/status", Map.of("status", "approved", "note", "O adăugăm luna viitoare"))
                .andExpect(status().isOk());
        assertThat(notifications(author, "PROPOSAL")).isEqualTo(1);
        JsonNode pub = readJson(mvc.perform(getAs("/api/proposals", null)).andReturn());
        assertThat(pub.toString()).contains("O adăugăm luna viitoare");
        assertThat(readJson(mvc.perform(getAs("/api/admin/proposals?status=APPROVED", admin)).andReturn()).toString()).contains(proposalId);
    }

    @Test
    void broadcastReachesActiveMembersOnly() throws Exception {
        TestUser active = register();
        TestUser banned = register();
        jdbc.update("update users set banned = true where id = ?", banned.id());
        adminPost("/api/admin/notifications/broadcast", Map.of("title", "Salut", "link", "https://evil.example"))
                .andExpect(status().isBadRequest());
        adminPost("/api/admin/notifications/broadcast", Map.of("title", "Magazin nou!", "body", "Am adăugat produse noi.",
                "link", "#/magazin")).andExpect(status().isOk()).andExpect(jsonPath("$.recipients").isNumber());
        assertThat(notifications(active, "ANNOUNCEMENT")).isEqualTo(1);
        assertThat(notifications(banned, "ANNOUNCEMENT")).isZero();
    }

    // ------------------------------------------------------------------ utilizatori

    @Test
    void usersCanBeSearchedAdjustedAndSuspended() throws Exception {
        TestUser u = register();
        TestUser otherAdmin = register();
        jdbc.update("update users set role = 'ADMIN' where id = ?", otherAdmin.id());

        JsonNode found = readJson(mvc.perform(getAs("/api/admin/users?q=" + u.email().toUpperCase(), admin)).andReturn());
        assertThat(found.at("/items/0/id").asText()).isEqualTo(u.id());
        assertThat(found.toString()).doesNotContain("password");

        clock.advance(Duration.ofMinutes(1));
        int balance = points(u);
        int xp = xp(u);
        adminPost("/api/admin/users/" + u.id() + "/points", Map.of("amount", 500, "reason", "x")).andExpect(status().isBadRequest());
        adminPost("/api/admin/users/" + u.id() + "/points", Map.of("amount", 500, "reason", "Premiu concurs"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.newBalance").value(balance + 500));
        assertThat(xp(u)).isEqualTo(xp);
        adminPost("/api/admin/users/" + u.id() + "/points", Map.of("amount", -1_000_000, "reason", "Corecție"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INSUFFICIENT_POINTS"));
        assertThat(notifications(u, "ACCOUNT")).isEqualTo(1);

        JsonNode detail = readJson(mvc.perform(getAs("/api/admin/users/" + u.id(), admin)).andExpect(status().isOk()).andReturn());
        assertThat(detail.at("/recentTransactions/0/type").asText()).isEqualTo("ADMIN_ADJUSTMENT");
        assertThat(detail.path("audit")).hasSize(1);

        adminPost("/api/admin/users/" + u.id() + "/ban", Map.of("banned", true)).andExpect(status().isBadRequest());
        adminPost("/api/admin/users/" + admin.id() + "/ban", Map.of("banned", true, "reason", "test")).andExpect(status().isConflict());
        adminPost("/api/admin/users/" + otherAdmin.id() + "/ban", Map.of("banned", true, "reason", "test")).andExpect(status().isConflict());
        adminPost("/api/admin/users/" + u.id() + "/ban", Map.of("banned", true, "reason", "Spam repetat")).andExpect(status().isOk());
        mvc.perform(getAs("/api/user/me", u)).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/login", null, Map.of("email", u.email(), "password", "Parola-Sigura-123")))
                .andExpect(status().isForbidden());

        adminPost("/api/admin/users/" + u.id() + "/ban", Map.of("banned", false)).andExpect(status().isOk());
        // Tokenul vechi ramane invalid (versiunea s-a schimbat), dar contul se poate autentifica din nou
        mvc.perform(getAs("/api/user/me", u)).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/login", null, Map.of("email", u.email(), "password", "Parola-Sigura-123")))
                .andExpect(status().isOk());

        JsonNode audit = readJson(mvc.perform(getAs("/api/admin/audit?targetType=USER&targetId=" + u.id(), admin)).andReturn());
        assertThat(audit.path("items")).hasSize(3);
        assertThat(audit.at("/items/0/action").asText()).isEqualTo("USER_UNBAN");
    }
}
