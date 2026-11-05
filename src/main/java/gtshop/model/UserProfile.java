package gtshop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class UserProfile {
    @Id
    private String id;
    private String name;
    private String avatar;
    private int activityPoints;
    private int streakDays;
    private String lastDailyClaimDate;
    private String lastQuizDate;
    private String referralCode;
    private String referredBy;
    private int totalPointsEarned;

    @JsonIgnore
    private String email;
    @JsonIgnore
    private String password;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = {CascadeType.ALL}, orphanRemoval = true)
    private List<CartItem> cartItems = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = {CascadeType.ALL}, orphanRemoval = true)
    private List<PurchaseRecord> purchases = new ArrayList<>();

    public UserProfile() {
    }

    public UserProfile(String id, String name, String avatar, int activityPoints) {
        this.id = id;
        this.name = name;
        this.avatar = avatar;
        this.activityPoints = activityPoints;
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

    public String getAvatar() {
        return this.avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public int getActivityPoints() {
        return this.activityPoints;
    }

    public void setActivityPoints(int activityPoints) {
        this.activityPoints = activityPoints;
    }

    public int getStreakDays() {
        return this.streakDays;
    }

    public void setStreakDays(int streakDays) {
        this.streakDays = streakDays;
    }

    public String getLastDailyClaimDate() {
        return this.lastDailyClaimDate;
    }

    public void setLastDailyClaimDate(String lastDailyClaimDate) {
        this.lastDailyClaimDate = lastDailyClaimDate;
    }

    public String getLastQuizDate() {
        return this.lastQuizDate;
    }

    public void setLastQuizDate(String lastQuizDate) {
        this.lastQuizDate = lastQuizDate;
    }

    public String getEmail() {
        return this.email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return this.password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<CartItem> getCartItems() {
        return this.cartItems;
    }

    public void setCartItems(List<CartItem> cartItems) {
        this.cartItems = cartItems;
    }

    public List<PurchaseRecord> getPurchases() {
        return this.purchases;
    }

    public void setPurchases(List<PurchaseRecord> purchases) {
        this.purchases = purchases;
    }

    public String getReferralCode() {
        return this.referralCode;
    }

    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }

    public String getReferredBy() {
        return this.referredBy;
    }

    public void setReferredBy(String referredBy) {
        this.referredBy = referredBy;
    }

    public int getTotalPointsEarned() {
        return this.totalPointsEarned;
    }

    public void setTotalPointsEarned(int totalPointsEarned) {
        this.totalPointsEarned = totalPointsEarned;
    }
}
