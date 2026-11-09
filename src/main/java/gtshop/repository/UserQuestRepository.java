package gtshop.repository;

import gtshop.model.UserQuest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserQuestRepository extends JpaRepository<UserQuest, String> {
    @Query("select q from UserQuest q where q.user.id = :userId and q.questType = :type and q.questDate = :date order by q.createdAt, q.id")
    List<UserQuest> findForPeriod(@Param("userId") String userId, @Param("type") String type, @Param("date") String date);

    Optional<UserQuest> findByIdAndUserId(String id, String userId);
}
