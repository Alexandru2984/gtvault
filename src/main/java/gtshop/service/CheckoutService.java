package gtshop.service;

import gtshop.game.Badge;
import gtshop.game.QuestCatalog.Action;
import gtshop.model.FlashDeal;
import gtshop.model.PromoRedemption;
import gtshop.model.PurchaseRecord;
import gtshop.model.Reward;
import gtshop.model.ShopOrder;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.CartItemRepository;
import gtshop.repository.PromoRedemptionRepository;
import gtshop.repository.PurchaseRecordRepository;
import gtshop.repository.ShopOrderRepository;
import gtshop.service.PricingService.CartLine;
import gtshop.service.PricingService.Line;
import gtshop.service.PricingService.Quote;
import gtshop.util.Ids;
import gtshop.util.InputSanitizer;
import gtshop.util.ShippingValidator;
import gtshop.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plasarea comenzii, integral pe server: pretul vine din catalog, stocul/ofertele/codul promo sunt blocate,
 * iar cheia de idempotenta face ca un dublu-click sau o reincercare de retea sa nu plateasca de doua ori.
 */
@Service
public class CheckoutService {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private final CartService cart;
    private final CartItemRepository cartItems;
    private final QuoteService quotes;
    private final ShopOrderRepository orders;
    private final PurchaseRecordRepository purchases;
    private final PromoRedemptionRepository redemptions;
    private final WalletService wallet;
    private final BadgeService badges;
    private final QuestService quests;
    private final NotificationService notifications;
    private final gtshop.repository.UserRepository users;
    private final TimeService time;

    public CheckoutService(CartService cart, CartItemRepository cartItems, QuoteService quotes, ShopOrderRepository orders,
                           PurchaseRecordRepository purchases, PromoRedemptionRepository redemptions, WalletService wallet,
                           BadgeService badges, QuestService quests, NotificationService notifications,
                           gtshop.repository.UserRepository users, TimeService time) {
        this.cart = cart;
        this.cartItems = cartItems;
        this.quotes = quotes;
        this.orders = orders;
        this.purchases = purchases;
        this.redemptions = redemptions;
        this.wallet = wallet;
        this.badges = badges;
        this.quests = quests;
        this.notifications = notifications;
        this.users = users;
        this.time = time;
    }

    public record CheckoutRequest(String promoCode, ShippingValidator.Shipping shipping, String notes, Integer expectedTotal) {}

    public record CheckoutResult(boolean success, boolean replayed, String message, OrderService.OrderView order,
                                 int newBalance, List<String> badgesUnlocked) {}

    @Transactional(readOnly = true)
    public Quote quote(String userId, String promoCode) {
        UserProfile u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        return quotes.quote(u, cart.lines(userId), promoCode, false).quote();
    }

