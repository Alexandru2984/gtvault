package gtshop.service;

import gtshop.model.CartItem;
import gtshop.model.CheckoutResponse;
import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;

import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CheckoutServiceImpl implements CheckoutService {
    private final UserService userService;
    private final CartService cartService;
    public CheckoutServiceImpl(UserService userService, CartService cartService) {
        this.userService = userService;
        this.cartService = cartService;
    }
    @Override public CheckoutResponse checkout(List<CartItem> items) {
        int total = items.stream().mapToInt(i -> i.getPrice() * i.getQuantity()).sum();
        UserProfile user = userService.getUser();
        CheckoutResponse resp = new CheckoutResponse();
        if (user.getActivityPoints() < total) { resp.setSuccess(false); resp.setMessage("Puncte insuficiente!"); return resp; }
        user.setActivityPoints(user.getActivityPoints() - total);
        items.forEach(it -> {
            PurchaseRecord pr = new PurchaseRecord();
            pr.setId(UUID.randomUUID().toString());
            pr.setRewardName(it.getName());
            pr.setPointsSpent(it.getPrice() * it.getQuantity());
            pr.setPurchaseDate(Instant.now());
            pr.setStatus("Completed");
            userService.addPurchase(pr);
        });
        resp.setSuccess(true); resp.setMessage("Cumpărare reuşită!"); resp.setNewBalance(user.getActivityPoints());
        resp.setPurchasedItems(items); resp.setTransactionId(UUID.randomUUID().toString()); cartService.clearCart();
        return resp;
    }
}
