package gtshop.service;

import gtshop.model.ProductReview;
import gtshop.model.UserProfile;
import java.util.List;
import java.util.Map;

public interface ProductReviewService {
    Map<String, Object> getReviewsSummary(String rewardId);
    ProductReview addReview(String rewardId, UserProfile user, int rating, String comment);
    List<Map<String, Object>> getRecentCommunityActivity();
}
