package gtshop.repository;

import gtshop.model.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, String> {
    List<UserBadge> findByUserIdOrderByUnlockedAtDesc(String userId);
    Optional<UserBadge> findByUserIdAndBadgeCode(String userId, String badgeCode);
    boolean existsByUserIdAndBadgeCode(String userId, String badgeCode);
    List<UserBadge> findTop10ByOrderByUnlockedAtDesc();
}
