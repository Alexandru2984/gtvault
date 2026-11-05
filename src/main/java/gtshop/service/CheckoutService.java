package gtshop.service;

import gtshop.model.CartItem;
import gtshop.model.CheckoutResponse;
import java.util.List;

public interface CheckoutService {
    CheckoutResponse checkout(List<CartItem> items);
    CheckoutResponse checkout(List<CartItem> items, String promoCode);
}

