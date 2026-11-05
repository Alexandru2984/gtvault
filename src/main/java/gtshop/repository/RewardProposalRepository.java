package gtshop.repository;

import gtshop.model.RewardProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RewardProposalRepository extends JpaRepository<RewardProposal, String> {
    List<RewardProposal> findAllByOrderByVotesCountDescCreatedAtDesc();
}
