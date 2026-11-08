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

    long countByUserIdAndType(String userId, String type);

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
            where t.createdAt >= :since and t.amount > 0 and t.type in :types
            group by t.user.guildId""")
    List<Object[]> guildEarningsSince(@Param("since") Instant since, @Param("types") Collection<String> types);
}
