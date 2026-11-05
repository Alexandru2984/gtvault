package gtshop.repository;

import gtshop.model.PointTransaction;
import gtshop.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, String> {
    List<PointTransaction> findByUserOrderByCreatedAtDesc(UserProfile user);
    List<PointTransaction> findTop25ByUserOrderByCreatedAtDesc(UserProfile user);
}
