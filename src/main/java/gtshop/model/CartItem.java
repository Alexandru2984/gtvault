package gtshop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Linie din cosul de pe server. {@code name} si {@code price} sunt doar o fotografie informativa din
 * momentul adaugarii: la afisare si la plata se folosesc mereu valorile curente din catalog.
 */
@Entity
@Table(name = "cart_items")
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reward_id", nullable = false)
    private String rewardId;

    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private UserProfile user;

    public CartItem() {
    }

    public CartItem(UserProfile user, Reward reward, int quantity, Instant now) {
        this.user = user;
        this.rewardId = reward.getId();
        this.name = reward.getName();
        this.price = reward.getPrice();
        this.quantity = quantity;
        this.addedAt = now;
    }

    public Long getId() { return id; }
    public String getRewardId() { return rewardId; }
    public String getName() { return name; }
    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }
    public void setName(String name) { this.name = name; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public Instant getAddedAt() { return addedAt; }
    public UserProfile getUser() { return user; }
}
