package gtshop.repository;

import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseRecordRepository
extends JpaRepository<PurchaseRecord, String> {
    public List<PurchaseRecord> findByUserOrderByPurchaseDateDesc(UserProfile var1);
}

