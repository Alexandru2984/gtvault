package gtshop.repository;

import gtshop.model.ProposalVote;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalVoteRepository extends JpaRepository<ProposalVote, String> {
    Optional<ProposalVote> findByProposalIdAndUserId(String proposalId, String userId);

    @Query("select v.proposal.id from ProposalVote v where v.user.id = :userId and v.active = true")
    List<String> findActiveProposalIdsByUser(@Param("userId") String userId);
}
