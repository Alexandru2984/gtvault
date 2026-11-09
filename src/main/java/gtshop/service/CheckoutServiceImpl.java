package gtshop.service;

import gtshop.model.CartItem;
import gtshop.model.CheckoutResponse;
import gtshop.model.PurchaseRecord;
import gtshop.model.Reward;
import gtshop.model.UserProfile;
import gtshop.repository.RewardRepository;
import gtshop.repository.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CheckoutServiceImpl implements CheckoutService {
    private final UserService userService;
    private final CartService cartService;
    private final UserRepository userRepository;
    private final RewardRepository rewardRepository;
    private final PromoCodeService promoCodeService;
    private final BadgeService badgeService;
    private final WalletService wallet;
    private final QuestService quests;
    private final gtshop.security.CurrentUser currentUser;

    public CheckoutServiceImpl(UserService userService, CartService cartService, UserRepository userRepository, RewardRepository rewardRepository, PromoCodeService promoCodeService, BadgeService badgeService, WalletService wallet, QuestService quests, gtshop.security.CurrentUser currentUser) {
        this.userService = userService;
        this.cartService = cartService;
        this.userRepository = userRepository;
        this.rewardRepository = rewardRepository;
        this.promoCodeService = promoCodeService;
        this.badgeService = badgeService;
        this.wallet = wallet;
        this.quests = quests;
        this.currentUser = currentUser;
    }

    @Override
    public CheckoutResponse checkout(List<CartItem> items) {
        return checkout(items, null);
    }

    @Override
    public CheckoutResponse checkout(List<CartItem> items, String promoCode) {
        CheckoutResponse resp = new CheckoutResponse();

        if (items == null || items.isEmpty()) {
            resp.setSuccess(false);
            resp.setMessage("Coșul este gol!");
            return resp;
        }

        // Blocam intai contul (aceeasi ordine ca restul aplicatiei), apoi produsele
        UserProfile user = this.wallet.lock(this.currentUser.requireId());
        int calculatedTotal = 0;
        List<Reward> rewardsToUpdate = new ArrayList<>();

        // Validate items, enforce authoritative server-side prices, and check stock with pessimistic lock
        for (CartItem item : items) {
            if (item.getQuantity() <= 0) {
                resp.setSuccess(false);
                resp.setMessage("Cantitatea pentru produsul " + item.getName() + " este invalidă!");
                return resp;
            }

            // Lock reward row to prevent race conditions on stock
            Reward reward = this.rewardRepository.findByIdForUpdate(item.getRewardId())
                    .orElse(this.rewardRepository.findById(item.getRewardId()).orElse(null));

            if (reward == null) {
                resp.setSuccess(false);
                resp.setMessage("Produsul " + item.getName() + " nu mai există în catalog!");
                return resp;
            }

            if (reward.getStockCount() < item.getQuantity()) {
                resp.setSuccess(false);
                resp.setMessage("Stoc insuficient pentru " + reward.getName() + "! (Disponibil: " + reward.getStockCount() + ")");
                return resp;
            }

            // CRITICAL SECURITY FIX (SEC-01): Enforce server-authoritative price from DB
            item.setPrice(reward.getPrice());
            item.setName(reward.getName());
            calculatedTotal += reward.getPrice() * item.getQuantity();

            reward.setStockCount(reward.getStockCount() - item.getQuantity());
            if (reward.getStockCount() <= 0) {
                reward.setInStock(false);
            }
            rewardsToUpdate.add(reward);
        }

        // Apply promo code discount if provided
        int discount = 0;
        if (promoCode != null && !promoCode.trim().isEmpty()) {
            discount = this.promoCodeService.applyPromoAtCheckout(promoCode, calculatedTotal);
            resp.setDiscountApplied(discount);
        }

        int finalTotal = Math.max(0, calculatedTotal - discount);

        // Check if user has sufficient activity points
        if (user.getActivityPoints() < finalTotal) {
            resp.setSuccess(false);
            int missing = finalTotal - user.getActivityPoints();
            resp.setMissingPoints(missing);
            resp.setMessage("Puncte insuficiente! Mai ai nevoie de " + missing + " AP.");
            return resp;
        }

        String transactionId = "TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        if (finalTotal > 0) {
            this.wallet.debit(user, finalTotal, gtshop.model.TxType.ORDER_PAYMENT,
                "Comandă #" + transactionId + (discount > 0 ? " (reducere -" + discount + " AP)" : ""));
        }
        this.rewardRepository.saveAll(rewardsToUpdate);
        for (CartItem it : items) {
            PurchaseRecord pr = new PurchaseRecord();
            pr.setId("PR-" + UUID.randomUUID().toString().substring(0, 8));
            pr.setRewardName(it.getName());
            pr.setPointsSpent(it.getPrice() * it.getQuantity());
            pr.setPurchaseDate(Instant.now());
            pr.setStatus("Completed");
            String voucher = "GT-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase() + "-" +
                             UUID.randomUUID().toString().substring(0, 4).toUpperCase() + "-" +
                             UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            pr.setVoucherCode(voucher);
            pr.setUser(user);
            this.userService.addPurchase(pr);
        }

        this.badgeService.onOrder(user, finalTotal);
        this.quests.track(user, gtshop.game.QuestCatalog.Action.PLACE_ORDER, 1);

        resp.setSuccess(true);
        resp.setMessage(discount > 0 
            ? "Cumpărare reușită! (Reducere promo: -" + discount + " AP)"
            : "Cumpărare reușită!");
        resp.setNewBalance(user.getActivityPoints());
        resp.setPurchasedItems(items);
        resp.setTransactionId(transactionId);
        this.cartService.clearCart();
        return resp;
    }
}
