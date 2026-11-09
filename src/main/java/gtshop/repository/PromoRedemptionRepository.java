package gtshop.repository;

import gtshop.model.PromoRedemption;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PromoRedemptionRepository extends JpaRepository<PromoRedemption, Long> {
    boolean existsByPromoIdAndUserId(String promoId, String userId);

    Optional<PromoRedemption> findByOrderId(String orderId);
}
