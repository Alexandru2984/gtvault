package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.service.TimeService;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderHistoryIntegrationTest extends AbstractIntegrationTest {
    @Autowired TimeService time;

    private String order(TestUser user, String state, boolean physical, Instant created, String name) {
        String id = "ORD-H" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbc.update("""
                INSERT INTO orders (id, user_id, status, subtotal, total, requires_shipping, created_at, updated_at,
                    shipping_name, shipping_phone, shipping_address, shipping_city, shipping_county, shipping_postal_code,
                    notes, tracking_code, admin_note, promo_code, idempotency_key)
                VALUES (?, ?, ?, 200, 200, ?, ?, ?, 'Client Sintetic', '0700000000', 'Adresa sintetică 12',
                    'Oraș Test', 'Test', '000000', 'Notă privată', 'AWB-PRIVATE', 'Notă administrator', 'PROMO-PRIVATE', ?)
                """, id, user.id(), state, physical, Timestamp.from(created), Timestamp.from(created), UUID.randomUUID().toString());
        item(user, id, name, "rew-templatecv", 2);
        return id;
    }

    private void item(TestUser user, String orderId, String name, String rewardId, int quantity) {
        String id = "history-" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO purchase_records (id, order_id, user_id, reward_id, reward_name, quantity, unit_price,
                    points_spent, purchase_date, status, fulfillment, voucher_code)
                VALUES (?, ?, ?, ?, ?, ?, 100, ?, ?, 'FULFILLED', 'DIGITAL', ?)
                """, id, orderId, user.id(), rewardId, name, quantity, quantity * 100, Timestamp.from(START), "SYNTHETIC-" + UUID.randomUUID());
    }

    private JsonNode list(TestUser user, String... parameters) throws Exception {
        var request = getAs("/api/orders", user);
        for (int i = 0; i < parameters.length; i += 2) request.param(parameters[i], parameters[i + 1]);
        return readJson(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    @Test
    void authenticationAndEmptyHistoryDoNotRevealAnotherAccountsOrders() throws Exception {
        TestUser owner = register();
        TestUser other = register();
        order(owner, "FULFILLED", false, START, "Privat");
        mvc.perform(getAs("/api/orders", null)).andExpect(status().isUnauthorized());
        JsonNode empty = list(other, "userId", owner.id());
        assertThat(empty.path("items")).isEmpty();
        assertThat(empty.path("totalItems").asInt()).isZero();
        assertThat(empty.path("totalPages").asInt()).isZero();
    }

    @Test
    void summaryOmitsDeliveryDetailsVouchersAndInternalFieldsButDetailStillWorks() throws Exception {
        TestUser owner = register();
        TestUser other = register();
        String id = order(owner, "PLACED", true, START, "Denumire istorică");
        JsonNode summary = list(owner).path("items").get(0);
        assertThat(summary.path("id").asText()).isEqualTo(id);
        assertThat(summary.path("itemCount").asInt()).isEqualTo(2);
        assertThat(summary.at("/items/0/available").asBoolean()).isTrue();
        assertThat(summary.at("/items/0/name").asText()).isEqualTo("Denumire istorică");
        assertThat(summary.toString()).doesNotContain("shipping", "voucher", "promo", "tracking", "notes", "admin", "idempotency",
                "Adresa sintetică", "0700000000", "SYNTHETIC-", owner.email(), owner.id());
        mvc.perform(getAs("/api/orders/" + id, owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.shipping.address").value("Adresa sintetică 12"))
                .andExpect(jsonPath("$.items[0].voucherCode").isNotEmpty());
        mvc.perform(getAs("/api/orders/" + id, other)).andExpect(status().isNotFound());
    }

    @Test
    void statusDeliveryAndSearchCombineWithoutDuplicatingOrders() throws Exception {
        TestUser owner = register();
        TestUser other = register();
        String found = order(owner, "FULFILLED", false, START, "Curs Java vechi");
        item(owner, found, "Java încă o linie", "rew-alltracks", 1);
        order(owner, "PLACED", true, START.minusSeconds(1), "Java fizic");
        order(owner, "CANCELLED", false, START.minusSeconds(2), "Java anulat");
        order(other, "FULFILLED", false, START, "Java privat");
        JsonNode page = list(owner, "q", "jAvA", "status", " fulfilled ", "delivery", "digital");
        assertThat(page.path("totalItems").asInt()).isEqualTo(1);
        assertThat(page.at("/items/0/id").asText()).isEqualTo(found);
        assertThat(page.at("/items/0/itemCount").asInt()).isEqualTo(3);
        assertThat(page.at("/items/0/items")).hasSize(2);
        assertThat(list(owner, "q", found.toLowerCase()).path("totalItems").asInt()).isEqualTo(1);
        assertThat(list(owner, "delivery", "PHYSICAL").at("/items/0/requiresShipping").asBoolean()).isTrue();
        assertThat(list(owner, "status", "CANCELLED").path("totalItems").asInt()).isEqualTo(1);
    }

    @Test
    void searchTreatsSqlWildcardsAndEscapesAsLiteralTextAndCannotSearchPrivateFields() throws Exception {
        TestUser owner = register();
        order(owner, "FULFILLED", false, START, "Curs 50%_! Java\\SQL");
        order(owner, "FULFILLED", false, START, "Alt curs");
        for (String literal : List.of("%", "_", "!", "Java\\SQL")) {
            assertThat(list(owner, "q", literal).path("totalItems").asInt()).as(literal).isEqualTo(1);
        }
        for (String hidden : List.of("' OR 1=1 --", "Adresa sintetică", "0700000000", "PROMO-PRIVATE", "SYNTHETIC-", "AWB-PRIVATE")) {
            assertThat(list(owner, "q", hidden).path("totalItems").asInt()).as(hidden).isZero();
        }
        assertThat(list(owner).path("totalItems").asInt()).isEqualTo(2);
    }

    @Test
    void dateFiltersIncludeWholeRomanianCalendarDaysAcrossBothDstChanges() throws Exception {
        TestUser owner = register();
        for (LocalDate day : List.of(LocalDate.of(2026, 3, 29), LocalDate.of(2026, 10, 25))) {
            Instant start = day.atStartOfDay(time.zone()).toInstant();
            Instant end = day.plusDays(1).atStartOfDay(time.zone()).toInstant();
            assertThat(Duration.between(start, end).toHours()).isEqualTo(day.getMonthValue() == 3 ? 23 : 25);
            order(owner, "FULFILLED", false, start.minusMillis(1), "Înainte");
            order(owner, "FULFILLED", false, start, "La început");
            order(owner, "FULFILLED", false, end.minusMillis(1), "La sfârșit");
            order(owner, "FULFILLED", false, end, "După");
            JsonNode result = list(owner, "from", day.toString(), "to", day.toString());
            assertThat(result.path("totalItems").asInt()).isEqualTo(2);
        }
    }

    @Test
    void stablePaginationAndBoundsHandleEqualTimestampsAndHugePageNumbers() throws Exception {
        TestUser owner = register();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) ids.add(order(owner, "FULFILLED", false, START, "Curs"));
        ids.sort(Comparator.reverseOrder());
        JsonNode first = list(owner, "size", "2");
        assertThat(first.path("totalPages").asInt()).isEqualTo(2);
        assertThat(first.at("/items/0/id").asText()).isEqualTo(ids.get(0));
        assertThat(first.at("/items/1/id").asText()).isEqualTo(ids.get(1));
        assertThat(list(owner, "size", "2", "page", "1").at("/items/0/id").asText()).isEqualTo(ids.get(2));
        assertThat(list(owner, "sort", "OLDEST").at("/items/0/id").asText()).isEqualTo(ids.get(2));
        JsonNode huge = list(owner, "page", "2147483647", "size", "2147483647");
        assertThat(huge.path("page").asInt()).isZero();
        assertThat(huge.path("size").asInt()).isEqualTo(50);
        assertThat(huge.path("items")).hasSize(3);
        assertThat(list(owner, "page", "2147483647", "size", "1").path("page").asInt()).isEqualTo(2);
        assertThat(list(owner, "page", "-1", "size", "0").path("size").asInt()).isEqualTo(1);
    }

    @Test
    void invalidFiltersAreRejectedAndRemovedProductsKeepTheirHistoricalNames() throws Exception {
        TestUser owner = register();
        String id = order(owner, "FULFILLED", false, START, "Premiu retras");
        jdbc.update("UPDATE purchase_records SET reward_id = 'missing-product' WHERE order_id = ?", id);
        JsonNode result = list(owner, "q", "Premiu retras");
        assertThat(result.at("/items/0/items/0/available").asBoolean()).isFalse();
        for (String[] invalid : List.of(new String[]{"status", "INVALID"}, new String[]{"delivery", "INVALID"},
                new String[]{"sort", "id DESC; DROP TABLE orders"}, new String[]{"q", "x".repeat(81)},
                new String[]{"from", "2026-02-30"}, new String[]{"to", "0000-01-01"}, new String[]{"to", "9999-12-31"})) {
            mvc.perform(getAs("/api/orders", owner).param(invalid[0], invalid[1])).andExpect(status().isBadRequest());
        }
        mvc.perform(getAs("/api/orders", owner).param("from", "2026-11-10").param("to", "2026-11-09"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historyReadsAreBoundedPerAccountWithoutChangingFinancialOrQuestData() throws Exception {
        TestUser owner = register();
        TestUser other = register();
        order(owner, "FULFILLED", false, START, "Curs");
        int balance = points(owner);
        int ledger = jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE user_id = ?", Integer.class, owner.id());
        for (int i = 0; i < 60; i++) mvc.perform(getAs("/api/orders", owner)).andExpect(status().isOk());
        mvc.perform(getAs("/api/orders", owner)).andExpect(status().isTooManyRequests());
        mvc.perform(getAs("/api/orders", other)).andExpect(status().isOk());
        assertThat(points(owner)).isEqualTo(balance);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE user_id = ?", Integer.class, owner.id())).isEqualTo(ledger);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_quests WHERE user_id = ?", Integer.class, owner.id())).isZero();
    }
}
