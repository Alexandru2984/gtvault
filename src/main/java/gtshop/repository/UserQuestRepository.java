package gtshop.repository;

import gtshop.model.UserProfile;
import gtshop.model.UserQuest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserQuestRepository extends JpaRepository<UserQuest, String> {
    List<UserQuest> findByUserAndQuestDate(UserProfile user, String questDate);
    Optional<UserQuest> findByUserAndQuestKeyAndQuestDate(UserProfile user, String questKey, String questDate);
    Optional<UserQuest> findByIdAndUser(String id, UserProfile user);
}
