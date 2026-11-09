package gtshop.service.admin;

import gtshop.model.TxType;
import gtshop.service.TimeService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Indicatorii principali pentru panoul de administrare. */
@Service
public class AdminDashboardService {
    private static final int LOW_STOCK = 10;
    private static final int CHART_DAYS = 14;
    /** Constante din cod, nu din cerere. */
    private static final String XP_IN = String.join(", ", TxType.XP_TYPES.stream().map(t -> "'" + t + "'").toList());

    private final JdbcTemplate jdbc;
    private final TimeService time;

    public AdminDashboardService(JdbcTemplate jdbc, TimeService time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Instant now = time.now();
        Timestamp week = Timestamp.from(now.minus(Duration.ofDays(7)));
        Timestamp month = Timestamp.from(now.minus(Duration.ofDays(30)));
        String tz = time.zone().getId();

        Map<String, Object> users = new LinkedHashMap<>();
        users.put("total", count("SELECT count(*) FROM users"));
        users.put("banned", count("SELECT count(*) FROM users WHERE banned"));
        users.put("newLast7Days", count("SELECT count(*) FROM users WHERE created_at > ?", week));
        users.put("activeToday", count("SELECT count(*) FROM users WHERE last_daily_claim_date = ?", time.todayIso()));
        users.put("publicProfiles", count("SELECT count(*) FROM users WHERE public_profile AND NOT banned"));

        Map<String, Object> orders = new LinkedHashMap<>();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        jdbc.query("SELECT status, count(*) FROM orders GROUP BY status ORDER BY status",
                rs -> {
                    byStatus.put(rs.getString(1), rs.getLong(2));
                });
        orders.put("byStatus", byStatus);
        orders.put("toFulfill", count("SELECT count(*) FROM orders WHERE requires_shipping AND status IN ('PLACED', 'PROCESSING')"));
        orders.put("last7Days", count("SELECT count(*) FROM orders WHERE status <> 'CANCELLED' AND created_at > ?", week));
        orders.put("revenueLast7Days", count("SELECT coalesce(sum(total), 0) FROM orders WHERE status <> 'CANCELLED' AND created_at > ?", week));
        orders.put("revenueLast30Days", count("SELECT coalesce(sum(total), 0) FROM orders WHERE status <> 'CANCELLED' AND created_at > ?", month));
        orders.put("vouchersRedeemed", count("SELECT count(*) FROM purchase_records WHERE redeemed_at IS NOT NULL"));

        Map<String, Object> economy = new LinkedHashMap<>();
        economy.put("pointsInCirculation", count("SELECT coalesce(sum(activity_points), 0) FROM users WHERE NOT banned"));
        economy.put("xpEarnedLast7Days", count("SELECT coalesce(sum(amount), 0) FROM point_transactions WHERE amount > 0 AND created_at > ? AND type IN (" + XP_IN + ")", week));
        economy.put("pointsSpentLast7Days", count("SELECT coalesce(-sum(amount), 0) FROM point_transactions WHERE amount < 0 AND created_at > ?", week));
        economy.put("adminAdjustmentsLast30Days", count("SELECT count(*) FROM point_transactions WHERE type = 'ADMIN_ADJUSTMENT' AND created_at > ?", month));

        Map<String, Object> catalog = new LinkedHashMap<>();
        catalog.put("activeProducts", count("SELECT count(*) FROM rewards WHERE active"));
        catalog.put("outOfStock", count("SELECT count(*) FROM rewards WHERE active AND stock_count = 0"));
        catalog.put("lowStock", jdbc.queryForList(
                "SELECT id, name, stock_count FROM rewards WHERE active AND stock_count <= ? ORDER BY stock_count, name LIMIT 20", LOW_STOCK));
        catalog.put("activeDeals", count("SELECT count(*) FROM flash_deals WHERE starts_at <= ? AND ends_at > ?", Timestamp.from(now), Timestamp.from(now)));
        catalog.put("topProductsLast30Days", jdbc.queryForList("""
                SELECT p.reward_id, max(p.reward_name) AS name, sum(p.quantity) AS units, sum(p.points_spent) AS points
                FROM purchase_records p JOIN orders o ON o.id = p.order_id
                WHERE o.status <> 'CANCELLED' AND o.created_at > ?
                GROUP BY p.reward_id ORDER BY units DESC LIMIT 5""", month));

        Map<String, Object> community = new LinkedHashMap<>();
        community.put("pendingProposals", count("SELECT count(*) FROM reward_proposals WHERE status = 'PENDING'"));
        community.put("reportedReviews", count("""
                SELECT count(DISTINCT r.id) FROM product_reviews r JOIN review_reports rep ON rep.review_id = r.id WHERE NOT r.hidden"""));
        community.put("reviewsLast7Days", count("SELECT count(*) FROM product_reviews WHERE created_at > ?", week));
        community.put("wordlePlayersToday", count("SELECT count(*) FROM wordle_games WHERE game_date = ?", time.todayIso()));

        LocalDate today = time.today();
        LocalDate from = today.minusDays(CHART_DAYS - 1L);
        Timestamp fromTs = Timestamp.from(from.atStartOfDay(time.zone()).toInstant());
        Map<String, Object> charts = new LinkedHashMap<>();
        charts.put("signups", daily("SELECT (created_at AT TIME ZONE ?)::date AS d, count(*) FROM users WHERE created_at >= ? GROUP BY d",
                from, tz, fromTs));
        charts.put("orders", daily("SELECT (created_at AT TIME ZONE ?)::date AS d, count(*) FROM orders WHERE status <> 'CANCELLED' AND created_at >= ? GROUP BY d",
                from, tz, fromTs));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("generatedAt", now);
        res.put("users", users);
        res.put("orders", orders);
        res.put("economy", economy);
        res.put("catalog", catalog);
        res.put("community", community);
        res.put("charts", charts);
        return res;
    }

    /** Serie zilnica completa (zilele fara activitate apar cu 0). */
    private List<Map<String, Object>> daily(String sql, LocalDate from, Object... args) {
        Map<LocalDate, Long> values = new LinkedHashMap<>();
        jdbc.query(sql, rs -> {
            values.put(rs.getDate(1).toLocalDate(), rs.getLong(2));
        }, args);
        return from.datesUntil(time.today().plusDays(1))
                .map(d -> Map.<String, Object>of("date", d.toString(), "count", values.getOrDefault(d, 0L)))
                .toList();
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }
}
