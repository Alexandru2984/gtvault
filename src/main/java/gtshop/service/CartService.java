package gtshop.service;

import gtshop.model.CartItem;
import java.util.List;

public interface CartService {
    List<CartItem> getCartItems();
    void addToCart(CartItem item);
    void clearCart();
}
