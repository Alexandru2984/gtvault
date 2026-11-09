package gtshop.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** O comanda plasata in magazin (tabela {@code orders}). */
@Entity
@Table(name = "orders")
public class ShopOrder {
    public static final String PLACED = "PLACED";
    public static final String PROCESSING = "PROCESSING";
    public static final String SHIPPED = "SHIPPED";
    public static final String DELIVERED = "DELIVERED";
    public static final String FULFILLED = "FULFILLED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile user;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(nullable = false)
    private int subtotal;
    @Column(name = "flash_discount", nullable = false)
    private int flashDiscount;
    @Column(name = "tier_discount", nullable = false)
    private int tierDiscount;
    @Column(name = "promo_discount", nullable = false)
    private int promoDiscount;
    @Column(nullable = false)
    private int total;

    @Column(name = "tier_code", length = 16)
    private String tierCode;
    @Column(name = "promo_code", length = 64)
    private String promoCode;
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "requires_shipping", nullable = false)
    private boolean requiresShipping;
    @Column(name = "shipping_name", length = 80)
    private String shippingName;
    @Column(name = "shipping_phone", length = 20)
    private String shippingPhone;
    @Column(name = "shipping_address", length = 200)
    private String shippingAddress;
    @Column(name = "shipping_city", length = 80)
    private String shippingCity;
    @Column(name = "shipping_county", length = 40)
    private String shippingCounty;
    @Column(name = "shipping_postal_code", length = 10)
    private String shippingPostalCode;
    @Column(length = 300)
    private String notes;
    @Column(name = "tracking_code", length = 64)
    private String trackingCode;
    @Column(name = "admin_note", length = 255)
    private String adminNote;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "processing_at")
    private Instant processingAt;
    @Column(name = "shipped_at")
    private Instant shippedAt;
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<PurchaseRecord> items = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public UserProfile getUser() { return user; }
    public void setUser(UserProfile user) { this.user = user; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getSubtotal() { return subtotal; }
    public void setSubtotal(int subtotal) { this.subtotal = subtotal; }
    public int getFlashDiscount() { return flashDiscount; }
    public void setFlashDiscount(int flashDiscount) { this.flashDiscount = flashDiscount; }
    public int getTierDiscount() { return tierDiscount; }
    public void setTierDiscount(int tierDiscount) { this.tierDiscount = tierDiscount; }
    public int getPromoDiscount() { return promoDiscount; }
    public void setPromoDiscount(int promoDiscount) { this.promoDiscount = promoDiscount; }
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public String getTierCode() { return tierCode; }
    public void setTierCode(String tierCode) { this.tierCode = tierCode; }
    public String getPromoCode() { return promoCode; }
    public void setPromoCode(String promoCode) { this.promoCode = promoCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public boolean isRequiresShipping() { return requiresShipping; }
    public void setRequiresShipping(boolean requiresShipping) { this.requiresShipping = requiresShipping; }
    public String getShippingName() { return shippingName; }
    public void setShippingName(String shippingName) { this.shippingName = shippingName; }
    public String getShippingPhone() { return shippingPhone; }
    public void setShippingPhone(String shippingPhone) { this.shippingPhone = shippingPhone; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public String getShippingCity() { return shippingCity; }
    public void setShippingCity(String shippingCity) { this.shippingCity = shippingCity; }
    public String getShippingCounty() { return shippingCounty; }
    public void setShippingCounty(String shippingCounty) { this.shippingCounty = shippingCounty; }
    public String getShippingPostalCode() { return shippingPostalCode; }
    public void setShippingPostalCode(String shippingPostalCode) { this.shippingPostalCode = shippingPostalCode; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getTrackingCode() { return trackingCode; }
    public void setTrackingCode(String trackingCode) { this.trackingCode = trackingCode; }
    public String getAdminNote() { return adminNote; }
    public void setAdminNote(String adminNote) { this.adminNote = adminNote; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<PurchaseRecord> getItems() { return items; }
    public Instant getProcessingAt() { return processingAt; }
    public void setProcessingAt(Instant processingAt) { this.processingAt = processingAt; }
    public Instant getShippedAt() { return shippedAt; }
    public void setShippedAt(Instant shippedAt) { this.shippedAt = shippedAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    /** Momentul in care comanda a ajuns intr-un anumit status (null daca nu a ajuns sau nu se cunoaste). */
    public Instant reachedAt(String s) {
        return switch (s) {
            case PLACED -> createdAt;
            case PROCESSING -> processingAt;
            case SHIPPED -> shippedAt;
            case DELIVERED -> deliveredAt;
            case FULFILLED -> FULFILLED.equals(status) ? createdAt : null;
            case CANCELLED -> cancelledAt;
            default -> null;
        };
    }
}
