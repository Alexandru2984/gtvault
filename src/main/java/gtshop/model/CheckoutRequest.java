package gtshop.model;

import gtshop.model.CartItem;
import java.util.List;

public class CheckoutRequest {
    private List<CartItem> items;
    private String promoCode;

    public List<CartItem> getItems() {
        return this.items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }

    public String getPromoCode() {
        return this.promoCode;
    }

    public void setPromoCode(String promoCode) {
        this.promoCode = promoCode;
    }
}

