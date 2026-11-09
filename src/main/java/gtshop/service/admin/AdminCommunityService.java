package gtshop.service.admin;

import gtshop.model.RewardProposal;
import gtshop.repository.RewardProposalRepository;
import gtshop.service.NotificationService;
import gtshop.service.ProposalService;
import gtshop.service.TimeService;
import gtshop.util.InputSanitizer;
import gtshop.web.ApiException;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Moderarea comunitatii: propuneri, recenzii raportate si anunturi catre toti membrii. */
@Service
public class AdminCommunityService {
    private final RewardProposalRepository proposals;
    private final NotificationService notifications;
    private final AdminAudit audit;
    private final JdbcTemplate jdbc;
    private final TimeService time;

    public AdminCommunityService(RewardProposalRepository proposals, NotificationService notifications, AdminAudit audit,
                                 JdbcTemplate jdbc, TimeService time) {
        this.proposals = proposals;
        this.notifications = notifications;
        this.audit = audit;
        this.jdbc = jdbc;
        this.time = time;
    }

    // ------------------------------------------------------------------ propuneri

    @Transactional(readOnly = true)
    public List<Map<String, Object>> proposals(String status) {
        String st = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (st != null && !ProposalService.STATUSES.contains(st)) {
            throw ApiException.badRequest("Status invalid.");
        }
        return jdbc.queryForList("""
                SELECT p.id, p.title, p.category, p.description, p.status, p.votes_count, p.admin_note, p.created_at,
                       p.reviewed_at, u.id AS user_id, u.name AS user_name
                FROM reward_proposals p JOIN users u ON u.id = p.user_id
                WHERE (CAST(? AS varchar) IS NULL OR p.status = ?)
                ORDER BY CASE p.status WHEN 'PENDING' THEN 0 ELSE 1 END, p.votes_count DESC, p.created_at DESC
                LIMIT 300""", st, st);
    }

    @Transactional
    public Map<String, Object> setProposalStatus(String id, String status, String note) {
        String st = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!ProposalService.STATUSES.contains(st)) {
            throw ApiException.badRequest("Status invalid. Valori permise: " + String.join(", ", ProposalService.STATUSES) + ".");
        }
        String adminNote = InputSanitizer.cleanOptional(note, 255, "Nota");
        RewardProposal p = proposals.findByIdForUpdate(id)
                .orElseThrow(() -> ApiException.notFound("Propunerea nu a fost găsită."));
        String previous = p.getStatus();
        p.setStatus(st);
        p.setAdminNote(adminNote);
        p.setReviewedAt(time.now());
        proposals.save(p);
        if (!st.equals(previous) && p.getUser() != null) {
            String title = switch (st) {
                case "APPROVED" -> "Propunerea ta a fost aprobată 🎉";
                case "IMPLEMENTED" -> "Propunerea ta a devenit realitate 🚀";
                case "REJECTED" -> "Propunerea ta nu a fost aprobată";
                default -> "Propunerea ta este din nou în analiză";
            };
            notifications.notify(p.getUser().getId(), "PROPOSAL", title,
                    "„" + p.getTitle() + "”" + (adminNote != null ? " – " + adminNote : ""), "💡", "#/comunitate");
        }
        audit.record("PROPOSAL_STATUS", "PROPOSAL", p.getId(), previous + " -> " + st + (adminNote != null ? ": " + adminNote : ""));
        return Map.of("success", true, "id", p.getId(), "status", st, "message", "Statusul propunerii a fost actualizat.");
    }

    // ------------------------------------------------------------------ recenzii raportate

    @Transactional(readOnly = true)
    public List<Map<String, Object>> reportedReviews(boolean includeHidden) {
        return jdbc.queryForList("""
                SELECT r.id, r.reward_id, w.name AS reward_name, r.rating, r.comment, r.hidden, r.created_at,
                       u.id AS user_id, u.name AS user_name,
                       count(rep.user_id) AS reports, string_agg(DISTINCT rep.reason, ',') AS reasons,
                       max(rep.created_at) AS last_report_at
                FROM product_reviews r
                JOIN review_reports rep ON rep.review_id = r.id
                JOIN rewards w ON w.id = r.reward_id
                JOIN users u ON u.id = r.user_id
                WHERE ? OR NOT r.hidden
                GROUP BY r.id, w.name, u.id, u.name
                ORDER BY count(rep.user_id) DESC, max(rep.created_at) DESC
                LIMIT 200""", includeHidden);
    }

    @Transactional
    public Map<String, Object> moderateReview(String reviewId, boolean hide, String reason) {
        String why = InputSanitizer.cleanOptional(reason, 200, "Motivul");
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.user_id, r.hidden, w.name AS reward_name, r.reward_id FROM product_reviews r
                JOIN rewards w ON w.id = r.reward_id WHERE r.id = ? FOR UPDATE OF r""", reviewId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Recenzia nu a fost găsită.");
        }
        Map<String, Object> row = rows.get(0);
        boolean wasHidden = (Boolean) row.get("hidden");
        jdbc.update("UPDATE product_reviews SET hidden = ? WHERE id = ?", hide, reviewId);
        int dismissed = 0;
        if (!hide) {
            // Recenzia a fost verificata si e in regula: rapoartele existente se inchid
            dismissed = jdbc.update("DELETE FROM review_reports WHERE review_id = ?", reviewId);
        }
        if (hide && !wasHidden) {
            notifications.notify((String) row.get("user_id"), "REVIEW", "Recenzia ta a fost ascunsă de un moderator",
                    "Recenzia pentru „" + row.get("reward_name") + "” nu respectă regulile comunității"
                            + (why != null ? ": " + why : "."), "🛡️", "#/produs/" + row.get("reward_id"));
        }
        audit.record(hide ? "REVIEW_HIDE" : "REVIEW_APPROVE", "REVIEW", reviewId,
                (why != null ? why : "") + (dismissed > 0 ? " (" + dismissed + " rapoarte închise)" : ""));
        return Map.of("success", true, "hidden", hide, "reportsDismissed", dismissed,
                "message", hide ? "Recenzia a fost ascunsă." : "Recenzia este vizibilă, iar rapoartele au fost închise.");
    }

    // ------------------------------------------------------------------ anunturi

    @Transactional
    public Map<String, Object> broadcast(String rawTitle, String rawBody, String rawLink) {
        String title = InputSanitizer.cleanText(rawTitle, 3, 140, "Titlul");
        String body = InputSanitizer.cleanOptional(rawBody, 500, "Mesajul");
        String link = rawLink == null || rawLink.isBlank() ? null : rawLink.trim();
        if (link != null && !link.matches("^#/[A-Za-z0-9/_-]{0,120}$")) {
            throw ApiException.badRequest("Link-ul trebuie să fie o pagină din site (ex: #/magazin).");
        }
        int sent = jdbc.update("""
                INSERT INTO notifications (id, user_id, type, title, body, icon, link, is_read, created_at)
                SELECT 'ntf-' || replace(gen_random_uuid()::text, '-', ''), id, 'ANNOUNCEMENT', ?, ?, '📣', ?, false, ?
                FROM users WHERE NOT banned""", title, body, link, Timestamp.from(time.now()));
        audit.record("BROADCAST", "NOTIFICATION", null, title + " → " + sent + " membri");
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("recipients", sent);
        r.put("message", "Anunț trimis către " + sent + " membri.");
        return r;
    }
}
