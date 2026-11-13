package gtshop.service.admin;

import gtshop.security.ClientIpResolver;
import gtshop.security.CurrentUser;
import gtshop.service.TimeService;
import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Jurnalul actiunilor administrative: cine, ce, asupra carui obiect, de pe ce IP si cand. */
@Component
public class AdminAudit {
    private final JdbcTemplate jdbc;
    private final CurrentUser currentUser;
    private final ClientIpResolver ips;
    private final TimeService time;

    public AdminAudit(JdbcTemplate jdbc, CurrentUser currentUser, ClientIpResolver ips, TimeService time) {
        this.jdbc = jdbc;
        this.currentUser = currentUser;
        this.ips = ips;
        this.time = time;
    }

    /** Același ordin ca la checkout: actorul înaintea produselor/codurilor. FK-ul jurnalului nu inversează blocările. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockActor() {
        jdbc.queryForList("SELECT id FROM users WHERE id = ? FOR NO KEY UPDATE", currentUser.requireId());
    }

    /** Se scrie in aceeasi tranzactie cu modificarea: daca modificarea esueaza, nici intrarea din jurnal nu ramane. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String action, String targetType, String targetId, String details) {
        String ip = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            ip = ips.resolve(request);
        }
        String d = details == null ? null : details.length() <= 1000 ? details : details.substring(0, 999) + "…";
        jdbc.update("""
                INSERT INTO admin_audit_log (admin_id, action, target_type, target_id, details, ip, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                currentUser.requireId(), action, targetType, targetId, d, ip, Timestamp.from(time.now()));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> list(int page, int size, String targetType, String targetId) {
        int n = Math.min(100, Math.max(1, size));
        int p = Math.max(0, page);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT a.id, a.admin_id, u.name AS admin_name, a.action, a.target_type, a.target_id, a.details, a.ip, a.created_at
                FROM admin_audit_log a LEFT JOIN users u ON u.id = a.admin_id
                WHERE (CAST(? AS varchar) IS NULL OR a.target_type = ?) AND (CAST(? AS varchar) IS NULL OR a.target_id = ?)
                ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?""",
                targetType, targetType, targetId, targetId, n + 1, p * n);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", rows.size() > n ? rows.subList(0, n) : rows);
        res.put("page", p);
        res.put("hasMore", rows.size() > n);
        return res;
    }
}