    @Transactional
    public CheckoutResult placeOrder(String userId, String idempotencyKey, CheckoutRequest req) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("Antetul Idempotency-Key lipsește sau nu este valid.");
        }
        CheckoutRequest r = req == null ? new CheckoutRequest(null, null, null, null) : req;
        UserProfile user = wallet.lock(userId);

        var existing = orders.findByIdempotencyKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            return new CheckoutResult(true, true, "Comanda a fost deja plasată.", OrderService.view(existing.get()),
                    user.getActivityPoints(), List.of());
        }

        List<CartLine> lines = cart.lines(userId);
        if (lines.isEmpty()) {
            throw ApiException.badRequest("Coșul este gol.");
        }
        QuoteService.Priced priced = quotes.quote(user, lines, r.promoCode(), true);
        Quote q = priced.quote();
        if (!q.issues().isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "CART_INVALID", String.join(" ", q.issues()));
        }
        if (q.promo() != null && !q.promo().valid()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROMO_INVALID", q.promo().message());
        }
        if (r.expectedTotal() != null && r.expectedTotal() != q.total()) {
            throw new ApiException(HttpStatus.CONFLICT, "PRICE_CHANGED",
                    "Totalul comenzii s-a schimbat (acum: " + q.total() + " AP). Verifică din nou coșul.");
        }
        ShippingValidator.Shipping shipping = q.requiresShipping() ? ShippingValidator.validate(r.shipping()) : null;
        String notes = InputSanitizer.cleanOptional(r.notes(), 300, "Observațiile");

        Instant now = time.now();
        String orderId = "ORD-" + Ids.code(10);
        if (q.total() > 0) {
            wallet.debit(user, q.total(), TxType.ORDER_PAYMENT, "Comanda " + orderId + " (" + q.itemCount() + " produse)");
        }

        ShopOrder order = new ShopOrder();
        order.setId(orderId);
        order.setUser(user);
        order.setStatus(q.requiresShipping() ? ShopOrder.PLACED : ShopOrder.FULFILLED);
        order.setSubtotal(q.subtotal());
        order.setFlashDiscount(q.flashDiscount());
        order.setTierCode(q.tier());
        order.setTierDiscount(q.tierDiscount());
        order.setPromoCode(q.promo() != null && q.promo().valid() ? q.promo().code() : null);
        order.setPromoDiscount(q.promoDiscount());
        order.setTotal(q.total());
        order.setIdempotencyKey(idempotencyKey);
        order.setRequiresShipping(q.requiresShipping());
        if (shipping != null) {
            order.setShippingName(shipping.name());
            order.setShippingPhone(shipping.phone());
            order.setShippingAddress(shipping.address());
            order.setShippingCity(shipping.city());
            order.setShippingCounty(shipping.county());
            order.setShippingPostalCode(shipping.postalCode());
        }
        order.setNotes(notes);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        orders.save(order);

        boolean heroBadge = false;
        for (Line line : q.lines()) {
            Reward reward = priced.rewards().get(line.rewardId());
            reward.setStockCount(reward.getStockCount() - line.quantity());
            reward.setInStock(reward.getStockCount() > 0);
            FlashDeal deal = priced.deals().get(line.rewardId());
            if (deal != null && line.dealUnits() > 0) {
                deal.setSoldQuantity(deal.getSoldQuantity() + line.dealUnits());
            }
            PurchaseRecord p = new PurchaseRecord();
            p.setId("PR-" + Ids.code(12));
            p.setOrder(order);
            p.setUser(user);
            p.setRewardId(reward.getId());
            p.setRewardName(reward.getName());
            p.setQuantity(line.quantity());
            p.setUnitPrice(line.unitPrice());
            p.setPointsSpent(line.lineTotal());
            p.setFulfillment(reward.getFulfillment());
            p.setFlashDealId(line.dealUnits() > 0 ? line.flashDealId() : null);
            p.setFlashUnits(line.dealUnits());
            p.setPurchaseDate(now);
            if (reward.isPhysical()) {
                p.setStatus(ShopOrder.PLACED);
            } else {
                p.setStatus(ShopOrder.FULFILLED);
                if (Reward.FULFILLMENT_DIGITAL.equals(reward.getFulfillment())) {
                    p.setVoucherCode(uniqueVoucher());
                } else {
                    heroBadge = true;
                }
            }
            purchases.save(p);
            order.getItems().add(p);
        }

        if (priced.promo() != null && order.getPromoCode() != null) {
            priced.promo().setCurrentUses(priced.promo().getCurrentUses() + 1);
            redemptions.save(new PromoRedemption(priced.promo().getId(), userId, orderId, q.promoDiscount(), now));
        }
        cartItems.clear(userId);

        List<Badge> unlocked = new java.util.ArrayList<>(badges.onOrder(user, q.total()));
        if (heroBadge && badges.award(user, Badge.HERO, false)) {
            unlocked.add(Badge.HERO);
        }
        quests.track(user, Action.PLACE_ORDER, 1);
        notifications.notify(userId, "ORDER", "Comanda " + orderId + " a fost plasată 🎉",
                q.requiresShipping() ? "Te anunțăm când este expediată." : "Codurile tale digitale sunt disponibile în comandă.",
                "🛍️", "#/comenzi/" + orderId);

        String msg = q.requiresShipping()
                ? "Comandă plasată! Te anunțăm când pleacă spre tine."
                : "Comandă finalizată! Găsești codurile în detaliile comenzii.";
        return new CheckoutResult(true, false, msg, OrderService.view(order), user.getActivityPoints(),
                unlocked.stream().map(Badge::name).toList());
    }

    private String uniqueVoucher() {
        for (int i = 0; i < 10; i++) {
            String code = Ids.voucher();
            if (!purchases.existsByVoucherCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Nu am putut genera un cod voucher unic");
    }
}
