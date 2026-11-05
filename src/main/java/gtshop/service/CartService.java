package gtshop.service;

import gtshop.model.CartItem;
import java.util.List;

public interface CartService {
    public List<CartItem> getCartItems();

    public void addToCart(CartItem var1);

    public void clearCart();
}

