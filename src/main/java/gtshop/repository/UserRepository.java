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

    List<UserProfile> findTop20ByReferredByOrderByCreatedAtDesc(String referredBy);

    List<UserProfile> findByReferralCodeIsNull();

    @Query("select u.guildId, count(u), coalesce(sum(u.totalPointsEarned), 0) from UserProfile u where u.banned = false group by u.guildId")
    List<Object[]> guildTotals();

    @Query("select u from UserProfile u where u.banned = false and u.guildId = :guildId order by u.totalPointsEarned desc, u.createdAt asc")
    List<UserProfile> findTopInGuild(@Param("guildId") String guildId, Pageable pageable);

    /** Streak „viu”: ultima revendicare este ieri/azi sau zilele ratate sunt acoperite de freeze-uri. */
    String ALIVE_STREAK = """
            u.banned = false and u.streak_days > 0
            and u.last_daily_claim_date ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
            and to_date(u.last_daily_claim_date, 'YYYY-MM-DD') >= CAST(:today AS date) - 1 - u.streak_freezes""";

    @Query(value = "select u.* from users u where " + ALIVE_STREAK
            + " order by u.streak_days desc, u.longest_streak desc, u.created_at asc limit :limit", nativeQuery = true)
    List<UserProfile> findTopAliveStreaks(@Param("today") String today, @Param("limit") int limit);

    @Query(value = "select count(*) from users u where " + ALIVE_STREAK + " and u.streak_days > :value", nativeQuery = true)
    long countAliveStreaksAbove(@Param("today") String today, @Param("value") int value);
}
