package gtshop.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Recenzia unui produs. Autorul (nume, avatar) se citeste mereu din profil, deci recenzia nu mai
 * afiseaza un nume vechi dupa ce utilizatorul si-l schimba. O singura recenzie per utilizator si produs.
 */
@Entity
@Table(name = "product_reviews")
public class ProductReview {
    @Id
    private String id;

    @Column(name = "reward_id", nullable = false)
    private String rewardId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 1000, nullable = false)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "helpful_count", nullable = false)
    private int helpfulCount;

    @Column(nullable = false)
    private boolean hidden;

    protected ProductReview() {
    }

    public ProductReview(String id, String rewardId, String userId, int rating, String comment, Instant createdAt) {
        this.id = id;
        this.rewardId = rewardId;
        this.userId = userId;
        this.rating = rating;
        this.comment = comment;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getRewardId() { return rewardId; }
    public String getUserId() { return userId; }
    public int getRating() { return rating; }
    public void setRating(int rating) { this.rating = rating; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public int getHelpfulCount() { return helpfulCount; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
}
