package gtshop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class UserProfile {
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    @Id
    private String id;
    private String name;
    private String avatar;
    @Column(name = "activity_points", nullable = false)
    private int activityPoints;
    @Column(name = "streak_days", nullable = false)
    private int streakDays;
    private String lastDailyClaimDate;
    private String lastQuizDate;
    private String referralCode;
    private String referredBy;
    @Column(name = "total_points_earned", nullable = false)
    private int totalPointsEarned;
    private String guildId = "JAVA_KNIGHTS";
    private String lastFortuneDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(nullable = false, length = 16)
    private String role = ROLE_USER;
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;
    @Column(nullable = false)
    private boolean banned;
    @Column(name = "longest_streak", nullable = false)
    private int longestStreak;
    @Column(name = "streak_freezes", nullable = false)
    private int streakFreezes;
    @Column(name = "last_free_spin_date", length = 16)
    private String lastFreeSpinDate;

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

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (role == null) {
            role = ROLE_USER;
        }
    }

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public String getId() { return this.id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return this.name; }
    public void setName(String name) { this.name = name; }

    public String getAvatar() { return this.avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public int getActivityPoints() { return this.activityPoints; }
    public void setActivityPoints(int activityPoints) { this.activityPoints = activityPoints; }

    public int getStreakDays() { return this.streakDays; }
    public void setStreakDays(int streakDays) { this.streakDays = streakDays; }

    public String getLastDailyClaimDate() { return this.lastDailyClaimDate; }
    public void setLastDailyClaimDate(String lastDailyClaimDate) { this.lastDailyClaimDate = lastDailyClaimDate; }

    public String getLastQuizDate() { return this.lastQuizDate; }
    public void setLastQuizDate(String lastQuizDate) { this.lastQuizDate = lastQuizDate; }

    public String getEmail() { return this.email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return this.password; }
    public void setPassword(String password) { this.password = password; }

    public List<CartItem> getCartItems() { return this.cartItems; }
    public void setCartItems(List<CartItem> cartItems) { this.cartItems = cartItems; }

    public List<PurchaseRecord> getPurchases() { return this.purchases; }
    public void setPurchases(List<PurchaseRecord> purchases) { this.purchases = purchases; }

    public String getReferralCode() { return this.referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

    public String getReferredBy() { return this.referredBy; }
    public void setReferredBy(String referredBy) { this.referredBy = referredBy; }

    public int getTotalPointsEarned() { return this.totalPointsEarned; }
    public void setTotalPointsEarned(int totalPointsEarned) { this.totalPointsEarned = totalPointsEarned; }

    public String getGuildId() { return this.guildId; }
    public void setGuildId(String guildId) { this.guildId = guildId; }

    public String getLastFortuneDate() { return this.lastFortuneDate; }
    public void setLastFortuneDate(String lastFortuneDate) { this.lastFortuneDate = lastFortuneDate; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public int getTokenVersion() { return tokenVersion; }
    public void setTokenVersion(int tokenVersion) { this.tokenVersion = tokenVersion; }

    public boolean isBanned() { return banned; }
    public void setBanned(boolean banned) { this.banned = banned; }

    public int getLongestStreak() { return longestStreak; }
    public void setLongestStreak(int longestStreak) { this.longestStreak = longestStreak; }

    public int getStreakFreezes() { return streakFreezes; }
    public void setStreakFreezes(int streakFreezes) { this.streakFreezes = streakFreezes; }

    public String getLastFreeSpinDate() { return lastFreeSpinDate; }
    public void setLastFreeSpinDate(String lastFreeSpinDate) { this.lastFreeSpinDate = lastFreeSpinDate; }
}
