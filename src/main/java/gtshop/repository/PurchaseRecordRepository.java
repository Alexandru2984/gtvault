package gtshop.repository;

import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseRecordRepository extends JpaRepository<PurchaseRecord, String> {
    List<PurchaseRecord> findByUserOrderByPurchaseDateDesc(UserProfile user);

    Optional<PurchaseRecord> findByVoucherCode(String voucherCode);

    boolean existsByVoucherCode(String voucherCode);

    /** Cumparare verificata: utilizatorul are o comanda neanulata care contine produsul. */
    @Query("""
            select count(p) > 0 from PurchaseRecord p
            where p.user.id = :userId and p.rewardId = :rewardId and p.order.status <> 'CANCELLED'""")
    boolean hasPurchased(@Param("userId") String userId, @Param("rewardId") String rewardId);

    /** Unitati cumparate de utilizator la pretul unui flash deal (comenzi neanulate). */
    @Query("""
            select coalesce(sum(p.flashUnits), 0) from PurchaseRecord p
            where p.user.id = :userId and p.flashDealId = :dealId and p.order.status <> 'CANCELLED'""")
    long flashUnitsBought(@Param("userId") String userId, @Param("dealId") String dealId);
}
