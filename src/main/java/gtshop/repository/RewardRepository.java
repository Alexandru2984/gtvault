package gtshop.repository;

import gtshop.model.Reward;
import jakarta.persistence.LockModeType;
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

    @Query("SELECT r FROM Reward r WHERE LOWER(r.category) = LOWER(:category) ORDER BY r.id")
    List<Reward> findByCategoryIgnoreCaseOrdered(@Param("category") String category);

    List<Reward> findByCategoryIgnoreCase(String category);

    @Query("SELECT r FROM Reward r WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.description) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.fullDescription) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY r.category, r.id")
    List<Reward> searchByQuery(@Param("query") String query);

    @Query("SELECT r FROM Reward r WHERE (LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.description) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.fullDescription) LIKE LOWER(CONCAT('%', :query, '%'))) AND LOWER(r.category) = LOWER(:category) ORDER BY r.id")
    List<Reward> searchByQueryAndCategory(@Param("query") String query, @Param("category") String category);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reward r WHERE r.id = :id")
    Optional<Reward> findByIdForUpdate(@Param("id") String id);
}
