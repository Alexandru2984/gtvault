package gtshop.service;

import gtshop.model.CartItem;
import gtshop.model.CheckoutResponse;
import gtshop.model.PurchaseRecord;
import gtshop.model.Reward;
import gtshop.model.UserProfile;
import gtshop.repository.RewardRepository;
import gtshop.repository.UserRepository;
import java.time.Instant;
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

    public CheckoutServiceImpl(UserService userService, CartService cartService, UserRepository userRepository, RewardRepository rewardRepository) {
        this.userService = userService;
        this.cartService = cartService;
        this.userRepository = userRepository;
        this.rewardRepository = rewardRepository;
    }

    public CheckoutResponse checkout(List<CartItem> items) {
        int total = items.stream().mapToInt(i -> i.getPrice() * i.getQuantity()).sum();
        UserProfile user = this.userService.getUser();
        CheckoutResponse resp = new CheckoutResponse();
        if (user.getActivityPoints() < total) {
            resp.setSuccess(false);
            resp.setMessage("Puncte insuficiente!");
            return resp;
        }
        for (CartItem item : items) {
            Reward reward = this.rewardRepository.findById(item.getRewardId()).orElse(null);
            if (reward == null) continue;
            if (reward.getStockCount() < item.getQuantity()) {
                resp.setSuccess(false);
                resp.setMessage("Stoc insuficient pentru " + reward.getName());
                return resp;
            }
            reward.setStockCount(reward.getStockCount() - item.getQuantity());
            if (reward.getStockCount() <= 0) {
                reward.setInStock(false);
            }
            this.rewardRepository.save(reward);
        }
        user.setActivityPoints(user.getActivityPoints() - total);
        this.userRepository.save(user);
        String transactionId = UUID.randomUUID().toString();
        items.forEach(it -> {
            PurchaseRecord pr = new PurchaseRecord();
            pr.setId(UUID.randomUUID().toString());
            pr.setRewardName(it.getName());
            pr.setPointsSpent(it.getPrice() * it.getQuantity());
            pr.setPurchaseDate(Instant.now());
            pr.setStatus("Completed");
            this.userService.addPurchase(pr);
        });
        resp.setSuccess(true);
        resp.setMessage("Cumpărare reușită!");
        resp.setNewBalance(user.getActivityPoints());
        resp.setPurchasedItems(items);
        resp.setTransactionId(transactionId);
        this.cartService.clearCart();
        return resp;
    }
}
