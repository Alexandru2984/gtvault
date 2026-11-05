package gtshop.model;

import gtshop.model.CartItem;
import java.util.List;

public class CheckoutRequest {
    private List<CartItem> items;

    public List<CartItem> getItems() {
        return this.items;
    }

    public void setItems(List<CartItem> items) {
        this.items = items;
    }
}

