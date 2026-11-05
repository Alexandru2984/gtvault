package gtshop.service;

import gtshop.model.CartItem;
import gtshop.model.UserProfile;
import gtshop.repository.CartItemRepository;
import gtshop.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CartServiceImpl implements CartService {
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private static final String DEFAULT_USER_ID = "user-123";

    public CartServiceImpl(CartItemRepository cartItemRepository, UserRepository userRepository) {
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
    }

    private UserProfile getDefaultUser() {
        return this.userRepository.findById(DEFAULT_USER_ID).orElseGet(() -> {
            UserProfile user = new UserProfile(DEFAULT_USER_ID, "Popescu Ion", "https://example.com/avatar.png", 2056);
            return this.userRepository.save(user);
        });
    }

    public List<CartItem> getCartItems() {
        return this.cartItemRepository.findByUser(this.getDefaultUser());
    }

    public void addToCart(CartItem item) {
        UserProfile user = this.getDefaultUser();
        this.cartItemRepository.findByUserAndRewardId(user, item.getRewardId()).ifPresentOrElse(existing -> {
            existing.setQuantity(existing.getQuantity() + item.getQuantity());
            this.cartItemRepository.save(existing);
        }, () -> {
            item.setUser(user);
            this.cartItemRepository.save(item);
        });
    }

    public void clearCart() {
        this.cartItemRepository.deleteByUser(this.getDefaultUser());
    }
}
