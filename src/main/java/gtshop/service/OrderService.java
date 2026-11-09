package gtshop.service;

import gtshop.model.FlashDeal;
import gtshop.model.PurchaseRecord;
import gtshop.model.Reward;
import gtshop.model.ShopOrder;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.FlashDealRepository;
import gtshop.repository.PromoCodeRepository;
import gtshop.repository.PromoRedemptionRepository;
import gtshop.repository.PurchaseRecordRepository;
import gtshop.repository.RewardRepository;
import gtshop.repository.ShopOrderRepository;
import gtshop.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Comenzile utilizatorului: istoric, detalii, anulare cu rambursare si verificarea voucherelor. */
@Service
public class OrderService {
    public static final Map<String, String> STATUS_LABELS = Map.of(
            ShopOrder.PLACED, "Plasată",
            ShopOrder.PROCESSING, "În pregătire",
            ShopOrder.SHIPPED, "Expediată",
            ShopOrder.DELIVERED, "Livrată",
            ShopOrder.FULFILLED, "Finalizată",
            ShopOrder.CANCELLED, "Anulată");
    private static final List<String> PHYSICAL_FLOW =
            List.of(ShopOrder.PLACED, ShopOrder.PROCESSING, ShopOrder.SHIPPED, ShopOrder.DELIVERED);
    private static final List<String> DIGITAL_FLOW = List.of(ShopOrder.PLACED, ShopOrder.FULFILLED);

    private final ShopOrderRepository orders;
    private final PurchaseRecordRepository purchases;
    private final RewardRepository rewards;
    private final FlashDealRepository deals;
    private final PromoCodeRepository promos;
    private final PromoRedemptionRepository redemptions;
    private final WalletService wallet;
    private final NotificationService notifications;
    private final TimeService time;
    private final gtshop.util.Locks locks;

    public OrderService(ShopOrderRepository orders, PurchaseRecordRepository purchases, RewardRepository rewards,
                        FlashDealRepository deals, PromoCodeRepository promos, PromoRedemptionRepository redemptions,
                        WalletService wallet, NotificationService notifications, TimeService time,
                        gtshop.util.Locks locks) {
        this.orders = orders;
        this.purchases = purchases;
        this.rewards = rewards;
        this.deals = deals;
        this.promos = promos;
        this.redemptions = redemptions;
        this.wallet = wallet;
        this.notifications = notifications;
        this.time = time;
        this.locks = locks;
    }

    public record ItemView(String id, String rewardId, String name, int quantity, int unitPrice, int pointsSpent,
                           int flashUnits, String fulfillment, String status, String voucherCode, boolean redeemed) {}

    public record Step(String status, String label, boolean reached, boolean current) {}

    public record OrderView(String id, String status, String statusLabel, Instant createdAt, Instant updatedAt,
                            List<ItemView> items, int itemCount, int subtotal, int flashDiscount, String tier,
                            int tierDiscount, String promoCode, int promoDiscount, int total, boolean requiresShipping,
                            Map<String, String> shipping, String notes, String trackingCode, List<Step> timeline,
                            boolean canCancel) {}

    // ------------------------------------------------------------------ citire

    @Transactional(readOnly = true)
    public Map<String, Object> list(String userId, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        Page<ShopOrder> result = orders.findForUser(userId, PageRequest.of(p, s));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", result.getContent().stream().map(OrderService::view).toList());
        res.put("page", p);
        res.put("size", s);
        res.put("totalItems", result.getTotalElements());
        res.put("totalPages", result.getTotalPages());
        return res;
    }

    @Transactional(readOnly = true)
    public OrderView get(String userId, String orderId) {
        return view(orders.findForUser(orderId, userId)
                .orElseThrow(() -> ApiException.notFound("Comanda nu a fost găsită.")));
    }

    /** Ultima adresa de livrare folosita (pentru precompletarea formularului). */
    @Transactional(readOnly = true)
    public Map<String, String> lastShipping(String userId) {
        return orders.findShippingOrders(userId, PageRequest.of(0, 1)).stream().findFirst()
                .map(OrderService::shipping).orElse(Map.of());
    }

    // ------------------------------------------------------------------ anulare

    @Transactional
    public OrderView cancelByUser(String userId, String orderId) {
        UserProfile user = wallet.lock(userId);
        ShopOrder order = orders.findByIdForUpdate(orderId)
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Comanda nu a fost găsită."));
        if (!canCancel(order)) {
            throw new ApiException(HttpStatus.CONFLICT, "CANNOT_CANCEL", ShopOrder.PLACED.equals(order.getStatus())
                    ? "Comanda conține produse digitale deja livrate și nu mai poate fi anulată."
                    : "Comanda nu mai poate fi anulată (status: " + STATUS_LABELS.getOrDefault(order.getStatus(), order.getStatus()) + ").");
        }
        cancel(user, order, "Anulată de client");
        return view(order);
    }

