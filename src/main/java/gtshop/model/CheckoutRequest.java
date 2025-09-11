package gtshop.model;

import java.util.List;

public class CheckoutRequest {
    private List<CartItem> items;

    public CheckoutRequest() {}
    public List<CartItem> getItems() { return items; }
    public void setItems(List<CartItem> items) { this.items = items; }
}
