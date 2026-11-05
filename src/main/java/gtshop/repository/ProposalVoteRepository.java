package gtshop.repository;

import gtshop.model.ProposalVote;
import gtshop.model.RewardProposal;
import gtshop.model.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProposalVoteRepository extends JpaRepository<ProposalVote, String> {
    boolean existsByProposalAndUser(RewardProposal proposal, UserProfile user);
    Optional<ProposalVote> findByProposalAndUser(RewardProposal proposal, UserProfile user);
    List<ProposalVote> findByUser(UserProfile user);
}
