package gtshop.repository;

import gtshop.model.UserProfile;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserProfile, String> {
    Optional<UserProfile> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<UserProfile> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);

    int countByReferredBy(String referredBy);

    List<UserProfile> findTop10ByOrderByActivityPointsDesc();

    /** Blocheaza randul utilizatorului (SELECT ... FOR UPDATE) pentru orice modificare de sold. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserProfile u where u.id = :id")
    Optional<UserProfile> findByIdForUpdate(@Param("id") String id);

    @Query("select u from UserProfile u where u.banned = false order by u.totalPointsEarned desc, u.createdAt asc")
    List<UserProfile> findTopByXp(Pageable pageable);

    @Query("select count(u) from UserProfile u where u.banned = false and u.totalPointsEarned > :xp")
    long countWithXpGreaterThan(@Param("xp") int xp);

    @Query("select u from UserProfile u where u.banned = false order by u.streakDays desc, u.longestStreak desc")
    List<UserProfile> findTopByStreak(Pageable pageable);

    List<UserProfile> findByEmailIn(List<String> emails);
}
