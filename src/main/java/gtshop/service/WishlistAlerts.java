package gtshop.service;

import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notificari pentru cei care au un produs in wishlist: cand intra intr-o oferta flash sau revine in stoc.
 * Inserarea se face intr-o singura instructiune (fara a incarca lista de utilizatori in memorie).
 */
@Component
public class WishlistAlerts {
    private static final String INSERT = """
            INSERT INTO notifications (id, user_id, type, title, body, icon, link, is_read, created_at)
            SELECT 'ntf-' || replace(gen_random_uuid()::text, '-', ''), w.user_id, 'WISHLIST', ?, ?, ?, ?, false, ?
            FROM wishlist_items w JOIN users u ON u.id = w.user_id
            WHERE w.reward_id = ? AND NOT u.banned""";

    private final JdbcTemplate jdbc;
    private final TimeService time;

    public WishlistAlerts(JdbcTemplate jdbc, TimeService time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int dealStarted(String rewardId, String rewardName, int discountPercent) {
        return jdbc.update(INSERT, truncate("⚡ -" + discountPercent + "% la " + rewardName, 140),
                "Un produs din wishlist-ul tău e în oferta zilei. Stoc limitat!", "⚡",
                "#/produs/" + rewardId, Timestamp.from(time.now()), rewardId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int backInStock(String rewardId, String rewardName) {
        return jdbc.update(INSERT, truncate("📦 " + rewardName + " e din nou în stoc", 140),
                "Produsul din wishlist-ul tău poate fi comandat din nou.", "📦",
                "#/produs/" + rewardId, Timestamp.from(time.now()), rewardId);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
