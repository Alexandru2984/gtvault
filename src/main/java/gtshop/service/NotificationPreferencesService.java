package gtshop.service;

import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Preferințe per cont pentru notificările opționale din aplicație. */
@Service
public class NotificationPreferencesService {
    private final JdbcTemplate jdbc;
    private final TimeService time;

    public NotificationPreferencesService(JdbcTemplate jdbc, TimeService time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    public record Preferences(@NotNull Boolean wishlistSales, @NotNull Boolean wishlistStock,
                              @NotNull Boolean progress, @NotNull Boolean community, @NotNull Boolean announcements) {}

    @Transactional(readOnly = true)
    public Preferences get(String userId) {
        var rows = jdbc.query("SELECT * FROM notification_preferences WHERE user_id = ?",
                (rs, row) -> new Preferences(rs.getBoolean("wishlist_sales"), rs.getBoolean("wishlist_stock"),
                        rs.getBoolean("progress"), rs.getBoolean("community"), rs.getBoolean("announcements")), userId);
        return rows.isEmpty() ? new Preferences(true, true, true, true, true) : rows.getFirst();
    }

    @Transactional
    public Preferences save(String userId, Preferences p) {
        jdbc.update("""
                INSERT INTO notification_preferences
                    (user_id, wishlist_sales, wishlist_stock, progress, community, announcements, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET wishlist_sales = excluded.wishlist_sales,
                    wishlist_stock = excluded.wishlist_stock, progress = excluded.progress,
                    community = excluded.community, announcements = excluded.announcements, updated_at = excluded.updated_at
                """, userId, p.wishlistSales(), p.wishlistStock(), p.progress(), p.community(), p.announcements(), Timestamp.from(time.now()));
        return p;
    }

    @Transactional(readOnly = true)
    public boolean allows(String userId, String type) {
        return switch (type) {
            case "BADGE", "QUEST", "STREAK" -> get(userId).progress();
            case "REVIEW", "PROPOSAL" -> get(userId).community();
            case "ANNOUNCEMENT" -> get(userId).announcements();
            // Comenzile, cadourile, soldurile și moderarea rămân disponibile indiferent de preferințe.
            default -> true;
        };
    }
}
