package gtshop.service;

import gtshop.web.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Rezumate private, cu filtre parametrizate și încărcarea liniilor în lot. */
@Service
public class OrderHistoryService {
    private final NamedParameterJdbcTemplate jdbc;
    private final TimeService time;

    public OrderHistoryService(NamedParameterJdbcTemplate jdbc, TimeService time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public record Filters(String q, String status, String delivery, String from, String to, String sort) {}
    public record Item(String rewardId, String name, int quantity, boolean available) {}
    public record Summary(String id, String status, String statusLabel, Instant createdAt, int total,
                          boolean requiresShipping, long itemCount, List<Item> items) {}
    public record HistoryPage(List<Summary> items, int page, int size, long totalItems, long totalPages, Filters filters) {}
    private record Header(String id, String status, Instant createdAt, int total, boolean requiresShipping) {}

    @Transactional(readOnly = true)
    public HistoryPage list(String userId, int page, int size, String q, String status, String delivery,
                            String from, String to, String sort) {
        String search = q == null ? "" : q.trim();
        if (search.length() > 80) throw ApiException.badRequest("Căutarea poate avea cel mult 80 de caractere.");
        String state = choice(status, "ALL");
        if (!state.equals("ALL") && !OrderService.STATUS_LABELS.containsKey(state)) {
            throw ApiException.badRequest("Statusul comenzii nu este valid.");
        }
        String shipping = choice(delivery, "ALL");
        if (!Set.of("ALL", "PHYSICAL", "DIGITAL").contains(shipping)) throw ApiException.badRequest("Tipul de livrare nu este valid.");
        String ordering = choice(sort, "NEWEST");
        if (!Set.of("NEWEST", "OLDEST").contains(ordering)) throw ApiException.badRequest("Sortarea nu este validă.");
        LocalDate start = day(from);
        LocalDate end = day(to);
        if (start != null && end != null && start.isAfter(end)) throw ApiException.badRequest("Data de început trebuie să fie înaintea datei de sfârșit.");
        Filters filters = new Filters(search, state, shipping, start == null ? "" : start.toString(), end == null ? "" : end.toString(), ordering);

        var params = new MapSqlParameterSource("userId", userId);
        var where = new StringBuilder(" WHERE o.user_id = :userId");
        if (!state.equals("ALL")) {
            where.append(" AND o.status = :status");
            params.addValue("status", state);
        }
        if (!shipping.equals("ALL")) {
            where.append(" AND o.requires_shipping = :shipping");
            params.addValue("shipping", shipping.equals("PHYSICAL"));
        }
        if (start != null) {
            where.append(" AND o.created_at >= :start");
            params.addValue("start", Timestamp.from(start.atStartOfDay(time.zone()).toInstant()));
        }
        if (end != null) {
            where.append(" AND o.created_at < :end");
            params.addValue("end", Timestamp.from(end.plusDays(1).atStartOfDay(time.zone()).toInstant()));
        }
        if (!search.isEmpty()) {
            where.append("""
                     AND (lower(o.id) LIKE :search ESCAPE '!' OR EXISTS (
                         SELECT 1 FROM purchase_records p WHERE p.order_id = o.id AND p.user_id = :userId
                         AND lower(p.reward_name) LIKE :search ESCAPE '!'))
                    """);
            params.addValue("search", "%" + search.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        int s = Math.max(1, Math.min(50, size));
        long total = jdbc.queryForObject("SELECT count(*) FROM orders o" + where, params, Long.class);
        long pages = (total + s - 1) / s;
        int p = (int) Math.min(Math.max(0, page), Math.max(0, pages - 1));
        if (total == 0) return new HistoryPage(List.of(), p, s, total, pages, filters);
        params.addValue("limit", s).addValue("offset", (long) p * s);
        // Direcția este aleasă exclusiv din valori constante; nicio expresie SQL nu provine din cerere.
        String direction = ordering.equals("OLDEST") ? " ASC" : " DESC";
        List<Header> headers = jdbc.query("""
                SELECT o.id, o.status, o.created_at, o.total, o.requires_shipping FROM orders o
                """ + where + " ORDER BY o.created_at" + direction + ", o.id" + direction + " LIMIT :limit OFFSET :offset",
                params, (rs, row) -> new Header(rs.getString("id"), rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                        rs.getInt("total"), rs.getBoolean("requires_shipping")));
        if (headers.isEmpty()) return new HistoryPage(List.of(), p, s, total, pages, filters);
        params.addValue("ids", headers.stream().map(Header::id).toList());
        Map<String, List<Item>> items = new HashMap<>();
        jdbc.query("""
                SELECT p.order_id, p.reward_id, p.reward_name, p.quantity, COALESCE(r.active, false) AS available
                FROM purchase_records p LEFT JOIN rewards r ON r.id = p.reward_id
                WHERE p.user_id = :userId AND p.order_id IN (:ids) ORDER BY p.id
                """, params, (org.springframework.jdbc.core.RowCallbackHandler) rs -> items.computeIfAbsent(rs.getString("order_id"), ignored -> new ArrayList<>())
                .add(new Item(rs.getString("reward_id"), rs.getString("reward_name"), rs.getInt("quantity"), rs.getBoolean("available"))));
        List<Summary> summaries = headers.stream().map(header -> {
            List<Item> lines = List.copyOf(items.getOrDefault(header.id(), List.of()));
            return new Summary(header.id(), header.status(), OrderService.STATUS_LABELS.getOrDefault(header.status(), header.status()),
                    header.createdAt(), header.total(), header.requiresShipping(), lines.stream().mapToLong(Item::quantity).sum(), lines);
        }).toList();
        return new HistoryPage(summaries, p, s, total, pages, filters);
    }

    private static String choice(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private static LocalDate day(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String text = value.trim();
            if (text.length() != 10) throw new IllegalArgumentException();
            LocalDate result = LocalDate.parse(text);
            if (result.getYear() < 1 || result.getYear() > 9998) throw new IllegalArgumentException();
            return result;
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            throw ApiException.badRequest("Data trebuie să fie validă, în formatul AAAA-LL-ZZ.");
        }
    }
}
