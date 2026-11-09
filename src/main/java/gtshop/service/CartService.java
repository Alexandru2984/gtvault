package gtshop.service;

import gtshop.model.CartItem;
import gtshop.model.Reward;
import gtshop.model.UserProfile;
import gtshop.repository.CartItemRepository;
import gtshop.repository.RewardRepository;
import gtshop.repository.UserRepository;
import gtshop.service.PricingService.CartLine;
import gtshop.service.PricingService.Quote;
import gtshop.web.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cosul de cumparaturi pastrat pe server (sincronizat intre dispozitive). */
@Service
public class CartService {
    public static final int MAX_LINES = 20;

    private final CartItemRepository cart;
    private final RewardRepository rewards;
    private final UserRepository users;
    private final WalletService wallet;
    private final QuoteService quotes;
    private final TimeService time;

    public CartService(CartItemRepository cart, RewardRepository rewards, UserRepository users, WalletService wallet,
                       QuoteService quotes, TimeService time) {
        this.cart = cart;
        this.rewards = rewards;
        this.users = users;
        this.wallet = wallet;
        this.quotes = quotes;
        this.time = time;
    }

    public List<CartLine> lines(String userId) {
        return cart.findForUser(userId).stream().map(c -> new CartLine(c.getRewardId(), c.getQuantity())).toList();
    }

    @Transactional(readOnly = true)
    public Quote view(String userId, String promoCode) {
        UserProfile u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        return quotes.quote(u, lines(userId), promoCode, false).quote();
    }

    @Transactional
    public Quote add(String userId, String rewardId, Integer quantity) {
        int qty = quantity == null ? 1 : quantity;
        if (qty < 1 || qty > 99) {
            throw ApiException.badRequest("Cantitatea trebuie să fie între 1 și 99.");
        }
        UserProfile u = wallet.lock(userId);
        Reward r = activeReward(rewardId);
        CartItem line = cart.findLine(userId, r.getId()).orElse(null);
        int next = (line == null ? 0 : line.getQuantity()) + qty;
        checkQuantity(r, next);
        if (line == null) {
            if (cart.countForUser(userId) >= MAX_LINES) {
                throw ApiException.badRequest("Coșul poate conține cel mult " + MAX_LINES + " produse diferite.");
            }
            cart.save(new CartItem(u, r, next, time.now()));
        } else {
            line.setQuantity(next);
            line.setPrice(r.getPrice());
            line.setName(r.getName());
            cart.save(line);
        }
        return quotes.quote(u, lines(userId), null, false).quote();
    }

    @Transactional
    public Quote setQuantity(String userId, String rewardId, Integer quantity) {
        if (quantity == null || quantity < 0 || quantity > 99) {
            throw ApiException.badRequest("Cantitatea trebuie să fie între 0 și 99.");
        }
        UserProfile u = wallet.lock(userId);
        CartItem line = cart.findLine(userId, rewardId)
                .orElseThrow(() -> ApiException.notFound("Produsul nu este în coș."));
        if (quantity == 0) {
            cart.delete(line);
        } else {
            Reward r = activeReward(rewardId);
            checkQuantity(r, quantity);
            line.setQuantity(quantity);
            line.setPrice(r.getPrice());
            cart.save(line);
        }
        cart.flush();
        return quotes.quote(u, lines(userId), null, false).quote();
    }

    @Transactional
    public Quote remove(String userId, String rewardId) {
        UserProfile u = wallet.lock(userId);
        cart.findLine(userId, rewardId).ifPresent(cart::delete);
        cart.flush();
        return quotes.quote(u, lines(userId), null, false).quote();
    }

    @Transactional
    public void clear(String userId) {
        wallet.lock(userId);
        cart.clear(userId);
    }

    private Reward activeReward(String rewardId) {
        if (rewardId == null || rewardId.isBlank() || rewardId.length() > 255) {
            throw ApiException.badRequest("Produsul nu este valid.");
        }
        return rewards.findById(rewardId).filter(Reward::isActive)
                .orElseThrow(() -> ApiException.notFound("Produsul nu a fost găsit."));
    }

    private static void checkQuantity(Reward r, int qty) {
        if (r.getStockCount() <= 0) {
            throw ApiException.badRequest(r.getName() + " nu mai este în stoc.");
        }
        if (qty > r.getMaxPerOrder()) {
            throw ApiException.badRequest("Poți comanda maximum " + r.getMaxPerOrder() + " bucăți din " + r.getName() + ".");
        }
        if (qty > r.getStockCount()) {
            throw ApiException.badRequest("Stoc insuficient pentru " + r.getName() + " (disponibil: " + r.getStockCount() + ").");
        }
    }
}
