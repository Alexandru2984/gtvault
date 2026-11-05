package gtshop.model;

import gtshop.model.CartItem;
import java.util.List;

public class CheckoutResponse {
    private boolean success;
    private String message;
    private int newBalance;
    private Integer missingPoints;
    private List<CartItem> purchasedItems;
    private String transactionId;

    public boolean isSuccess() {
        return this.success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getNewBalance() {
        return this.newBalance;
    }

    public void setNewBalance(int newBalance) {
        this.newBalance = newBalance;
    }

    public Integer getMissingPoints() {
        return this.missingPoints;
    }

    public void setMissingPoints(Integer missingPoints) {
        this.missingPoints = missingPoints;
    }

    public List<CartItem> getPurchasedItems() {
        return this.purchasedItems;
    }

    public void setPurchasedItems(List<CartItem> purchasedItems) {
        this.purchasedItems = purchasedItems;
    }

    public String getTransactionId() {
        return this.transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }
}

