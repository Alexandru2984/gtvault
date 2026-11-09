package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Recenzii verificate, wishlist, notificari, profil public, GDPR si feed-ul comunitatii. */
class SocialFeaturesIntegrationTest extends AbstractIntegrationTest {

    /** Simuleaza o comanda deja primita (sau in curs) pentru un produs. */
    private String grant(TestUser u, String rewardId, String status) {
        String orderId = "ORD-T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("insert into orders (id, user_id, status, subtotal, total, created_at, updated_at) values (?, ?, ?, 0, 0, ?, ?)",
                orderId, u.id(), status, now, now);
        jdbc.update("""
                insert into purchase_records (id, order_id, user_id, reward_id, reward_name, quantity, unit_price, points_spent,
                                              status, purchase_date, fulfillment)
                select ?, ?, ?, id, name, 1, price, price, ?, ?, fulfillment from rewards where id = ?""",
                "pur-" + UUID.randomUUID(), orderId, u.id(), status, now, rewardId);
        return orderId;
    }

    private ResultActions review(TestUser u, String rewardId, Integer rating, String comment) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("rating", rating);
        body.put("comment", comment);
        return mvc.perform(postJson("/api/rewards/" + rewardId + "/reviews", u, body));
    }

    private int notificationCount(TestUser u, String type) {
        Integer c = jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = ?", Integer.class, u.id(), type);
        return c == null ? 0 : c;
    }

    // ------------------------------------------------------------------ recenzii

    @Test
    void onlyCustomersWhoReceivedTheProductCanReviewAndAreRewardedOnce() throws Exception {
        TestUser u = register();
        review(u, "rew-udemy", 5, "Foarte util pentru învățat.").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PURCHASE_REQUIRED"));
        grant(u, "rew-mouse", "PLACED");
        review(u, "rew-mouse", 5, "Încă nu a ajuns, dar sigur e bun.").andExpect(status().isForbidden());

        grant(u, "rew-udemy", "FULFILLED");
        review(u, "rew-udemy", 6, "Foarte util pentru învățat.").andExpect(status().isBadRequest());
        review(u, "rew-udemy", 5, "Scurt").andExpect(status().isBadRequest());
        review(u, "rew-udemy", 5, "x".repeat(1001)).andExpect(status().isBadRequest());

        JsonNode before = readJson(mvc.perform(getAs("/api/rewards/rew-udemy/reviews", u)).andReturn());
        assertThat(before.at("/eligibility/canReview").asBoolean()).isTrue();
        assertThat(before.at("/eligibility/rewardPoints").asInt()).isEqualTo(30);

        int start = points(u);
        JsonNode created = readJson(review(u, "rew-udemy", 5, "  Cursuri excelente, <b>recomand</b>!  ")
                .andExpect(status().isCreated()).andReturn());
        assertThat(created.path("rewardPoints").asInt()).isEqualTo(30);
        assertThat(created.path("badgesUnlocked").toString()).contains("CRITIC");
        assertThat(created.at("/review/verified").asBoolean()).isTrue();
        assertThat(created.at("/review/comment").asText()).isEqualTo("Cursuri excelente, <b>recomand</b>!");
        assertThat(points(u)).isEqualTo(start + 30 + 25);

        review(u, "rew-udemy", 4, "A doua recenzie pentru același produs.").andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_REVIEWED"));

        mvc.perform(authed(put("/api/rewards/rew-udemy/reviews/mine").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"comment\":\"Bun, dar unele cursuri sunt vechi.\"}"), u))
                .andExpect(status().isOk()).andExpect(jsonPath("$.review.rating").value(4))
                .andExpect(jsonPath("$.review.updatedAt").isNotEmpty());

        // Stergerea si rescrierea nu mai aduc puncte
        mvc.perform(authed(delete("/api/rewards/rew-udemy/reviews/mine"), u)).andExpect(status().isOk());
        int afterDelete = points(u);
        review(u, "rew-udemy", 5, "Revin cu o recenzie nouă și completă.").andExpect(status().isCreated())
                .andExpect(jsonPath("$.rewardPoints").value(0));
        assertThat(points(u)).isEqualTo(afterDelete);
    }

    @Test
    void rewardedReviewsAreCappedPerDay() throws Exception {
        TestUser u = register();
        grant(u, "rew-ghidgpt", "FULFILLED");
        jdbc.update("""
                insert into user_daily_counters (user_id, counter_date, counter_key, counter_value) values (?, '2026-11-09', 'REVIEWS_REWARDED', 3)""",
                u.id());
        int start = points(u);
        review(u, "rew-ghidgpt", 4, "Ghid bun pentru începători în AI.").andExpect(status().isCreated())
                .andExpect(jsonPath("$.rewardPoints").value(0));
        assertThat(points(u)).isEqualTo(start);
        // Recompensa ramane disponibila pentru alta zi (daca recenzia e rescrisa)
        assertThat(jdbc.queryForObject("select count(*) from review_rewards where user_id = ?", Integer.class, u.id())).isZero();
    }

    @Test
    void reviewListingHelpfulVotesReportsAndCatalogRating() throws Exception {
        TestUser a = register();
        TestUser b = register();
        TestUser c = register();
        grant(a, "rew-meet", "FULFILLED");
        grant(b, "rew-meet", "FULFILLED");
        JsonNode ra = readJson(review(a, "rew-meet", 5, "Experiență fantastică, mentori super.").andReturn());
        review(b, "rew-meet", 3, "Ok, dar a fost prea scurt evenimentul.").andExpect(status().isCreated());
        String reviewA = ra.at("/review/id").asText();

        mvc.perform(postJson("/api/reviews/" + reviewA + "/helpful", a, null)).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/reviews/" + reviewA + "/helpful", c, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.helpful").value(true)).andExpect(jsonPath("$.helpfulCount").value(1));
        mvc.perform(postJson("/api/reviews/" + reviewA + "/helpful", b, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(2));
        mvc.perform(postJson("/api/reviews/" + reviewA + "/helpful", c, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.helpful").value(false)).andExpect(jsonPath("$.helpfulCount").value(1));
        mvc.perform(postJson("/api/reviews/" + reviewA + "/helpful", c, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.helpfulCount").value(2));
        assertThat(notificationCount(a, "REVIEW")).isEqualTo(1); // doar la primul vot (prag)

        JsonNode list = readJson(mvc.perform(getAs("/api/rewards/rew-meet/reviews?sort=helpful", c)).andExpect(status().isOk()).andReturn());
        assertThat(list.path("totalReviews").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(list.at("/reviews/0/id").asText()).isEqualTo(reviewA);
        assertThat(list.at("/reviews/0/votedHelpful").asBoolean()).isTrue();
        assertThat(list.at("/reviews/0/author/name").asText()).isEqualTo("Tester Activ");
        assertThat(list.at("/reviews/0/author").has("email")).isFalse();
        assertThat(list.at("/eligibility/reason").asText()).isEqualTo("PURCHASE_REQUIRED");
        mvc.perform(getAs("/api/rewards/rew-meet/reviews?sort=hack", null)).andExpect(status().isBadRequest());
        mvc.perform(getAs("/api/rewards/nu-exista/reviews", null)).andExpect(status().isNotFound());

        // Numele autorului este cel curent (nu o copie veche)
        mvc.perform(postJson("/api/user/me", a, Map.of("name", "Ana Nouă"))).andExpect(status().isOk());
        JsonNode renamed = readJson(mvc.perform(getAs("/api/rewards/rew-meet/reviews", null)).andReturn());
        assertThat(renamed.toString()).contains("Ana Nouă");

        mvc.perform(postJson("/api/reviews/" + reviewA + "/report", c, Map.of("reason", "spam"))).andExpect(status().isOk());
        mvc.perform(postJson("/api/reviews/" + reviewA + "/report", c, Map.of("reason", "spam"))).andExpect(status().isOk());
        mvc.perform(postJson("/api/reviews/" + reviewA + "/report", c, Map.of("reason", "nu-place"))).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/reviews/" + reviewA + "/report", a, Map.of("reason", "SPAM"))).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from review_reports where review_id = ?", Integer.class, reviewA)).isEqualTo(1);

        JsonNode catalog = readJson(mvc.perform(getAs("/api/rewards/rew-meet", null)).andReturn());
        assertThat(catalog.at("/reward/reviewCount").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(catalog.at("/reward/rating").asDouble()).isBetween(1.0, 5.0);
        mvc.perform(getAs("/api/rewards?sort=rating", null)).andExpect(status().isOk());

        // Recenziile ascunse de moderator dispar din liste si din medie
        jdbc.update("update product_reviews set hidden = true where id = ?", reviewA);
        JsonNode hidden = readJson(mvc.perform(getAs("/api/rewards/rew-meet/reviews", null)).andReturn());
        assertThat(hidden.toString()).doesNotContain(reviewA);
        mvc.perform(postJson("/api/reviews/" + reviewA + "/helpful", c, null)).andExpect(status().isNotFound());
        JsonNode mine = readJson(mvc.perform(getAs("/api/user/reviews", a)).andReturn());
        assertThat(mine.at("/0/hidden").asBoolean()).isTrue();
    }

    @Test
    void pendingReviewsListsReceivedProductsWithoutReview() throws Exception {
        TestUser u = register();
        grant(u, "rew-templatecv", "FULFILLED");
        grant(u, "rew-agenda", "PLACED");
        JsonNode pending = readJson(mvc.perform(getAs("/api/user/reviews/pending", u)).andExpect(status().isOk()).andReturn());
        assertThat(pending).hasSize(1);
        assertThat(pending.at("/0/rewardId").asText()).isEqualTo("rew-templatecv");
        assertThat(pending.at("/0/rewardPoints").asInt()).isEqualTo(30);
        review(u, "rew-templatecv", 5, "Template curat și ușor de editat.").andExpect(status().isCreated());
        assertThat(readJson(mvc.perform(getAs("/api/user/reviews/pending", u)).andReturn())).isEmpty();
    }

    // ------------------------------------------------------------------ wishlist

    @Test
    void wishlistIsServerSideIdempotentAndCounted() throws Exception {
        TestUser u = register();
        mvc.perform(authed(put("/api/wishlist/rew-mouse"), u)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(authed(put("/api/wishlist/rew-mouse"), u)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        clock.advance(java.time.Duration.ofSeconds(1));
        mvc.perform(authed(put("/api/wishlist/rew-tricou"), u)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
        mvc.perform(authed(put("/api/wishlist/rew-lucky"), u)).andExpect(status().isNotFound());
        mvc.perform(authed(put("/api/wishlist/nu-exista"), u)).andExpect(status().isNotFound());
        mvc.perform(put("/api/wishlist/rew-mouse")).andExpect(status().isUnauthorized());

        JsonNode list = readJson(mvc.perform(getAs("/api/wishlist", u)).andExpect(status().isOk()).andReturn());
        assertThat(list.path("count").asInt()).isEqualTo(2);
        assertThat(list.at("/items/0/reward/id").asText()).isEqualTo("rew-tricou");
        assertThat(list.at("/items/0/reward/wishlistCount").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(readJson(mvc.perform(getAs("/api/wishlist/ids", u)).andReturn()).toString()).contains("rew-mouse", "rew-tricou");

        mvc.perform(authed(delete("/api/wishlist/rew-mouse"), u)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
        mvc.perform(authed(delete("/api/wishlist/rew-mouse"), u)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void wishlistersAreNotifiedWhenTheirProductEntersTheDailyDeals() throws Exception {
        advanceDays(137); // o zi in care nu exista oferte manuale sau generate de alte teste
        TestUser u = register();
        List<String> candidates = jdbc.queryForList(
                "select id from rewards where active and stock_count >= 5 and fulfillment <> 'BADGE'", String.class);
        for (String id : candidates) {
            mvc.perform(authed(put("/api/wishlist/" + id), u)).andExpect(status().isOk());
        }
        assertThat(notificationCount(u, "WISHLIST")).isZero();
        mvc.perform(getAs("/api/rewards", null)).andExpect(status().isOk());
        assertThat(notificationCount(u, "WISHLIST")).isEqualTo(2);
        mvc.perform(getAs("/api/rewards", null)).andExpect(status().isOk());
        assertThat(notificationCount(u, "WISHLIST")).isEqualTo(2); // ofertele nu se regenereaza
        JsonNode wl = readJson(mvc.perform(getAs("/api/wishlist", u)).andReturn());
        assertThat(wl.path("onSale").asInt()).isEqualTo(2);
    }

    @Test
    void wishlistersAreNotifiedWhenAnOrderCancellationRestocksTheProduct() throws Exception {
        TestUser watcher = register();
        TestUser buyer = register();
        jdbc.update("update rewards set stock_count = 1, in_stock = true where id = 'rew-agenda'");
        try {
            setPoints(buyer, 5000);
            mvc.perform(authed(put("/api/wishlist/rew-agenda"), watcher)).andExpect(status().isOk());
            mvc.perform(postJson("/api/cart/items", buyer, Map.of("rewardId", "rew-agenda", "quantity", 1))).andExpect(status().isOk());
            JsonNode order = readJson(mvc.perform(postJson("/api/checkout", buyer, Map.of("shipping", Map.of(
                            "name", "Ana Popescu", "phone", "0722123456", "address", "Str. Exemplu nr. 10",
                            "city", "Cluj-Napoca", "county", "Cluj", "postalCode", "400001")))
                    .header("Idempotency-Key", "k-" + UUID.randomUUID())).andExpect(status().isOk()).andReturn());
            assertThat(jdbc.queryForObject("select stock_count from rewards where id = 'rew-agenda'", Integer.class)).isZero();
            mvc.perform(postJson("/api/orders/" + order.at("/order/id").asText() + "/cancel", buyer, null)).andExpect(status().isOk());
            assertThat(notificationCount(watcher, "WISHLIST")).isEqualTo(1);
            assertThat(notificationCount(buyer, "WISHLIST")).isZero();
        } finally {
            jdbc.update("update rewards set stock_count = 50, in_stock = true where id = 'rew-agenda'");
        }
    }

    // ------------------------------------------------------------------ notificari

    @Test
    void notificationsCanBeListedReadAndDeletedOnlyByTheirOwner() throws Exception {
        TestUser u = register();
        TestUser other = register();
        mvc.perform(postJson("/api/gamification/daily-claim", u, null)).andExpect(status().isOk());
        mvc.perform(postJson("/api/gamification/daily-claim", u, null));
        jdbc.update("insert into notifications (id, user_id, type, title, created_at) values (?, ?, 'SYSTEM', 'Test 1', now()), (?, ?, 'SYSTEM', 'Test 2', now())",
                "ntf-a-" + u.id(), u.id(), "ntf-b-" + u.id(), u.id());

        JsonNode list = readJson(mvc.perform(getAs("/api/notifications?limit=5", u)).andExpect(status().isOk()).andReturn());
        long unread = list.path("unread").asLong();
        assertThat(unread).isGreaterThanOrEqualTo(2);
        mvc.perform(getAs("/api/notifications/unread-count", u)).andExpect(jsonPath("$.unread").value(unread));

        mvc.perform(postJson("/api/notifications/ntf-a-" + u.id() + "/read", other, null)).andExpect(status().isNotFound());
        mvc.perform(authed(delete("/api/notifications/ntf-a-" + u.id()), other)).andExpect(status().isNotFound());
        mvc.perform(postJson("/api/notifications/ntf-a-" + u.id() + "/read", u, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(unread - 1));
        mvc.perform(authed(delete("/api/notifications/ntf-b-" + u.id()), u)).andExpect(status().isOk());
        mvc.perform(postJson("/api/notifications/read-all", u, null)).andExpect(status().isOk());
        mvc.perform(getAs("/api/notifications/unread-count", u)).andExpect(jsonPath("$.unread").value(0));
        mvc.perform(authed(delete("/api/notifications"), u)).andExpect(status().isOk());
        assertThat(readJson(mvc.perform(getAs("/api/notifications", u)).andReturn()).path("items")).isEmpty();
        mvc.perform(getAs("/api/notifications", null)).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ profil public

    @Test
    void publicProfileShowsOnlyPublicDataAndRespectsPrivacy() throws Exception {
        TestUser u = register();
        TestUser viewer = register();
        grant(u, "rew-mentorat", "FULFILLED");
        review(u, "rew-mentorat", 5, "Sesiune foarte utilă cu mentorul meu.").andExpect(status().isCreated());
        mvc.perform(postJson("/api/user/me", u, Map.of("bio", "  Pasionat de\n\nbackend și Spring  "))).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.bio").value("Pasionat de backend și Spring"));
        mvc.perform(postJson("/api/user/me", u, Map.of("bio", "x".repeat(161)))).andExpect(status().isBadRequest());

        JsonNode p = readJson(mvc.perform(getAs("/api/users/" + u.id(), null)).andExpect(status().isOk()).andReturn());
        assertThat(p.path("private").asBoolean()).isFalse();
        assertThat(p.path("bio").asText()).isEqualTo("Pasionat de backend și Spring");
        assertThat(p.path("badges").toString()).contains("CRITIC");
        assertThat(p.path("reviewCount").asInt()).isEqualTo(1);
        assertThat(p.path("recentReviews")).hasSize(1);
        assertThat(p.path("xpRank").asInt()).isPositive();
        String raw = p.toString();
        assertThat(raw).doesNotContain(u.email()).doesNotContain("activityPoints").doesNotContain("referralCode")
                .doesNotContain("password");

        mvc.perform(postJson("/api/user/me", u, Map.of("publicProfile", false))).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.publicProfile").value(false));
        JsonNode hidden = readJson(mvc.perform(getAs("/api/users/" + u.id(), viewer)).andExpect(status().isOk()).andReturn());
        assertThat(hidden.path("private").asBoolean()).isTrue();
        assertThat(hidden.has("badges")).isFalse();
        assertThat(hidden.has("bio")).isFalse();
        JsonNode self = readJson(mvc.perform(getAs("/api/users/" + u.id(), u)).andReturn());
        assertThat(self.path("isMe").asBoolean()).isTrue();
        assertThat(self.has("badges")).isTrue();
        // In recenzii, autorul privat nu mai are link catre profil
        JsonNode reviews = readJson(mvc.perform(getAs("/api/rewards/rew-mentorat/reviews", viewer)).andReturn());
        for (JsonNode r : reviews.path("reviews")) {
            if (r.path("author").path("name").asText().equals("Tester Activ") && !r.path("mine").asBoolean()) {
                assertThat(r.at("/author/id").isNull()).isTrue();
            }
        }

        mvc.perform(getAs("/api/users/nu-exista", null)).andExpect(status().isNotFound());
        jdbc.update("update users set banned = true where id = ?", viewer.id());
        mvc.perform(getAs("/api/users/" + viewer.id(), null)).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ GDPR

    @Test
    void dataExportContainsPersonalDataButNoSecrets() throws Exception {
        TestUser u = register();
        grant(u, "rew-tricou", "FULFILLED");
        mvc.perform(authed(put("/api/wishlist/rew-mouse"), u)).andExpect(status().isOk());
        JsonNode export = readJson(mvc.perform(getAs("/api/user/export", u)).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn());
        assertThat(export.at("/profile/account/email").asText()).isEqualTo(u.email());
        assertThat(export.path("orders")).hasSize(1);
        assertThat(export.path("order_items")).hasSize(1);
        assertThat(export.path("wishlist")).hasSize(1);
        assertThat(export.path("point_transactions")).isNotEmpty();
        String raw = export.toString();
        assertThat(raw).doesNotContain("$2a$").doesNotContain("token_version").doesNotContain("\"password\"");
        mvc.perform(getAs("/api/user/export", null)).andExpect(status().isUnauthorized());
    }

    @Test
    void accountDeletionRequiresPasswordConfirmationAndNoOrdersInProgress() throws Exception {
        TestUser u = register();
        mvc.perform(authed(put("/api/wishlist/rew-mouse"), u)).andExpect(status().isOk());
        String order = grant(u, "rew-sticker", "SHIPPED");

        mvc.perform(postJson("/api/user/account/delete", u, Map.of("password", "Parola-Sigura-123", "confirmation", "da")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("CONFIRMATION_REQUIRED"));
        mvc.perform(postJson("/api/user/account/delete", u, Map.of("password", "gresita", "confirmation", "STERGE")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("WRONG_PASSWORD"));
        mvc.perform(postJson("/api/user/account/delete", u, Map.of("password", "Parola-Sigura-123", "confirmation", "STERGE")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ORDERS_IN_PROGRESS"));

        jdbc.update("update orders set status = 'DELIVERED' where id = ?", order);
        mvc.perform(postJson("/api/user/account/delete", u, Map.of("password", "Parola-Sigura-123", "confirmation", "sterge")))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, u.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from wishlist_items where user_id = ?", Integer.class, u.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from orders where user_id = ?", Integer.class, u.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from point_transactions where user_id = ?", Integer.class, u.id())).isZero();
        mvc.perform(getAs("/api/user/me", u)).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/auth/login", null, Map.of("email", u.email(), "password", "Parola-Sigura-123")))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ feed comunitate

    @Test
    void communityFeedShowsActivityAndKeepsOrdersAnonymous() throws Exception {
        TestUser u = register();
        TestUser shy = register();
        mvc.perform(postJson("/api/user/me", u, Map.of("name", "Maria Feed"))).andExpect(status().isOk());
        mvc.perform(postJson("/api/user/me", shy, Map.of("name", "Ion Discret", "publicProfile", false))).andExpect(status().isOk());
        grant(u, "rew-udemy", "FULFILLED");
        grant(shy, "rew-udemy", "FULFILLED");
        review(u, "rew-udemy", 5, "Platformă excelentă pentru cursuri.").andExpect(status().isCreated());
        clock.advance(java.time.Duration.ofSeconds(5));
        review(shy, "rew-udemy", 4, "Bun, dar prea multe cursuri similare.").andExpect(status().isCreated());

        JsonNode feed = readJson(mvc.perform(getAs("/api/community/activity?limit=30", null)).andExpect(status().isOk()).andReturn());
        List<String> titles = new ArrayList<>();
        feed.forEach(i -> titles.add(i.path("title").asText()));
        assertThat(titles).anyMatch(t -> t.startsWith("Maria Feed a dat 5★"));
        assertThat(titles).noneMatch(t -> t.contains("Ion Discret"));
        assertThat(titles).anyMatch(t -> t.startsWith("Un membru a dat 4★"));
        for (JsonNode i : feed) {
            if ("ORDER".equals(i.path("type").asText())) {
                assertThat(i.path("actor").isNull()).isTrue();
                assertThat(i.path("title").asText()).startsWith("Un membru a revendicat");
            }
        }
        JsonNode stats = readJson(mvc.perform(getAs("/api/community/stats", null)).andExpect(status().isOk()).andReturn());
        assertThat(stats.path("members").asLong()).isPositive();
        assertThat(stats.path("reviews").asLong()).isGreaterThanOrEqualTo(2);
        assertThat(stats.has("xpEarnedThisWeek")).isTrue();
    }
}
