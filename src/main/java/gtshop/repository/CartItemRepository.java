package gtshop.repository;

import gtshop.model.CartItem;
import gtshop.model.UserProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CartItemRepository
extends JpaRepository<CartItem, Long> {
    public List<CartItem> findByUser(UserProfile var1);

    public Optional<CartItem> findByUserAndRewardId(UserProfile var1, String var2);

    public void deleteByUser(UserProfile var1);
}

