package gtshop.repository;

import gtshop.model.PointTransaction;
import gtshop.model.UserProfile;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, String> {
    List<PointTransaction> findByUserOrderByCreatedAtDesc(UserProfile user);

    List<PointTransaction> findTop25ByUserOrderByCreatedAtDesc(UserProfile user);

    Page<PointTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<PointTransaction> findByUserIdAndTypeInOrderByCreatedAtDesc(String userId, Collection<String> types, Pageable pageable);

    long countByUserIdAndType(String userId, String type);

    @Query("select t.type, count(t) from PointTransaction t where t.user.id = :userId group by t.type")
    List<Object[]> countByTypeForUser(@Param("userId") String userId);

    /** Pozitia in clasamentul pe perioada: cati utilizatori au castigat strict mai mult XP. */
    @Query(value = """
            select count(*) from (
              select t.user_id from point_transactions t join users u on u.id = t.user_id
              where t.created_at >= :since and t.amount > 0 and t.type in (:types) and u.banned = false
              group by t.user_id having sum(t.amount) > :value) s""", nativeQuery = true)
    long countEarnersAbove(@Param("since") Instant since, @Param("types") Collection<String> types, @Param("value") long value);

    @Query("select coalesce(sum(t.amount), 0) from PointTransaction t where t.user.id = :userId and t.type = :type")
    long sumByUserAndType(@Param("userId") String userId, @Param("type") String type);

    /** Clasament pe perioada: suma creditelor care conteaza ca XP. */
    @Query("""
            select t.user.id, sum(t.amount) from PointTransaction t
            where t.createdAt >= :since and t.amount > 0 and t.type in :types and t.user.banned = false
            group by t.user.id order by sum(t.amount) desc""")
    List<Object[]> topEarnersSince(@Param("since") Instant since, @Param("types") Collection<String> types, Pageable pageable);

    @Query("""
            select coalesce(sum(t.amount), 0) from PointTransaction t
            where t.user.id = :userId and t.createdAt >= :since and t.amount > 0 and t.type in :types""")
    long earnedSince(@Param("userId") String userId, @Param("since") Instant since, @Param("types") Collection<String> types);

    @Query("""
            select t.user.guildId, sum(t.amount) from PointTransaction t
            where t.createdAt >= :since and t.amount > 0 and t.type in :types and t.user.banned = false
            group by t.user.guildId""")
    List<Object[]> guildEarningsSince(@Param("since") Instant since, @Param("types") Collection<String> types);
}
