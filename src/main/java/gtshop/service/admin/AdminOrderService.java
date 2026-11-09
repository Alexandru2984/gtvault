package gtshop.service.admin;

import gtshop.model.PurchaseRecord;
import gtshop.model.Reward;
import gtshop.model.ShopOrder;
import gtshop.model.UserProfile;
import gtshop.repository.ShopOrderRepository;
import gtshop.service.NotificationService;
import gtshop.service.OrderService;
import gtshop.service.TimeService;
import gtshop.service.WalletService;
import gtshop.util.InputSanitizer;
import gtshop.util.Locks;
import gtshop.web.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Procesarea comenzilor de catre administrator: cautare, schimbarea statusului, anulare, vouchere. */
@Service
public class AdminOrderService {
    /** Tranzitiile permise pentru comenzile cu livrare (anularea are regulile ei). */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
            ShopOrder.PLACED, Set.of(ShopOrder.PROCESSING, ShopOrder.SHIPPED),
            ShopOrder.PROCESSING, Set.of(ShopOrder.SHIPPED),
            ShopOrder.SHIPPED, Set.of(ShopOrder.DELIVERED));
    private static final Set<String> CANCELLABLE = Set.of(ShopOrder.PLACED, ShopOrder.PROCESSING, ShopOrder.SHIPPED, ShopOrder.FULFILLED);
    private static final Pattern TRACKING = Pattern.compile("^[A-Za-z0-9 ._/-]{3,64}$");

    private final ShopOrderRepository orders;
    private final OrderService orderService;
    private final WalletService wallet;
    private final NotificationService notifications;
    private final AdminAudit audit;
    private final JdbcTemplate jdbc;
    private final Locks locks;
    private final TimeService time;

    public AdminOrderService(ShopOrderRepository orders, OrderService orderService, WalletService wallet,
                             NotificationService notifications, AdminAudit audit, JdbcTemplate jdbc, Locks locks,
                             TimeService time) {
        this.orders = orders;
        this.orderService = orderService;
        this.wallet = wallet;
        this.notifications = notifications;
        this.audit = audit;
        this.jdbc = jdbc;
        this.locks = locks;
        this.time = time;
    }

    public record AdminOrderView(OrderService.OrderView order, Map<String, Object> customer, String adminNote,
                                 List<String> allowedTransitions, boolean canCancel) {}

    // ------------------------------------------------------------------ citire

    @Transactional(readOnly = true)
    public Map<String, Object> list(String status, String q, int page, int size) {
        if (status != null && !status.isBlank() && !OrderService.STATUS_LABELS.containsKey(status)) {
            throw ApiException.badRequest("Status invalid.");
        }
        String st = status == null || status.isBlank() ? null : status;
        String like = q == null || q.isBlank() ? null : "%" + escapeLike(q.trim().toLowerCase(Locale.ROOT)) + "%";
        int n = Math.min(100, Math.max(1, size));
        int p = Math.max(0, page);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT o.id, o.status, o.created_at, o.updated_at, o.total, o.requires_shipping, o.tracking_code,
                       o.shipping_name, o.shipping_city, o.shipping_county,
                       (SELECT coalesce(sum(p.quantity), 0) FROM purchase_records p WHERE p.order_id = o.id) AS item_count,
                       u.id AS user_id, u.name AS user_name, u.email AS user_email
                FROM orders o JOIN users u ON u.id = o.user_id
                WHERE (CAST(? AS varchar) IS NULL OR o.status = ?)
                  AND (CAST(? AS varchar) IS NULL OR lower(o.id) LIKE ? ESCAPE '\\' OR lower(u.email) LIKE ? ESCAPE '\\'
                       OR lower(u.name) LIKE ? ESCAPE '\\' OR lower(coalesce(o.shipping_name, '')) LIKE ? ESCAPE '\\'
                       OR coalesce(o.shipping_phone, '') LIKE ? ESCAPE '\\')
                ORDER BY o.created_at DESC, o.id DESC LIMIT ? OFFSET ?""",
                st, st, like, like, like, like, like, like, n + 1, p * n);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", rows.size() > n ? rows.subList(0, n) : rows);
        res.put("page", p);
        res.put("hasMore", rows.size() > n);
        res.put("statuses", OrderService.STATUS_LABELS);
        return res;
    }

    @Transactional(readOnly = true)
    public AdminOrderView get(String id) {
        return view(orders.findById(id).orElseThrow(() -> ApiException.notFound("Comanda nu a fost găsită.")));
    }

    // ------------------------------------------------------------------ status

    @Transactional
    public AdminOrderView updateStatus(String id, String newStatus, String trackingCode, String note) {
        String target = newStatus == null ? "" : newStatus.trim().toUpperCase(Locale.ROOT);
        if (ShopOrder.CANCELLED.equals(target)) {
            return cancel(id, note);
        }
        ShopOrder order = orders.findByIdForUpdate(id).map(locks::refresh)
                .orElseThrow(() -> ApiException.notFound("Comanda nu a fost găsită."));
        if (!order.isRequiresShipping()) {
            throw ApiException.conflict("DIGITAL_ORDER", "Comenzile digitale sunt finalizate automat; pot fi doar anulate.");
        }
        Set<String> allowed = TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowed.contains(target)) {
            throw ApiException.conflict("INVALID_TRANSITION", "Comanda nu poate trece din „"
                    + OrderService.STATUS_LABELS.get(order.getStatus()) + "” în „"
                    + OrderService.STATUS_LABELS.getOrDefault(target, target) + "”.");
        }
        String tracking = null;
        if (trackingCode != null && !trackingCode.isBlank()) {
            tracking = trackingCode.trim();
            if (!TRACKING.matcher(tracking).matches()) {
                throw ApiException.badRequest("Codul AWB poate conține 3-64 de litere, cifre, spații și . _ / -");
            }
        }
        String adminNote = InputSanitizer.cleanOptional(note, 255, "Nota");
        Instant now = time.now();
        String previous = order.getStatus();
        order.setStatus(target);
        switch (target) {
            case ShopOrder.PROCESSING -> order.setProcessingAt(now);
            case ShopOrder.SHIPPED -> order.setShippedAt(now);
            case ShopOrder.DELIVERED -> order.setDeliveredAt(now);
            default -> { }
        }
        if (tracking != null) {
            order.setTrackingCode(tracking);
        }
        if (adminNote != null) {
            order.setAdminNote(adminNote);
        }
        order.setUpdatedAt(now);
        for (PurchaseRecord p : order.getItems()) {
            if (Reward.FULFILLMENT_PHYSICAL.equals(p.getFulfillment())) {
                p.setStatus(target);
            }
        }
        orders.save(order);
        String userId = order.getUser().getId();
        String link = "#/comenzi/" + order.getId();
        switch (target) {
            case ShopOrder.PROCESSING -> notifications.notify(userId, "ORDER", "Comanda " + order.getId() + " e în pregătire 📦",
                    "Pregătim produsele pentru expediere.", "📦", link);
            case ShopOrder.SHIPPED -> notifications.notify(userId, "ORDER", "Comanda " + order.getId() + " a fost expediată 🚚",
                    order.getTrackingCode() != null ? "AWB: " + order.getTrackingCode() : "Coletul este pe drum.", "🚚", link);
            case ShopOrder.DELIVERED -> notifications.notify(userId, "ORDER", "Comanda " + order.getId() + " a fost livrată ✅",
                    "Lasă o recenzie verificată și primești AP bonus!", "✅", link);
            default -> { }
        }
        audit.record("ORDER_STATUS", "ORDER", order.getId(), previous + " -> " + target
                + (tracking != null ? " (AWB " + tracking + ")" : "") + (adminNote != null ? ": " + adminNote : ""));
        return view(order);
    }

    @Transactional
    public AdminOrderView cancel(String id, String reason) {
        String why = InputSanitizer.cleanText(reason, 5, 200, "Motivul anulării");
        String ownerId = jdbc.queryForList("SELECT user_id FROM orders WHERE id = ?", String.class, id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Comanda nu a fost găsită."));
        // Ordinea blocarilor este aceeasi ca la anularea de catre client: intai contul, apoi comanda
        UserProfile owner = wallet.lockForAdmin(ownerId);
        ShopOrder order = orders.findByIdForUpdate(id).map(locks::refresh)
                .orElseThrow(() -> ApiException.notFound("Comanda nu a fost găsită."));
        String problem = cancelProblem(order);
        if (problem != null) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_CANCEL", problem);
        }
        String previous = order.getStatus();
        orderService.cancel(owner, order, "Anulată de administrator: " + why);
        audit.record("ORDER_CANCEL", "ORDER", order.getId(), previous + " -> CANCELLED, rambursat " + order.getTotal() + " AP: " + why);
        return view(order);
    }

    static String cancelProblem(ShopOrder order) {
        if (!CANCELLABLE.contains(order.getStatus())) {
            return "Comanda nu mai poate fi anulată (status: " + OrderService.STATUS_LABELS.getOrDefault(order.getStatus(), order.getStatus()) + ").";
        }
        if (order.getItems().stream().anyMatch(p -> p.getRedeemedAt() != null)) {
            return "Comanda conține vouchere deja folosite și nu mai poate fi anulată.";
        }
        if (order.getItems().stream().anyMatch(p -> Reward.FULFILLMENT_BADGE.equals(p.getFulfillment()))) {
            return "Comenzile cu insigne digitale nu pot fi anulate.";
        }
        return null;
    }

    // ------------------------------------------------------------------ vouchere

    @Transactional
    public Map<String, Object> redeemVoucher(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.length() < 8 || code.length() > 40) {
            throw ApiException.badRequest("Codul voucherului nu este valid.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.id, p.reward_name, p.status, p.redeemed_at, p.order_id FROM purchase_records p
                WHERE p.voucher_code = ? FOR UPDATE""", code);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Voucherul nu există.");
        }
        Map<String, Object> row = rows.get(0);
        if (ShopOrder.CANCELLED.equals(row.get("status"))) {
            throw ApiException.conflict("VOUCHER_CANCELLED", "Voucherul aparține unei comenzi anulate.");
        }
        if (row.get("redeemed_at") != null) {
            throw ApiException.conflict("VOUCHER_REDEEMED", "Voucherul a fost deja folosit la " + row.get("redeemed_at") + ".");
        }
        Instant now = time.now();
        jdbc.update("UPDATE purchase_records SET redeemed_at = ? WHERE id = ?", Timestamp.from(now), row.get("id"));
        audit.record("VOUCHER_REDEEM", "ORDER", (String) row.get("order_id"), "Voucher " + mask(code) + " (" + row.get("reward_name") + ")");
        return Map.of("success", true, "code", code, "rewardName", row.get("reward_name"), "redeemedAt", now,
                "message", "Voucher validat: " + row.get("reward_name") + ".");
    }

    // ------------------------------------------------------------------ utilitare

    private AdminOrderView view(ShopOrder o) {
        UserProfile u = o.getUser();
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("id", u.getId());
        customer.put("name", u.getName());
        customer.put("email", u.getEmail());
        customer.put("banned", u.isBanned());
        List<String> transitions = o.isRequiresShipping()
                ? TRANSITIONS.getOrDefault(o.getStatus(), Set.of()).stream().sorted().toList() : List.of();
        return new AdminOrderView(OrderService.view(o), customer, o.getAdminNote(), transitions, cancelProblem(o) == null);
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String mask(String code) {
        return code.length() <= 6 ? "****" : code.substring(0, 4) + "…" + code.substring(code.length() - 2);
    }
}
