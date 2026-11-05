package gtshop.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_quests")
public class UserQuest {

    @Id
    private String id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile user;

    @Column(name = "quest_key", nullable = false, length = 64)
    private String questKey;

    @Column(name = "quest_type", nullable = false, length = 16)
    private String questType; // DAILY or WEEKLY

    @Column(nullable = false, length = 128)
    private String title;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "reward_points", nullable = false)
    private int rewardPoints;

    @Column(name = "current_progress", nullable = false)
    private int currentProgress;

    @Column(name = "target_progress", nullable = false)
    private int targetProgress;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false)
    private boolean claimed;

    @Column(name = "quest_date", nullable = false, length = 16)
    private String questDate;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UserQuest() {
    }

    public UserQuest(String id, UserProfile user, String questKey, String questType, String title, String description,
                     int rewardPoints, int currentProgress, int targetProgress, boolean completed, boolean claimed,
                     String questDate, Instant createdAt) {
        this.id = id;
        this.user = user;
        this.questKey = questKey;
        this.questType = questType;
        this.title = title;
        this.description = description;
        this.rewardPoints = rewardPoints;
        this.currentProgress = currentProgress;
        this.targetProgress = targetProgress;
        this.completed = completed;
        this.claimed = claimed;
        this.questDate = questDate;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UserProfile getUser() {
        return user;
    }

    public void setUser(UserProfile user) {
        this.user = user;
    }

    public String getQuestKey() {
        return questKey;
    }

    public void setQuestKey(String questKey) {
        this.questKey = questKey;
    }

    public String getQuestType() {
        return questType;
    }

    public void setQuestType(String questType) {
        this.questType = questType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getRewardPoints() {
        return rewardPoints;
    }

    public void setRewardPoints(int rewardPoints) {
        this.rewardPoints = rewardPoints;
    }

    public int getCurrentProgress() {
        return currentProgress;
    }

    public void setCurrentProgress(int currentProgress) {
        this.currentProgress = currentProgress;
    }

    public int getTargetProgress() {
        return targetProgress;
    }

    public void setTargetProgress(int targetProgress) {
        this.targetProgress = targetProgress;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }

    public String getQuestDate() {
        return questDate;
    }

    public void setQuestDate(String questDate) {
        this.questDate = questDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
