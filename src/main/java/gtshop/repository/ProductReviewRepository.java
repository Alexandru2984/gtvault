package gtshop.repository;

import gtshop.model.ProductReview;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductReviewRepository extends JpaRepository<ProductReview, String> {
    Optional<ProductReview> findByRewardIdAndUserId(String rewardId, String userId);

    long countByUserIdAndHiddenFalse(String userId);
}
