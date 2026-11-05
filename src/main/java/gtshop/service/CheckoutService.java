package gtshop.service;

import gtshop.model.CartItem;
import gtshop.model.CheckoutResponse;
import java.util.List;

public interface CheckoutService {
    public CheckoutResponse checkout(List<CartItem> var1);
}

