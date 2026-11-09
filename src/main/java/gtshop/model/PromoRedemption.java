package gtshop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "promo_redemptions")
public class PromoRedemption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "promo_id", nullable = false)
    private String promoId;
    @Column(name = "user_id", nullable = false)
    private String userId;
    @Column(name = "order_id", length = 32)
    private String orderId;
    @Column(nullable = false)
    private int discount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PromoRedemption() {
    }

    public PromoRedemption(String promoId, String userId, String orderId, int discount, Instant createdAt) {
        this.promoId = promoId;
        this.userId = userId;
        this.orderId = orderId;
        this.discount = discount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getPromoId() { return promoId; }
    public String getUserId() { return userId; }
    public String getOrderId() { return orderId; }
    public int getDiscount() { return discount; }
    public Instant getCreatedAt() { return createdAt; }
}
