package gtshop.service;

import gtshop.model.ProductReview;
import gtshop.model.Reward;
import gtshop.model.UserProfile;
import gtshop.repository.ProductReviewRepository;
import gtshop.repository.PurchaseRecordRepository;
import gtshop.repository.RewardRepository;
import gtshop.repository.UserBadgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class ProductReviewServiceImpl implements ProductReviewService {
    private final ProductReviewRepository reviewRepository;
    private final RewardRepository rewardRepository;
    private final PurchaseRecordRepository purchaseRecordRepository;
    private final UserBadgeRepository badgeRepository;

    public ProductReviewServiceImpl(ProductReviewRepository reviewRepository,
                                    RewardRepository rewardRepository,
                                    PurchaseRecordRepository purchaseRecordRepository,
                                    UserBadgeRepository badgeRepository) {
        this.reviewRepository = reviewRepository;
        this.rewardRepository = rewardRepository;
        this.purchaseRecordRepository = purchaseRecordRepository;
        this.badgeRepository = badgeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getReviewsSummary(String rewardId) {
        List<ProductReview> reviews = this.reviewRepository.findByRewardIdOrderByCreatedAtDesc(rewardId);
        Double avg = this.reviewRepository.getAverageRatingForReward(rewardId);
        long total = this.reviewRepository.countByRewardId(rewardId);

        double roundedAvg = (avg != null) ? Math.round(avg * 10.0) / 10.0 : 5.0;

        Map<String, Object> resp = new HashMap<>();
        resp.put("rewardId", rewardId);
        resp.put("averageRating", roundedAvg);
        resp.put("totalReviews", total);
        resp.put("reviews", reviews);
        return resp;
    }

    @Override
    public ProductReview addReview(String rewardId, UserProfile user, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Ratingul trebuie să fie între 1 și 5!");
        }
        if (comment == null || comment.trim().isEmpty()) {
            throw new IllegalArgumentException("Comentariul nu poate fi gol!");
        }

        Reward reward = this.rewardRepository.findById(rewardId)
                .orElseThrow(() -> new IllegalArgumentException("Produsul nu a fost găsit!"));

        ProductReview review = new ProductReview(
                "rev-" + UUID.randomUUID().toString().substring(0, 8),
                reward,
                user,
                user.getName() != null ? user.getName() : "Membru Generația Tech",
                user.getAvatar() != null ? user.getAvatar() : "images/user-avatar.svg",
                rating,
                comment.trim()
        );

        return this.reviewRepository.save(review);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentCommunityActivity() {
        List<Map<String, Object>> feed = new ArrayList<>();

        // Add recent reviews
        this.reviewRepository.findTop10ByOrderByCreatedAtDesc().forEach(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("type", "REVIEW");
            item.put("title", r.getUserName() + " a lăsat o recenzie de " + r.getRating() + " ⭐");
            item.put("detail", "\"" + (r.getComment().length() > 60 ? r.getComment().substring(0, 57) + "..." : r.getComment()) + "\"");
            item.put("icon", "⭐");
            item.put("timestamp", r.getCreatedAt());
            feed.add(item);
        });

        // Add recent unlocked badges
        this.badgeRepository.findTop10ByOrderByUnlockedAtDesc().forEach(b -> {
            Map<String, Object> item = new HashMap<>();
            item.put("type", "BADGE");
            item.put("title", "Insingnă nouă deblocată!");
            item.put("detail", b.getBadgeTitle() + " - " + b.getBadgeDescription());
            item.put("icon", b.getBadgeIcon());
            item.put("timestamp", b.getUnlockedAt());
            feed.add(item);
        });

        // Sort by timestamp desc
        feed.sort((a, b) -> {
            Instant t1 = (Instant) a.get("timestamp");
            Instant t2 = (Instant) b.get("timestamp");
            if (t1 == null || t2 == null) return 0;
            return t2.compareTo(t1);
        });

        return feed.size() > 10 ? feed.subList(0, 10) : feed;
    }
}
