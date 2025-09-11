package gtshop.service;

import gtshop.model.CartItem;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class CartServiceImpl implements CartService {
    private final List<CartItem> cart = new ArrayList<>();
    @Override public List<CartItem> getCartItems() { return new ArrayList<>(cart); }
    @Override public void addToCart(CartItem item) { cart.add(item); }
    @Override public void clearCart() { cart.clear(); }
}
