package gtshop.repository;

import gtshop.model.UserProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository
extends JpaRepository<UserProfile, String> {
    public Optional<UserProfile> findByEmail(String var1);

    public boolean existsByEmail(String var1);
}

