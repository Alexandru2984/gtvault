package gtshop.service;

import gtshop.model.CartItem;
import gtshop.model.UserProfile;
import gtshop.repository.CartItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartServiceImpl implements CartService {
    private final CartItemRepository cartItemRepository;
    private final UserService userService;

    public CartServiceImpl(CartItemRepository cartItemRepository, UserService userService) {
        this.cartItemRepository = cartItemRepository;
        this.userService = userService;
    }

    private UserProfile getUser() {
        return this.userService.getCurrentUser();
    }

    public List<CartItem> getCartItems() {
        return this.cartItemRepository.findByUser(this.getUser());
    }

    public void addToCart(CartItem item) {
        UserProfile user = this.getUser();
        this.cartItemRepository.findByUserAndRewardId(user, item.getRewardId()).ifPresentOrElse(existing -> {
            existing.setQuantity(existing.getQuantity() + item.getQuantity());
            this.cartItemRepository.save(existing);
        }, () -> {
            item.setUser(user);
            this.cartItemRepository.save(item);
        });
    }

    public void clearCart() {
        this.cartItemRepository.deleteByUser(this.getUser());
    }
}
