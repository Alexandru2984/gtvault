package gtshop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import gtshop.model.UserProfile;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name="purchase_records")
public class PurchaseRecord {
    @Id
    private String id;
    private String rewardName;
    private int pointsSpent;
    private Instant purchaseDate;
    private String status;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="user_id")
    @JsonIgnore
    private UserProfile user;

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRewardName() {
        return this.rewardName;
    }

    public void setRewardName(String rewardName) {
        this.rewardName = rewardName;
    }

    public int getPointsSpent() {
        return this.pointsSpent;
    }

    public void setPointsSpent(int pointsSpent) {
        this.pointsSpent = pointsSpent;
    }

    public Instant getPurchaseDate() {
        return this.purchaseDate;
    }

    public void setPurchaseDate(Instant purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public String getStatus() {
        return this.status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public UserProfile getUser() {
        return this.user;
    }

    public void setUser(UserProfile user) {
        this.user = user;
    }
}

