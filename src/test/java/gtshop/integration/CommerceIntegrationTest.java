package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class CommerceIntegrationTest extends AbstractIntegrationTest {
    /** Oferta manuala permanenta in teste: opreste generarea automata (care ar alege produse aleatoriu). */
    private static final String MANUAL_DEAL = "deal-test-manual";

    @BeforeEach
    void stableCatalog() {
        jdbc.update("delete from flash_deals where auto_generated = true");
        jdbc.update("delete from flash_deals where id = ?", MANUAL_DEAL);
        jdbc.update("""
                insert into flash_deals (id, reward_id, discount_percent, starts_at, ends_at, max_quantity, sold_quantity,
                                         per_user_limit, auto_generated, created_at)
                values (?, 'rew-alltracks', 20, ?, ?, 1000, 0, 1, false, ?)""",
                MANUAL_DEAL, Timestamp.from(START.minus(Duration.ofDays(1))), Timestamp.from(START.plus(Duration.ofDays(60))),
                Timestamp.from(START));
        jdbc.update("update rewards set price = 900, stock_count = 9999, in_stock = true, min_tier = null where id = 'rew-templatecv'");
        jdbc.update("update rewards set price = 180, stock_count = 300, in_stock = true where id = 'rew-sticker'");
        jdbc.update("update rewards set price = 3500, stock_count = 1000, in_stock = true where id = 'rew-alltracks'");
    }

    private ResultActions addToCart(TestUser u, String rewardId, int qty) throws Exception {
        return mvc.perform(postJson("/api/cart/items", u, Map.of("rewardId", rewardId, "quantity", qty)));
    }

    private MockHttpServletRequestBuilder checkout(TestUser u, String key, Map<String, Object> body) throws Exception {
        return postJson("/api/checkout", u, body == null ? Map.of() : body).header("Idempotency-Key", key);
    }

    private static String key() {
        return "k-" + UUID.randomUUID();
    }

    private static Map<String, Object> shipping() {
        return Map.of("name", "Ana Popescu", "phone", "0722 123 456", "address", "Str. Exemplu nr. 10, bl. A2",
                "city", "Cluj-Napoca", "county", "cluj", "postalCode", "400001");
    }

    // ------------------------------------------------------------------ catalog

    @Test
    void publicCatalogSearchSortAndDetails() throws Exception {
        JsonNode all = readJson(mvc.perform(getAs("/api/rewards", null)).andExpect(status().isOk()).andReturn());
        List<String> ids = new ArrayList<>();
        all.path("rewards").forEach(r -> ids.add(r.path("id").asText()));
        assertThat(ids).contains("rew-mouse", "rew-templatecv").doesNotContain("rew-lucky");
        assertThat(all.path("categories").toString()).contains("Premii fizice");

        JsonNode search = readJson(mvc.perform(getAs("/api/rewards?q=agenda", null)).andReturn());
        assertThat(search.at("/rewards/0/name").asText()).isEqualTo("Agendă GT");

        JsonNode sorted = readJson(mvc.perform(getAs("/api/rewards?sort=price_asc", null)).andReturn());
        int prev = 0;
        for (JsonNode r : sorted.path("rewards")) {
            assertThat(r.path("price").asInt()).isGreaterThanOrEqualTo(prev);
            prev = r.path("price").asInt();
        }
        mvc.perform(getAs("/api/rewards?sort=hack", null)).andExpect(status().isBadRequest());

        JsonNode detail = readJson(mvc.perform(getAs("/api/rewards/rew-alltracks", null)).andExpect(status().isOk()).andReturn());
        assertThat(detail.at("/reward/deal/dealPrice").asInt()).isEqualTo(2800);
        assertThat(detail.path("related")).isNotEmpty();
        mvc.perform(getAs("/api/rewards/rew-lucky", null)).andExpect(status().isNotFound());
    }

    @Test
    void dailyDealsAreGeneratedOnceWhenNoManualDealIsActive() throws Exception {
        jdbc.update("delete from flash_deals where id = ?", MANUAL_DEAL);
        JsonNode first = readJson(mvc.perform(getAs("/api/flash-deals", null)).andExpect(status().isOk()).andReturn());
        assertThat(first).hasSize(2);
        mvc.perform(getAs("/api/flash-deals", null)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from flash_deals where auto_generated = true", Integer.class)).isEqualTo(2);
        for (JsonNode d : first) {
            assertThat(d.path("dealPrice").asInt()).isLessThan(d.path("normalPrice").asInt());
        }
    }

    // ------------------------------------------------------------------ cos

    @Test
    void cartIsServerSideAndUsesCurrentPrices() throws Exception {
        TestUser u = register();
        addToCart(u, "rew-sticker", 2).andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(360));
        addToCart(u, "rew-sticker", 1).andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].quantity").value(3));
        addToCart(u, "rew-sticker", 3).andExpect(status().isBadRequest()); // maxim 5 per comanda
        addToCart(u, "nu-exista", 1).andExpect(status().isNotFound());
        addToCart(u, "rew-lucky", 1).andExpect(status().isNotFound());
        addToCart(u, "rew-sticker", 0).andExpect(status().isBadRequest());

        jdbc.update("update rewards set price = 200 where id = 'rew-sticker'");
        mvc.perform(getAs("/api/cart", u)).andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(600));

        mvc.perform(authed(put("/api/cart/items/rew-sticker").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"), u))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].quantity").value(1));
        mvc.perform(authed(delete("/api/cart/items/rew-sticker"), u)).andExpect(status().isOk()).andExpect(jsonPath("$.lines").isEmpty());
        mvc.perform(checkout(u, key(), null)).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ plata

    @Test
    void digitalOrderIsFulfilledWithVoucherAndIdempotent() throws Exception {
        TestUser u = register();
        addToCart(u, "rew-templatecv", 1).andExpect(status().isOk());
        JsonNode quote = readJson(mvc.perform(postJson("/api/checkout/quote", u, Map.of())).andExpect(status().isOk()).andReturn());
        assertThat(quote.path("total").asInt()).isEqualTo(900);
        assertThat(quote.path("requiresShipping").asBoolean()).isFalse();

        String k = key();
        JsonNode res = readJson(mvc.perform(checkout(u, k, Map.of("expectedTotal", 900))).andExpect(status().isOk()).andReturn());
        assertThat(res.at("/order/status").asText()).isEqualTo("FULFILLED");
        String voucher = res.at("/order/items/0/voucherCode").asText();
        assertThat(voucher).matches("GT-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}");
        assertThat(res.path("badgesUnlocked").toString()).contains("FIRST_ORDER");
        assertThat(points(u)).isEqualTo(1000 - 900 + 25);

        JsonNode replay = readJson(mvc.perform(checkout(u, k, Map.of())).andExpect(status().isOk()).andReturn());
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(replay.at("/order/id").asText()).isEqualTo(res.at("/order/id").asText());
        assertThat(points(u)).isEqualTo(125);
        assertThat(jdbc.queryForObject("select count(*) from orders where user_id = ?", Integer.class, u.id())).isEqualTo(1);
        mvc.perform(getAs("/api/cart", u)).andExpect(jsonPath("$.lines").isEmpty());

        mvc.perform(getAs("/api/vouchers/verify?code=" + voucher.toLowerCase(), null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.rewardName").value("Template CV"))
                .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(getAs("/api/vouchers/verify?code=GT-AAAA-BBBB-CCCC", null)).andExpect(status().isNotFound());
        String publicVerification = mvc.perform(postJson("/api/vouchers/verify", null, Map.of("code", voucher.toLowerCase())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(publicVerification).doesNotContain(u.email()).doesNotContain(u.id());

        String orderId = res.at("/order/id").asText();
        mvc.perform(postJson("/api/orders/" + orderId + "/cancel", u, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CANNOT_CANCEL"));
        TestUser other = register();
        mvc.perform(getAs("/api/orders/" + orderId, other)).andExpect(status().isNotFound());
        mvc.perform(getAs("/api/orders", u)).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void checkoutRequiresValidIdempotencyKeyAndEnoughPoints() throws Exception {
        TestUser u = register();
        addToCart(u, "rew-templatecv", 1).andExpect(status().isOk());
        mvc.perform(postJson("/api/checkout", u, Map.of())).andExpect(status().isBadRequest());
        mvc.perform(checkout(u, "scurt", null)).andExpect(status().isBadRequest());
        setPoints(u, 100);
        mvc.perform(checkout(u, key(), null)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_POINTS"));
        assertThat(points(u)).isEqualTo(100);
        assertThat(jdbc.queryForObject("select stock_count from rewards where id = 'rew-templatecv'", Integer.class)).isEqualTo(9999);
        mvc.perform(getAs("/api/cart", u)).andExpect(jsonPath("$.lines[0].rewardId").value("rew-templatecv"));
        setPoints(u, 5000);
        mvc.perform(checkout(u, key(), Map.of("expectedTotal", 1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PRICE_CHANGED"));
    }

    @Test
    void concurrentDoubleSubmitCreatesSingleOrder() throws Exception {
        TestUser u = register();
        addToCart(u, "rew-sticker", 1).andExpect(status().isOk());
        String k = key();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                calls.add(() -> mvc.perform(checkout(u, k, Map.of("shipping", shipping()))).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> f : pool.invokeAll(calls)) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("select count(*) from orders where user_id = ?", Integer.class, u.id())).isEqualTo(1);
        assertThat(points(u)).isEqualTo(1000 - 180 + 25);
    }

    @Test
    void lastUnitCannotBeSoldTwice() throws Exception {
        jdbc.update("update rewards set stock_count = 1 where id = 'rew-sticker'");
        TestUser a = register();
        TestUser b = register();
        addToCart(a, "rew-sticker", 1).andExpect(status().isOk());
        addToCart(b, "rew-sticker", 1).andExpect(status().isOk());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses = new ArrayList<>();
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> mvc.perform(checkout(a, key(), Map.of("shipping", shipping()))).andReturn().getResponse().getStatus(),
                    () -> mvc.perform(checkout(b, key(), Map.of("shipping", shipping()))).andReturn().getResponse().getStatus());
            for (Future<Integer> f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("select stock_count from rewards where id = 'rew-sticker'", Integer.class)).isZero();
    }

    @Test
    void physicalOrderNeedsShippingAndCanBeCancelledWithFullRefund() throws Exception {
        TestUser u = register();
        addToCart(u, "rew-sticker", 2).andExpect(status().isOk());
        mvc.perform(checkout(u, key(), null)).andExpect(status().isBadRequest());
        Map<String, Object> badPhone = new HashMap<>(shipping());
        badPhone.put("phone", "12345");
        mvc.perform(checkout(u, key(), Map.of("shipping", badPhone))).andExpect(status().isBadRequest());
        Map<String, Object> badCounty = new HashMap<>(shipping());
        badCounty.put("county", "Atlantida");
        mvc.perform(checkout(u, key(), Map.of("shipping", badCounty))).andExpect(status().isBadRequest());

        JsonNode res = readJson(mvc.perform(checkout(u, key(), Map.of("shipping", shipping(), "notes", "Sun înainte")))
                .andExpect(status().isOk()).andReturn());
        String orderId = res.at("/order/id").asText();
        assertThat(res.at("/order/status").asText()).isEqualTo("PLACED");
        assertThat(res.at("/order/shipping/county").asText()).isEqualTo("Cluj");
        assertThat(res.at("/order/shipping/phone").asText()).isEqualTo("0722123456");
        assertThat(res.at("/order/canCancel").asBoolean()).isTrue();
        assertThat(res.at("/order/items/0/voucherCode").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select stock_count from rewards where id = 'rew-sticker'", Integer.class)).isEqualTo(298);
        mvc.perform(getAs("/api/orders/last-shipping", u)).andExpect(jsonPath("$.city").value("Cluj-Napoca"));

        int afterOrder = points(u);
        mvc.perform(postJson("/api/orders/" + orderId + "/cancel", u, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(points(u)).isEqualTo(afterOrder + 360);
        assertThat(jdbc.queryForObject("select stock_count from rewards where id = 'rew-sticker'", Integer.class)).isEqualTo(300);
        mvc.perform(postJson("/api/orders/" + orderId + "/cancel", u, null)).andExpect(status().isConflict());
    }

    @Test
    void promoCodesApplyOncePerUserAndAreReleasedOnCancel() throws Exception {
        String code = "TEST" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("""
                insert into promo_codes (id, code, discount_points, discount_percent, min_order_points, max_uses, current_uses,
                                         is_active, expires_at, created_at)
                values (?, ?, 0, 10, 100, 2, 0, true, ?, now())""",
                "promo-" + code, code, Timestamp.from(START.plus(Duration.ofDays(5))));
        TestUser u = register();
        addToCart(u, "rew-sticker", 1).andExpect(status().isOk());
        mvc.perform(postJson("/api/promo/validate", u, Map.of("code", code.toLowerCase()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.promo.valid").value(true)).andExpect(jsonPath("$.promoDiscount").value(18))
                .andExpect(jsonPath("$.total").value(162));
        mvc.perform(postJson("/api/promo/validate", u, Map.of("code", "NU-EXISTA"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.promo.valid").value(false));
        mvc.perform(checkout(u, key(), Map.of("promoCode", "NU-EXISTA", "shipping", shipping())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PROMO_INVALID"));

        JsonNode order = readJson(mvc.perform(checkout(u, key(), Map.of("promoCode", code, "shipping", shipping())))
                .andExpect(status().isOk()).andReturn());
        assertThat(order.at("/order/promoDiscount").asInt()).isEqualTo(18);
        assertThat(order.at("/order/total").asInt()).isEqualTo(162);

        addToCart(u, "rew-sticker", 1).andExpect(status().isOk());
        mvc.perform(postJson("/api/promo/validate", u, Map.of("code", code))).andExpect(jsonPath("$.promo.valid").value(false))
                .andExpect(jsonPath("$.promo.message").value("Ai folosit deja acest cod promoțional."));

        mvc.perform(postJson("/api/orders/" + order.at("/order/id").asText() + "/cancel", u, null)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select current_uses from promo_codes where code = ?", Integer.class, code)).isZero();
        mvc.perform(postJson("/api/promo/validate", u, Map.of("code", code))).andExpect(jsonPath("$.promo.valid").value(true));

        clock.advance(Duration.ofDays(6));
        mvc.perform(postJson("/api/promo/validate", u, Map.of("code", code))).andExpect(jsonPath("$.promo.valid").value(false))
                .andExpect(jsonPath("$.promo.message").value("Acest cod promoțional a expirat."));
    }

    @Test
    void flashDealHonoursPerUserLimitAndTierDiscountStacks() throws Exception {
        TestUser u = register();
        jdbc.update("update users set total_points_earned = 2600, activity_points = 20000 where id = ?", u.id()); // GOLD: -5%
        addToCart(u, "rew-alltracks", 2).andExpect(status().isOk());
        JsonNode q = readJson(mvc.perform(postJson("/api/checkout/quote", u, Map.of())).andReturn());
        // o unitate la pret flash (limita 1 per utilizator), a doua la pret normal
        assertThat(q.at("/lines/0/dealUnits").asInt()).isEqualTo(1);
        assertThat(q.path("subtotal").asInt()).isEqualTo(7000);
        assertThat(q.path("flashDiscount").asInt()).isEqualTo(700);
        assertThat(q.path("tier").asText()).isEqualTo("GOLD");
        assertThat(q.path("tierDiscount").asInt()).isEqualTo(315);
        assertThat(q.path("total").asInt()).isEqualTo(5985);

        JsonNode res = readJson(mvc.perform(checkout(u, key(), Map.of("expectedTotal", 5985))).andExpect(status().isOk()).andReturn());
        assertThat(res.at("/order/total").asInt()).isEqualTo(5985);
        assertThat(jdbc.queryForObject("select sold_quantity from flash_deals where id = ?", Integer.class, MANUAL_DEAL)).isEqualTo(1);

        addToCart(u, "rew-alltracks", 1).andExpect(status().isOk());
        JsonNode again = readJson(mvc.perform(postJson("/api/checkout/quote", u, Map.of())).andReturn());
        assertThat(again.at("/lines/0/dealUnits").asInt()).isZero();
    }

    @Test
    void heroBadgeIsDeliveredInAppOnce() throws Exception {
        TestUser u = register();
        setPoints(u, 5000);
        addToCart(u, "rew-badge", 1).andExpect(status().isOk());
        JsonNode res = readJson(mvc.perform(checkout(u, key(), null)).andExpect(status().isOk()).andReturn());
        assertThat(res.path("badgesUnlocked").toString()).contains("HERO");
        assertThat(res.at("/order/items/0/voucherCode").isNull()).isTrue();
        addToCart(u, "rew-badge", 1).andExpect(status().isOk());
        mvc.perform(checkout(u, key(), null)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CART_INVALID"));
    }

    @Test
    void minimumTierIsEnforced() throws Exception {
        jdbc.update("update rewards set min_tier = 'GOLD' where id = 'rew-templatecv'");
        TestUser u = register();
        addToCart(u, "rew-templatecv", 1).andExpect(status().isOk());
        mvc.perform(checkout(u, key(), null)).andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CART_INVALID"));
        jdbc.update("update users set total_points_earned = 3000 where id = ?", u.id());
        mvc.perform(checkout(u, key(), null)).andExpect(status().isOk());
    }
}
