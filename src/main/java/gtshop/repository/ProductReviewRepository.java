package gtshop.repository;

import gtshop.model.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, String> {
    List<ProductReview> findByRewardIdOrderByCreatedAtDesc(String rewardId);

    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.reward.id = :rewardId")
    Double getAverageRatingForReward(@Param("rewardId") String rewardId);

    long countByRewardId(String rewardId);

    List<ProductReview> findTop10ByOrderByCreatedAtDesc();
}
