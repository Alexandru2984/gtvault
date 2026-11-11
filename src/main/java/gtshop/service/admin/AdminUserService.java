package gtshop.service.admin;

import gtshop.dto.UserSummary;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.security.CurrentUser;
import gtshop.service.NotificationService;
import gtshop.service.WalletService;
import gtshop.util.InputSanitizer;
import gtshop.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrarea conturilor: cautare, detalii, corectarea soldului si suspendarea.
 * Rolul de administrator este gestionat explicit de operator in baza de date.
 */
@Service
public class AdminUserService {
    private static final int MAX_ADJUSTMENT = 1_000_000;

    private final UserRepository users;
    private final WalletService wallet;
    private final NotificationService notifications;
    private final AdminAudit audit;
    private final CurrentUser currentUser;
    private final JdbcTemplate jdbc;

    public AdminUserService(UserRepository users, WalletService wallet, NotificationService notifications, AdminAudit audit,
                            CurrentUser currentUser, JdbcTemplate jdbc) {
        this.users = users;
        this.wallet = wallet;
        this.notifications = notifications;
        this.audit = audit;
        this.currentUser = currentUser;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> search(String q, int page, int size) {
        String like = q == null || q.isBlank() ? null : "%" + AdminOrderService.escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        int n = Math.min(100, Math.max(1, size));
        int p = Math.max(0, page);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT u.id, u.name, u.email, u.role, u.banned, u.activity_points, u.total_points_earned, u.streak_days,
                       u.created_at, u.guild_id,
                       (SELECT count(*) FROM orders o WHERE o.user_id = u.id AND o.status <> 'CANCELLED') AS orders
                FROM users u
                WHERE CAST(? AS varchar) IS NULL OR lower(u.id) LIKE ? ESCAPE '\\' OR lower(coalesce(u.email, '')) LIKE ? ESCAPE '\\'
                      OR lower(coalesce(u.name, '')) LIKE ? ESCAPE '\\' OR lower(coalesce(u.referral_code, '')) LIKE ? ESCAPE '\\'
                ORDER BY u.created_at DESC, u.id LIMIT ? OFFSET ?""",
                like, like, like, like, like, n + 1, p * n);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", rows.size() > n ? rows.subList(0, n) : rows);
        res.put("page", p);
        res.put("hasMore", rows.size() > n);
        return res;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(String id) {
        UserProfile u = users.findById(id).orElseThrow(() -> ApiException.notFound("Utilizatorul nu a fost găsit."));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("user", UserSummary.of(u));
        res.put("banned", u.isBanned());
        res.put("referredBy", u.getReferredBy());
        res.put("referrals", u.getReferralCode() == null ? 0
                : jdbc.queryForObject("SELECT count(*) FROM users WHERE referred_by = ?", Integer.class, u.getReferralCode()));
        res.put("badges", jdbc.queryForObject("SELECT count(*) FROM user_badges WHERE user_id = ?", Integer.class, id));
        res.put("reviews", jdbc.queryForObject("SELECT count(*) FROM product_reviews WHERE user_id = ?", Integer.class, id));
        res.put("recentTransactions", jdbc.queryForList(
                "SELECT id, type, amount, balance_after, description, created_at FROM point_transactions WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 25", id));
        res.put("recentOrders", jdbc.queryForList(
                "SELECT id, status, total, requires_shipping, created_at FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT 10", id));
        res.put("audit", jdbc.queryForList(
                "SELECT action, details, created_at FROM admin_audit_log WHERE target_type = 'USER' AND target_id = ? ORDER BY created_at DESC LIMIT 20", id));
        return res;
    }

    @Transactional
    public Map<String, Object> adjustPoints(String id, Integer amount, String reason) {
        if (amount == null || amount == 0 || amount < -MAX_ADJUSTMENT || amount > MAX_ADJUSTMENT) {
            throw ApiException.badRequest("Suma trebuie să fie nenulă și cel mult " + MAX_ADJUSTMENT + " AP în valoare absolută.");
        }
        String why = InputSanitizer.cleanText(reason, 3, 200, "Motivul");
        UserProfile u = wallet.lockForAdmin(id);
        if (amount > 0) {
            wallet.credit(u, amount, TxType.ADMIN_ADJUSTMENT, "Ajustare: " + why);
        } else {
            wallet.debit(u, -amount, TxType.ADMIN_ADJUSTMENT, "Ajustare: " + why);
        }
        notifications.notify(u.getId(), "ACCOUNT", "Soldul tău a fost ajustat: " + (amount > 0 ? "+" : "") + amount + " AP",
                why, "🛠️", "#/profil");
        audit.record("POINTS_ADJUST", "USER", u.getId(), (amount > 0 ? "+" : "") + amount + " AP: " + why
                + " (sold nou " + u.getActivityPoints() + ")");
        return Map.of("success", true, "newBalance", u.getActivityPoints(), "message", "Sold actualizat.");
    }

    @Transactional
    public Map<String, Object> setBanned(String id, Boolean banned, String reason) {
        if (banned == null) {
            throw ApiException.badRequest("Specifică dacă blochezi sau deblochezi contul.");
        }
        if (id.equals(currentUser.requireId())) {
            throw ApiException.conflict("SELF_ACTION", "Nu îți poți suspenda propriul cont.");
        }
        String why = banned ? InputSanitizer.cleanText(reason, 3, 200, "Motivul") : InputSanitizer.cleanOptional(reason, 200, "Motivul");
        UserProfile u = wallet.lockForAdmin(id);
        if (u.isAdmin()) {
            throw new ApiException(HttpStatus.CONFLICT, "ADMIN_ACCOUNT", "Conturile de administrator nu pot fi suspendate din aplicație.");
        }
        if (u.isBanned() == banned) {
            return Map.of("success", true, "banned", banned, "message", banned ? "Contul era deja suspendat." : "Contul era deja activ.");
        }
        u.setBanned(banned);
        // Orice sesiune existenta devine invalida imediat
        u.setTokenVersion(u.getTokenVersion() + 1);
        users.save(u);
        if (!banned) {
            notifications.notify(u.getId(), "ACCOUNT", "Contul tău a fost reactivat",
                    why != null ? why : "Bine ai revenit!", "✅", "#/profil");
        }
        audit.record(banned ? "USER_BAN" : "USER_UNBAN", "USER", u.getId(), why);
        return Map.of("success", true, "banned", banned,
                "message", banned ? "Contul a fost suspendat și deconectat de pe toate dispozitivele." : "Contul a fost reactivat.");
    }
}
