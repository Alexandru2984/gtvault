package gtshop.service;

import gtshop.dto.UserSummary;
import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.web.ApiException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drepturile GDPR: exportul tuturor datelor personale (art. 15/20) si stergerea contului (art. 17).
 * Stergerea elimina definitiv contul si, prin cheile straine ON DELETE CASCADE, toate datele asociate.
 */
@Service
public class PrivacyService {
    private static final Logger log = LoggerFactory.getLogger(PrivacyService.class);
    public static final String DELETE_CONFIRMATION = "STERGE";

    /**
     * Tabelele cu date personale si coloana care le leaga de utilizator. Numele sunt constante din cod,
     * nu provin din cerere.
     */
    private static final Map<String, String> TABLES = new LinkedHashMap<>();

    static {
        TABLES.put("point_transactions", "SELECT id, type, amount, balance_after, description, created_at FROM point_transactions WHERE user_id = ? ORDER BY created_at");
        TABLES.put("orders", "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at");
        TABLES.put("order_items", "SELECT id, order_id, reward_id, reward_name, quantity, unit_price, points_spent, status, voucher_code, purchase_date, redeemed_at FROM purchase_records WHERE user_id = ? ORDER BY purchase_date");
        TABLES.put("cart_items", "SELECT reward_id, quantity, added_at FROM cart_items WHERE user_id = ?");
        TABLES.put("wishlist", "SELECT reward_id, added_at FROM wishlist_items WHERE user_id = ? ORDER BY added_at");
        TABLES.put("saving_goal", "SELECT reward_id, selected_at, last_checked_at, notified_at FROM saving_goals WHERE user_id = ?");
        TABLES.put("quiz_attempts", "SELECT game_date, question_id, category, question, options::text AS options, selected_index, correct_index, explanation, reward_points, answered_at FROM quiz_attempts WHERE user_id = ? ORDER BY game_date");
        TABLES.put("sessions", "SELECT device, created_at, last_seen_at, expires_at FROM user_sessions WHERE user_id = ? ORDER BY created_at");
        TABLES.put("notification_preferences", "SELECT wishlist_sales, wishlist_stock, progress, community, announcements, updated_at FROM notification_preferences WHERE user_id = ?");
        TABLES.put("reviews", "SELECT id, reward_id, rating, comment, created_at, updated_at, helpful_count, hidden FROM product_reviews WHERE user_id = ? ORDER BY created_at");
        TABLES.put("review_votes", "SELECT review_id, created_at FROM review_votes WHERE user_id = ?");
        TABLES.put("review_reports", "SELECT review_id, reason, created_at FROM review_reports WHERE user_id = ?");
        TABLES.put("badges", "SELECT badge_code, badge_title, unlocked_at FROM user_badges WHERE user_id = ? ORDER BY unlocked_at");
        TABLES.put("quests", "SELECT quest_key, quest_type, title, reward_points, current_progress, target_progress, completed, claimed, quest_date FROM user_quests WHERE user_id = ? ORDER BY quest_date");
        TABLES.put("proposals", "SELECT id, title, category, description, status, votes_count, created_at FROM reward_proposals WHERE user_id = ? ORDER BY created_at");
        TABLES.put("proposal_votes", "SELECT * FROM proposal_votes WHERE user_id = ?");
        TABLES.put("promo_redemptions", "SELECT promo_id, order_id, discount, created_at FROM promo_redemptions WHERE user_id = ?");
        TABLES.put("wordle_games", "SELECT game_date, guesses, status, reward, updated_at, puzzle_date FROM wordle_games WHERE user_id = ? ORDER BY game_date");
        TABLES.put("notifications", "SELECT type, title, body, is_read, created_at FROM notifications WHERE user_id = ? ORDER BY created_at");
    }

    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final WalletService wallet;
    private final PasswordEncoder passwordEncoder;
    private final TimeService time;

    @PersistenceContext
    private EntityManager entityManager;

    public PrivacyService(UserRepository users, JdbcTemplate jdbc, WalletService wallet, PasswordEncoder passwordEncoder,
                          TimeService time) {
        this.users = users;
        this.jdbc = jdbc;
        this.wallet = wallet;
        this.passwordEncoder = passwordEncoder;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(String userId) {
        UserProfile u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("exportedAt", time.now());
        out.put("format", "rewardhub-export-v1");
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("account", UserSummary.of(u));
        profile.put("publicProfile", u.isPublicProfile());
        profile.put("bio", u.getBio());
        profile.put("referredBy", u.getReferredBy());
        profile.put("lastDailyClaimDate", u.getLastDailyClaimDate());
        profile.put("guildChangedAt", u.getGuildChangedAt());
        out.put("profile", profile);
        Integer referrals = u.getReferralCode() == null ? 0
                : jdbc.queryForObject("SELECT count(*) FROM users WHERE referred_by = ?", Integer.class, u.getReferralCode());
        out.put("referralsCount", referrals);
        for (Map.Entry<String, String> t : TABLES.entrySet()) {
            List<Map<String, Object>> rows = jdbc.queryForList(t.getValue(), userId);
            rows.forEach(row -> row.remove("user_id"));
            out.put(t.getKey(), rows);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> deleteAccount(String userId, String password, String confirmation) {
        UserProfile u = wallet.lock(userId);
        if (confirmation == null || !DELETE_CONFIRMATION.equalsIgnoreCase(confirmation.trim())) {
            throw ApiException.badRequest("CONFIRMATION_REQUIRED", "Scrie „" + DELETE_CONFIRMATION + "” pentru a confirma ștergerea contului.");
        }
        if (password == null || u.getPassword() == null || !passwordEncoder.matches(password, u.getPassword())) {
            throw ApiException.badRequest("WRONG_PASSWORD", "Parola nu este corectă.");
        }
        if (UserProfile.ROLE_ADMIN.equals(u.getRole())) {
            throw new ApiException(HttpStatus.CONFLICT, "ADMIN_ACCOUNT",
                    "Contul de administrator nu poate fi șters din aplicație.");
        }
        Integer inProgress = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ? AND status IN ('PLACED', 'PROCESSING', 'SHIPPED')", Integer.class, userId);
        if (inProgress != null && inProgress > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDERS_IN_PROGRESS",
                    "Ai comenzi în curs de livrare. Poți șterge contul după ce sunt livrate sau anulate.");
        }
        entityManager.flush();
        entityManager.detach(u);
        int deleted = jdbc.update("DELETE FROM users WHERE id = ?", userId);
        log.info("Cont sters la cererea utilizatorului (randuri: {})", deleted);
        return Map.of("success", true, "message", "Contul și toate datele tale au fost șterse definitiv. Ne pare rău că pleci!");
    }
}
