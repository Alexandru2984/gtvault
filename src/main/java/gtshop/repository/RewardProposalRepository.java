package gtshop.repository;

import gtshop.model.RewardProposal;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RewardProposalRepository extends JpaRepository<RewardProposal, String> {
    List<RewardProposal> findAllByOrderByVotesCountDescCreatedAtDesc();

    List<RewardProposal> findAllByOrderByCreatedAtDesc();

    long countByUserId(String userId);

    @Query("select count(p) > 0 from RewardProposal p where lower(p.title) = lower(:title) and p.status <> 'REJECTED'")
    boolean existsActiveWithTitle(@Param("title") String title);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from RewardProposal p where p.id = :id")
    Optional<RewardProposal> findByIdForUpdate(@Param("id") String id);
}
