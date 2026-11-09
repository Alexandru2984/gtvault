package gtshop.repository;

import gtshop.model.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @Query("select c from CartItem c where c.user.id = :userId order by c.addedAt, c.id")
    List<CartItem> findForUser(@Param("userId") String userId);

    @Query("select c from CartItem c where c.user.id = :userId and c.rewardId = :rewardId")
    Optional<CartItem> findLine(@Param("userId") String userId, @Param("rewardId") String rewardId);

    @Query("select count(c) from CartItem c where c.user.id = :userId")
    long countForUser(@Param("userId") String userId);

    @Modifying
    @Query("delete from CartItem c where c.user.id = :userId")
    int clear(@Param("userId") String userId);
}
