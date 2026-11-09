package gtshop.repository;

import gtshop.model.Reward;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RewardRepository extends JpaRepository<Reward, String> {
    @Query("SELECT r FROM Reward r ORDER BY r.category, r.id")
    List<Reward> findAllOrdered();

    @Query("SELECT r FROM Reward r WHERE r.active = true ORDER BY r.category, r.price, r.id")
    List<Reward> findActive();

    @Query("SELECT DISTINCT r.category FROM Reward r WHERE r.active = true AND r.category IS NOT NULL ORDER BY r.category")
    List<String> findActiveCategories();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reward r WHERE r.id IN :ids ORDER BY r.id")
    List<Reward> findAllByIdForUpdate(@Param("ids") Collection<String> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reward r WHERE r.id = :id")
    Optional<Reward> findByIdForUpdate(@Param("id") String id);

    /** Popularitate: unitati vandute (comenzi neanulate) de la o data. */
    @Query("""
            SELECT p.rewardId, SUM(p.quantity) FROM PurchaseRecord p
            WHERE p.purchaseDate >= :since AND p.order.status <> 'CANCELLED' AND p.rewardId IS NOT NULL
            GROUP BY p.rewardId""")
    List<Object[]> unitsSoldSince(@Param("since") Instant since);
}
