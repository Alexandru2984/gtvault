package gtshop.model;

public class CartItem {
    private String rewardId;
    private String name;
    private int price;
    private int quantity;

    public CartItem() {}
    public CartItem(String rewardId, String name, int price, int quantity) {
        this.rewardId = rewardId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
    public String getRewardId() { return rewardId; }
    public void setRewardId(String rewardId) { this.rewardId = rewardId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
