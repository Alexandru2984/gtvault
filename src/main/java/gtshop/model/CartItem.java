package gtshop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gtshop.model.UserProfile;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name="cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private String rewardId;
    private String name;
    private int price;
    private int quantity;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="user_id")
    @JsonIgnore
    private UserProfile user;

    public CartItem() {
    }

    public CartItem(String rewardId, String name, int price, int quantity) {
        this.rewardId = rewardId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRewardId() {
        return this.rewardId;
    }

    public void setRewardId(String rewardId) {
        this.rewardId = rewardId;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPrice() {
        return this.price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public int getQuantity() {
        return this.quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public UserProfile getUser() {
        return this.user;
    }

    public void setUser(UserProfile user) {
        this.user = user;
    }
}

