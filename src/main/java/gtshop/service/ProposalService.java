package gtshop.service;

import gtshop.model.RewardProposal;
import gtshop.model.UserProfile;

import java.util.List;
import java.util.Map;

public interface ProposalService {
    List<Map<String, Object>> getProposals(UserProfile currentUser);
    RewardProposal createProposal(UserProfile user, String title, String category, String description);
    Map<String, Object> toggleVote(UserProfile user, String proposalId);
}
