package gtshop.model;

import java.time.Instant;

public class PurchaseRecord {
    private String id;
    private String rewardName;
    private int pointsSpent;
    private Instant purchaseDate;
    private String status;

    public PurchaseRecord() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRewardName() { return rewardName; }
    public void setRewardName(String rewardName) { this.rewardName = rewardName; }
    public int getPointsSpent() { return pointsSpent; }
    public void setPointsSpent(int pointsSpent) { this.pointsSpent = pointsSpent; }
    public Instant getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(Instant purchaseDate) { this.purchaseDate = purchaseDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
