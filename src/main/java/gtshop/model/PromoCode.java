package gtshop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "promo_codes")
public class PromoCode {
    @Id
    private String id;

    @Column(unique = true, nullable = false)
    private String code;

    private int discountPoints;
    private int discountPercent;
    private int minOrderPoints;
    private int maxUses;
    private int currentUses;
    private boolean isActive;
    private Instant expiresAt;

    @jakarta.persistence.Column(length = 140)
    private String description;

    /** Plafon optional pentru reducerile procentuale. */
    @jakarta.persistence.Column(name = "max_discount")
    private Integer maxDiscount;

    @jakarta.persistence.Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public PromoCode() {
    }

    public PromoCode(String id, String code, int discountPoints, int discountPercent, int minOrderPoints, int maxUses, int currentUses, boolean isActive) {
        this.id = id;
        this.code = code;
        this.discountPoints = discountPoints;
        this.discountPercent = discountPercent;
        this.minOrderPoints = minOrderPoints;
        this.maxUses = maxUses;
        this.currentUses = currentUses;
        this.isActive = isActive;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return this.code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public int getDiscountPoints() {
        return this.discountPoints;
    }

    public void setDiscountPoints(int discountPoints) {
        this.discountPoints = discountPoints;
    }

    public int getDiscountPercent() {
        return this.discountPercent;
    }

    public void setDiscountPercent(int discountPercent) {
        this.discountPercent = discountPercent;
    }

    public int getMinOrderPoints() {
        return this.minOrderPoints;
    }

    public void setMinOrderPoints(int minOrderPoints) {
        this.minOrderPoints = minOrderPoints;
    }

    public int getMaxUses() {
        return this.maxUses;
    }

    public void setMaxUses(int maxUses) {
        this.maxUses = maxUses;
    }

    public int getCurrentUses() {
        return this.currentUses;
    }

    public void setCurrentUses(int currentUses) {
        this.currentUses = currentUses;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public Instant getExpiresAt() {
        return this.expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getMaxDiscount() { return maxDiscount; }
    public void setMaxDiscount(Integer maxDiscount) { this.maxDiscount = maxDiscount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
