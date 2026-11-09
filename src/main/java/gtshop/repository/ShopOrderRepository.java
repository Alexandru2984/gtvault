package gtshop.repository;

import gtshop.model.ShopOrder;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ShopOrderRepository extends JpaRepository<ShopOrder, String> {
    @Query("select o from ShopOrder o where o.user.id = :userId order by o.createdAt desc, o.id desc")
    Page<ShopOrder> findForUser(@Param("userId") String userId, Pageable pageable);

    @Query("select o from ShopOrder o where o.id = :id and o.user.id = :userId")
    Optional<ShopOrder> findForUser(@Param("id") String id, @Param("userId") String userId);

    @Query("select o from ShopOrder o where o.user.id = :userId and o.idempotencyKey = :key")
    Optional<ShopOrder> findByIdempotencyKey(@Param("userId") String userId, @Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ShopOrder o where o.id = :id")
    Optional<ShopOrder> findByIdForUpdate(@Param("id") String id);

    @Query("select o from ShopOrder o where o.user.id = :userId and o.requiresShipping = true order by o.createdAt desc")
    List<ShopOrder> findShippingOrders(@Param("userId") String userId, Pageable pageable);

    @Query("select count(o) from ShopOrder o where o.user.id = :userId and o.status <> 'CANCELLED'")
    long countActiveForUser(@Param("userId") String userId);
}
