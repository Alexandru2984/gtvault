package gtshop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_badges", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "badge_code"})
})
public class UserBadge {
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private UserProfile user;

    @Column(nullable = false)
    private String badgeCode;

    @Column(nullable = false)
    private String badgeTitle;

    private String badgeDescription;

    @Column(nullable = false)
    private String badgeIcon;

    private Instant unlockedAt;

    public UserBadge() {
    }

    public UserBadge(String id, UserProfile user, String badgeCode, String badgeTitle, String badgeDescription, String badgeIcon) {
        this.id = id;
        this.user = user;
        this.badgeCode = badgeCode;
        this.badgeTitle = badgeTitle;
        this.badgeDescription = badgeDescription;
        this.badgeIcon = badgeIcon;
        this.unlockedAt = Instant.now();
    }

    public String getId() {
        return this.id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UserProfile getUser() {
        return this.user;
    }

    public void setUser(UserProfile user) {
        this.user = user;
    }

    public String getBadgeCode() {
        return this.badgeCode;
    }

    public void setBadgeCode(String badgeCode) {
        this.badgeCode = badgeCode;
    }

    public String getBadgeTitle() {
        return this.badgeTitle;
    }

    public void setBadgeTitle(String badgeTitle) {
        this.badgeTitle = badgeTitle;
    }

    public String getBadgeDescription() {
        return this.badgeDescription;
    }

    public void setBadgeDescription(String badgeDescription) {
        this.badgeDescription = badgeDescription;
    }

    public String getBadgeIcon() {
        return this.badgeIcon;
    }

    public void setBadgeIcon(String badgeIcon) {
        this.badgeIcon = badgeIcon;
    }

    public Instant getUnlockedAt() {
        return this.unlockedAt;
    }

    public void setUnlockedAt(Instant unlockedAt) {
        this.unlockedAt = unlockedAt;
    }
}