    /**
     * Anulare cu rambursare integrala, readucerea stocului, eliberarea ofertei flash si a codului promo.
     * Contul si comanda trebuie sa fie blocate de apelant (folosita si de panoul de administrare).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancel(UserProfile lockedOwner, ShopOrder order, String reason) {
        if (ShopOrder.CANCELLED.equals(order.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CANCELLED", "Comanda este deja anulată.");
        }
        TreeSet<String> rewardIds = new TreeSet<>();
        TreeSet<String> dealIds = new TreeSet<>();
        for (PurchaseRecord p : order.getItems()) {
            if (p.getRewardId() != null) {
                rewardIds.add(p.getRewardId());
            }
            if (p.getFlashDealId() != null && p.getFlashUnits() > 0) {
                dealIds.add(p.getFlashDealId());
            }
        }
        Map<String, Reward> locked = new LinkedHashMap<>();
        rewards.findAllByIdForUpdate(rewardIds).forEach(r -> locked.put(r.getId(), locks.refresh(r)));
        Map<String, FlashDeal> lockedDeals = new LinkedHashMap<>();
        for (String id : dealIds) {
            deals.findByIdForUpdate(id).ifPresent(d -> lockedDeals.put(id, locks.refresh(d)));
        }
        for (PurchaseRecord p : order.getItems()) {
            Reward r = p.getRewardId() == null ? null : locked.get(p.getRewardId());
            if (r != null) {
                r.setStockCount(r.getStockCount() + p.getQuantity());
                r.setInStock(true);
            }
            FlashDeal d = p.getFlashDealId() == null ? null : lockedDeals.get(p.getFlashDealId());
            if (d != null) {
                d.setSoldQuantity(Math.max(0, d.getSoldQuantity() - p.getFlashUnits()));
            }
            p.setStatus(ShopOrder.CANCELLED);
        }
        redemptions.findByOrderId(order.getId()).ifPresent(red -> {
            promos.findByIdForUpdate(red.getPromoId()).ifPresent(pc -> {
                locks.refresh(pc);
                pc.setCurrentUses(Math.max(0, pc.getCurrentUses() - 1));
            });
            redemptions.delete(red);
        });
        if (order.getTotal() > 0) {
            wallet.credit(lockedOwner, order.getTotal(), TxType.ORDER_REFUND, "Rambursare comanda " + order.getId());
        }
        order.setStatus(ShopOrder.CANCELLED);
        order.setAdminNote(reason);
        order.setUpdatedAt(time.now());
        orders.save(order);
        notifications.notify(lockedOwner.getId(), "ORDER", "Comanda " + order.getId() + " a fost anulată",
                order.getTotal() > 0 ? "Ți-am returnat " + order.getTotal() + " AP." : reason, "↩️", "#/comenzi/" + order.getId());
    }

    static boolean canCancel(ShopOrder o) {
        return ShopOrder.PLACED.equals(o.getStatus())
                && o.getItems().stream().allMatch(p -> Reward.FULFILLMENT_PHYSICAL.equals(p.getFulfillment()));
    }

    // ------------------------------------------------------------------ vouchere

    @Transactional(readOnly = true)
    public Map<String, Object> verifyVoucher(String rawCode) {
        String code = rawCode == null ? "" : rawCode.trim().toUpperCase(Locale.ROOT);
        if (code.length() < 8 || code.length() > 40) {
            throw ApiException.badRequest("Codul voucherului nu este valid.");
        }
        PurchaseRecord p = purchases.findByVoucherCode(code)
                .orElseThrow(() -> ApiException.notFound("Voucherul nu există."));
        String state = ShopOrder.CANCELLED.equals(p.getStatus()) ? "CANCELLED"
                : p.getRedeemedAt() != null ? "REDEEMED" : "ACTIVE";
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("code", code);
        res.put("valid", "ACTIVE".equals(state));
        res.put("state", state);
        res.put("rewardName", p.getRewardName());
        res.put("quantity", p.getQuantity());
        res.put("issuedAt", p.getPurchaseDate());
        res.put("redeemedAt", p.getRedeemedAt());
        return res;
    }

    // ------------------------------------------------------------------ mapare

    static Map<String, String> shipping(ShopOrder o) {
        if (!o.isRequiresShipping()) {
            return null;
        }
        Map<String, String> m = new LinkedHashMap<>();
        m.put("name", o.getShippingName());
        m.put("phone", o.getShippingPhone());
        m.put("address", o.getShippingAddress());
        m.put("city", o.getShippingCity());
        m.put("county", o.getShippingCounty());
        m.put("postalCode", o.getShippingPostalCode());
        return m;
    }

    public static OrderView view(ShopOrder o) {
        List<ItemView> items = new ArrayList<>();
        int count = 0;
        for (PurchaseRecord p : o.getItems()) {
            boolean digital = !Reward.FULFILLMENT_PHYSICAL.equals(p.getFulfillment());
            items.add(new ItemView(p.getId(), p.getRewardId(), p.getRewardName(), p.getQuantity(),
                    p.getUnitPrice() == null ? p.getPointsSpent() : p.getUnitPrice(), p.getPointsSpent(), p.getFlashUnits(),
                    p.getFulfillment(), p.getStatus(),
                    digital && !ShopOrder.CANCELLED.equals(p.getStatus()) ? p.getVoucherCode() : null,
                    p.getRedeemedAt() != null));
            count += p.getQuantity();
        }
        List<String> flow = o.isRequiresShipping() ? PHYSICAL_FLOW : DIGITAL_FLOW;
        List<Step> timeline = new ArrayList<>();
        int reachedIndex = flow.indexOf(o.getStatus());
        for (int i = 0; i < flow.size(); i++) {
            String st = flow.get(i);
            boolean reached = reachedIndex >= 0 && i <= reachedIndex;
            timeline.add(new Step(st, STATUS_LABELS.get(st), reached, i == reachedIndex));
        }
        if (ShopOrder.CANCELLED.equals(o.getStatus())) {
            timeline.add(new Step(ShopOrder.CANCELLED, STATUS_LABELS.get(ShopOrder.CANCELLED), true, true));
        }
        return new OrderView(o.getId(), o.getStatus(), STATUS_LABELS.getOrDefault(o.getStatus(), o.getStatus()),
                o.getCreatedAt(), o.getUpdatedAt(), items, count, o.getSubtotal(), o.getFlashDiscount(), o.getTierCode(),
                o.getTierDiscount(), o.getPromoCode(), o.getPromoDiscount(), o.getTotal(), o.isRequiresShipping(),
                shipping(o), o.getNotes(), o.getTrackingCode(), timeline, canCancel(o));
    }
}
