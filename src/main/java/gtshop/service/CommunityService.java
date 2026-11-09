package gtshop.service;

import gtshop.game.Badge;
import gtshop.util.AvatarCatalog;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Feed-ul public al comunitatii si statisticile de pe prima pagina.
 *
 * <p>Confidentialitate: comenzile apar anonim („Un membru a revendicat …”), iar membrii cu profil privat
 * apar fara nume si fara link catre profil.</p>
 */
@Service
public class CommunityService {
    private static final int PER_SOURCE = 12;
    private static final String ANONYMOUS = "Un membru";
    /** Lista de tipuri XP este o constanta din cod (nu vine de la utilizator), deci poate fi inclusa in SQL. */
    private static final String XP_IN = String.join(", ", gtshop.model.TxType.XP_TYPES.stream().map(t -> "'" + t + "'").toList());

    private final JdbcTemplate jdbc;
    private final TimeService time;

    public CommunityService(JdbcTemplate jdbc, TimeService time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public record Actor(String id, String name, String avatar) {}

    public record FeedItem(String type, String icon, String title, String detail, String link, Instant at, Actor actor) {}

    @Transactional(readOnly = true)
    public List<FeedItem> activity(int limit) {
        int n = Math.min(30, Math.max(1, limit));
        List<FeedItem> feed = new ArrayList<>();

        jdbc.query("""
                SELECT r.rating, r.comment, r.created_at, r.reward_id, w.name AS reward_name,
                       u.id, u.name, u.avatar, u.public_profile
                FROM product_reviews r JOIN users u ON u.id = r.user_id JOIN rewards w ON w.id = r.reward_id
                WHERE NOT r.hidden AND NOT u.banned AND w.active
                ORDER BY r.created_at DESC LIMIT ?""", rs -> {
            Actor a = actor(rs);
            feed.add(new FeedItem("REVIEW", "⭐", a.name() + " a dat " + rs.getInt("rating") + "★ pentru „" + rs.getString("reward_name") + "”",
                    excerpt(rs.getString("comment"), 90), "#/produs/" + rs.getString("reward_id"),
                    rs.getTimestamp("created_at").toInstant(), a));
        }, PER_SOURCE);

        jdbc.query("""
                SELECT b.badge_code, b.unlocked_at, u.id, u.name, u.avatar, u.public_profile
                FROM user_badges b JOIN users u ON u.id = b.user_id
                WHERE NOT u.banned
                ORDER BY b.unlocked_at DESC NULLS LAST LIMIT ?""", rs -> {
            Badge badge = Badge.byCode(rs.getString("badge_code"));
            Timestamp at = rs.getTimestamp("unlocked_at");
            if (badge == null || at == null) {
                return;
            }
            Actor a = actor(rs);
            feed.add(new FeedItem("BADGE", badge.icon(), a.name() + " a deblocat insigna „" + badge.title() + "”",
                    badge.description(), a.id() == null ? null : "#/membru/" + a.id(), at.toInstant(), a));
        }, PER_SOURCE);

        jdbc.query("""
                SELECT p.id AS proposal_id, p.title, p.created_at, u.id, u.name, u.avatar, u.public_profile
                FROM reward_proposals p JOIN users u ON u.id = p.user_id
                WHERE NOT u.banned AND p.status <> 'REJECTED'
                ORDER BY p.created_at DESC LIMIT ?""", rs -> {
            Actor a = actor(rs);
            feed.add(new FeedItem("PROPOSAL", "💡", a.name() + " a propus o recompensă nouă",
                    excerpt(rs.getString("title"), 90), "#/comunitate", rs.getTimestamp("created_at").toInstant(), a));
        }, PER_SOURCE);

        jdbc.query("""
                SELECT p.reward_id, p.reward_name, o.created_at
                FROM orders o JOIN purchase_records p ON p.order_id = o.id
                JOIN rewards w ON w.id = p.reward_id
                WHERE o.status <> 'CANCELLED' AND w.active AND o.created_at > ?
                ORDER BY o.created_at DESC LIMIT ?""", rs -> {
            feed.add(new FeedItem("ORDER", "🛍️", ANONYMOUS + " a revendicat „" + rs.getString("reward_name") + "”",
                    null, "#/produs/" + rs.getString("reward_id"), rs.getTimestamp("created_at").toInstant(), null));
        }, Timestamp.from(time.now().minus(Duration.ofDays(30))), PER_SOURCE);

        feed.sort(Comparator.comparing(FeedItem::at).reversed());
        return feed.size() > n ? List.copyOf(feed.subList(0, n)) : feed;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        Instant weekAgo = time.now().minus(Duration.ofDays(7));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("members", count("SELECT count(*) FROM users WHERE NOT banned"));
        r.put("activeToday", count("SELECT count(*) FROM users WHERE NOT banned AND last_daily_claim_date = ?", time.todayIso()));
        r.put("ordersThisWeek", count("SELECT count(*) FROM orders WHERE status <> 'CANCELLED' AND created_at > ?",
                Timestamp.from(weekAgo)));
        r.put("reviews", count("SELECT count(*) FROM product_reviews WHERE NOT hidden"));
        Double avg = jdbc.queryForObject("SELECT avg(rating) FROM product_reviews WHERE NOT hidden", Double.class);
        r.put("averageRating", avg == null ? null : CatalogStats.round1(avg));
        r.put("xpEarnedThisWeek", count("SELECT coalesce(sum(amount), 0) FROM point_transactions WHERE amount > 0 AND created_at > ? AND type IN ("
                + XP_IN + ")", Timestamp.from(weekAgo)));
        r.put("wordleSolversToday", count("SELECT count(*) FROM wordle_games WHERE game_date = ? AND status = 'WON'",
                time.todayIso()));
        return r;
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0 : v;
    }

    private static Actor actor(ResultSet rs) throws SQLException {
        if (!rs.getBoolean("public_profile")) {
            return new Actor(null, ANONYMOUS, AvatarCatalog.DEFAULT);
        }
        return new Actor(rs.getString("id"), rs.getString("name"), AvatarCatalog.safe(rs.getString("avatar")));
    }

    private static String excerpt(String s, int max) {
        if (s == null) {
            return null;
        }
        String t = s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1).trim() + "…";
    }
}
