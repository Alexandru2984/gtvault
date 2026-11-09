package gtshop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/** O linie dintr-o comanda (tabela istorica {@code purchase_records}). */
@Entity
@Table(name = "purchase_records")
public class PurchaseRecord {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @JsonIgnore
    private ShopOrder order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private UserProfile user;

    @Column(name = "reward_id")
    private String rewardId;
    @Column(name = "reward_name")
    private String rewardName;
    @Column(nullable = false)
    private int quantity = 1;
    @Column(name = "unit_price")
    private Integer unitPrice;
    @Column(name = "points_spent", nullable = false)
    private int pointsSpent;
    @Column(nullable = false, length = 16)
    private String fulfillment = Reward.FULFILLMENT_DIGITAL;
    @Column(name = "flash_deal_id", length = 32)
    private String flashDealId;
    @Column(name = "flash_units", nullable = false)
    private int flashUnits;
    @Column(name = "purchase_date")
    private Instant purchaseDate;
    private String status;
    @Column(name = "voucher_code")
    private String voucherCode;
    @Column(name = "redeemed_at")
    private Instant redeemedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public ShopOrder getOrder() { return order; }
    public void setOrder(ShopOrder order) { this.order = order; }
    public UserProfile getUser() { return user; }
    public void setUser(UserProfile user) { this.user = user; }
    public String getRewardId() { return rewardId; }
    public void setRewardId(String rewardId) { this.rewardId = rewardId; }
    public String getRewardName() { return rewardName; }
    public void setRewardName(String rewardName) { this.rewardName = rewardName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public Integer getUnitPrice() { return unitPrice; }
    public void setUnitPrice(Integer unitPrice) { this.unitPrice = unitPrice; }
    public int getPointsSpent() { return pointsSpent; }
    public void setPointsSpent(int pointsSpent) { this.pointsSpent = pointsSpent; }
    public String getFulfillment() { return fulfillment; }
    public void setFulfillment(String fulfillment) { this.fulfillment = fulfillment; }
    public String getFlashDealId() { return flashDealId; }
    public void setFlashDealId(String flashDealId) { this.flashDealId = flashDealId; }
    public int getFlashUnits() { return flashUnits; }
    public void setFlashUnits(int flashUnits) { this.flashUnits = flashUnits; }
    public Instant getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(Instant purchaseDate) { this.purchaseDate = purchaseDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
    public Instant getRedeemedAt() { return redeemedAt; }
    public void setRedeemedAt(Instant redeemedAt) { this.redeemedAt = redeemedAt; }
}
