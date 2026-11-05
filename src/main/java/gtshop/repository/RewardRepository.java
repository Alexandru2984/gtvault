package gtshop.repository;

import gtshop.model.Reward;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RewardRepository
extends JpaRepository<Reward, String> {
    @Query(value="SELECT r FROM Reward r ORDER BY r.category, r.id")
    public List<Reward> findAllOrdered();

    @Query(value="SELECT r FROM Reward r WHERE LOWER(r.category) = LOWER(:category) ORDER BY r.id")
    public List<Reward> findByCategoryIgnoreCaseOrdered(@Param(value="category") String var1);

    public List<Reward> findByCategoryIgnoreCase(String var1);

    @Query(value="SELECT r FROM Reward r WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.description) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.fullDescription) LIKE LOWER(CONCAT('%', :query, '%')) ORDER BY r.category, r.id")
    public List<Reward> searchByQuery(@Param(value="query") String var1);

    @Query(value="SELECT r FROM Reward r WHERE (LOWER(r.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.description) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(r.fullDescription) LIKE LOWER(CONCAT('%', :query, '%'))) AND LOWER(r.category) = LOWER(:category) ORDER BY r.id")
    public List<Reward> searchByQueryAndCategory(@Param(value="query") String var1, @Param(value="category") String var2);
}

