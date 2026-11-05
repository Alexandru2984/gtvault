package gtshop.repository;

import gtshop.model.UserProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserProfile, String> {
    Optional<UserProfile> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserProfile> findByReferralCode(String referralCode);

    int countByReferredBy(String referredBy);

    List<UserProfile> findTop10ByOrderByActivityPointsDesc();
}
