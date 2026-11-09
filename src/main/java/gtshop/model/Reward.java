package gtshop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name="rewards")
public class Reward {
    @Id
    private String id;
    private String name;
    @Column(length=500)
    private String description;
    @Column(length=2000)
    private String fullDescription;
    private int price;
    private String image;
    private String category;
    private boolean inStock;
    private int stockCount;
    @Column(name="is_new", nullable = false)
    private boolean isNew;

    /** Produsele inactive nu apar in catalog si nu pot fi cumparate (istoricul ramane). */
    @Column(nullable = false)
    private boolean active = true;

    /** PHYSICAL (cere adresa de livrare), DIGITAL (voucher) sau BADGE (insigna livrata in aplicatie). */
    @Column(nullable = false, length = 16)
    private String fulfillment = FULFILLMENT_DIGITAL;

    @Column(name = "max_per_order", nullable = false)
    private int maxPerOrder = 5;

    /** Nivel minim (BRONZE..DIAMOND) necesar pentru a cumpara; null = oricine. */
    @Column(name = "min_tier", length = 16)
    private String minTier;

    @Column(name = "created_at", nullable = false)
    private java.time.Instant createdAt;

    public static final String FULFILLMENT_PHYSICAL = "PHYSICAL";
    public static final String FULFILLMENT_DIGITAL = "DIGITAL";
    public static final String FULFILLMENT_BADGE = "BADGE";

    @jakarta.persistence.PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = java.time.Instant.now();
        }
    }

    public Reward() {
    }

    public Reward(String id, String name, String description, String fullDescription, int price, String image, String category, boolean inStock, int stockCount, boolean isNew) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.fullDescription = fullDescription;
        this.price = price;
        this.image = image;
        this.category = category;
        this.inStock = inStock;
        this.stockCount = stockCount;
        this.isNew = isNew;
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFullDescription() {
        return this.fullDescription;
    }

    public void setFullDescription(String fullDescription) {
        this.fullDescription = fullDescription;
    }

    public int getPrice() {
        return this.price;
    }

    public void setPrice(int price) {
        this.price = price;
    }

    public String getImage() {
        return this.image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getCategory() {
        return this.category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isInStock() {
        return this.inStock;
    }

    public void setInStock(boolean inStock) {
        this.inStock = inStock;
    }

    public int getStockCount() {
        return this.stockCount;
    }

    public void setStockCount(int stockCount) {
        this.stockCount = stockCount;
    }

    public boolean isNew() {
        return this.isNew;
    }

    public void setNew(boolean aNew) {
        this.isNew = aNew;
    }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getFulfillment() { return fulfillment; }
    public void setFulfillment(String fulfillment) { this.fulfillment = fulfillment; }
    public int getMaxPerOrder() { return maxPerOrder; }
    public void setMaxPerOrder(int maxPerOrder) { this.maxPerOrder = maxPerOrder; }
    public String getMinTier() { return minTier; }
    public void setMinTier(String minTier) { this.minTier = minTier; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }
    public boolean isPhysical() { return FULFILLMENT_PHYSICAL.equals(fulfillment); }
}
