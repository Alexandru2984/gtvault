package gtshop.service;

import gtshop.model.ProposalVote;
import gtshop.model.RewardProposal;
import gtshop.model.UserProfile;
import gtshop.repository.ProposalVoteRepository;
import gtshop.repository.RewardProposalRepository;
import gtshop.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProposalServiceImpl implements ProposalService {

    private final RewardProposalRepository proposalRepository;
    private final ProposalVoteRepository voteRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;

    public ProposalServiceImpl(RewardProposalRepository proposalRepository,
                               ProposalVoteRepository voteRepository,
                               UserRepository userRepository,
                               TransactionService transactionService) {
        this.proposalRepository = proposalRepository;
        this.voteRepository = voteRepository;
        this.userRepository = userRepository;
        this.transactionService = transactionService;
    }

    @Override
    public List<Map<String, Object>> getProposals(UserProfile currentUser) {
        List<RewardProposal> list = proposalRepository.findAllByOrderByVotesCountDescCreatedAtDesc();
        if (list.isEmpty() && currentUser != null) {
            initSampleProposals(currentUser);
            list = proposalRepository.findAllByOrderByVotesCountDescCreatedAtDesc();
        }

        Set<String> votedProposalIds = new HashSet<>();
        if (currentUser != null) {
            List<ProposalVote> userVotes = voteRepository.findByUser(currentUser);
            votedProposalIds = userVotes.stream()
                .map(v -> v.getProposal().getId())
                .collect(Collectors.toSet());
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (RewardProposal p : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("authorName", p.getAuthorName());
            map.put("title", p.getTitle());
            map.put("category", p.getCategory());
            map.put("description", p.getDescription());
            map.put("votesCount", p.getVotesCount());
            map.put("status", p.getStatus());
            map.put("createdAt", p.getCreatedAt());
            map.put("hasVoted", votedProposalIds.contains(p.getId()));
            result.add(map);
        }

        return result;
    }

    private void initSampleProposals(UserProfile defaultUser) {
        RewardProposal p1 = new RewardProposal(
            "prop-1", defaultUser, "Mihai Mentor",
            "Voucher Reducere Certificare AWS / Cloud", "Premii exclusive",
            "Acoperire parțială pentru taxa examenului oficial de certificare AWS Cloud Practitioner.",
            32, "APPROVED", Instant.now().minusSeconds(86400 * 3)
        );
        RewardProposal p2 = new RewardProposal(
            "prop-2", defaultUser, "Elena Frontend",
            "Hanorac Generația Tech — Limited Edition", "Premii fizice",
            "Hanorac premium din bumbac cu glugă și logo brodat GT, mărimi S-XXL.",
            27, "PENDING", Instant.now().minusSeconds(86400 * 2)
        );
        RewardProposal p3 = new RewardProposal(
            "prop-3", defaultUser, "Radu Backend",
            "Licență JetBrains All Products (30 Zile)", "Premii digitale",
            "Acces complet la IntelliJ IDEA Ultimate, WebStorm, DataGrip pentru proiectele din track.",
            19, "PENDING", Instant.now().minusSeconds(86400)
        );
        proposalRepository.saveAll(List.of(p1, p2, p3));
    }

    @Override
    public RewardProposal createProposal(UserProfile user, String title, String category, String description) {
        if (user == null) {
            throw new IllegalArgumentException("Trebuie să fii autentificat pentru a propune o recompensă.");
        }
        if (title == null || title.trim().length() < 3) {
            throw new IllegalArgumentException("Titlul propunerii trebuie să aibă minim 3 caractere.");
        }
        if (description == null || description.trim().length() < 10) {
            throw new IllegalArgumentException("Descrierea propunerii trebuie să aibă minim 10 caractere.");
        }

        String id = "prop-" + UUID.randomUUID().toString().substring(0, 8);
        RewardProposal prop = new RewardProposal(
            id, user, user.getName(), title.trim(),
            category != null ? category.trim() : "Premii digitale",
            description.trim(), 1, "PENDING", Instant.now()
        );
        RewardProposal saved = proposalRepository.save(prop);

        // Author automatically casts the first vote
        ProposalVote vote = new ProposalVote("vote-" + UUID.randomUUID().toString().substring(0, 8), saved, user, Instant.now());
        voteRepository.save(vote);

        // Reward author with +15 AP
        user.setActivityPoints(user.getActivityPoints() + 15);
        userRepository.save(user);
        transactionService.recordTransaction(user, "COMMUNITY_CONTRIBUTION", 15, user.getActivityPoints(),
            "Bonus propunere recompensă nouă: " + prop.getTitle());

        return saved;
    }

    @Override
    public Map<String, Object> toggleVote(UserProfile user, String proposalId) {
        if (user == null) {
            throw new IllegalArgumentException("Trebuie să fii autentificat pentru a vota.");
        }

        RewardProposal prop = proposalRepository.findById(proposalId)
            .orElseThrow(() -> new IllegalArgumentException("Propunerea nu a fost găsită."));

        Optional<ProposalVote> existingVote = voteRepository.findByProposalAndUser(prop, user);
        Map<String, Object> res = new HashMap<>();

        if (existingVote.isPresent()) {
            voteRepository.delete(existingVote.get());
            prop.setVotesCount(Math.max(0, prop.getVotesCount() - 1));
            proposalRepository.save(prop);
            res.put("hasVoted", false);
            res.put("message", "Votul tău a fost retras.");
        } else {
            ProposalVote newVote = new ProposalVote("vote-" + UUID.randomUUID().toString().substring(0, 8), prop, user, Instant.now());
            voteRepository.save(newVote);
            prop.setVotesCount(prop.getVotesCount() + 1);
            proposalRepository.save(prop);

            // Award +5 AP for active voting participation
            user.setActivityPoints(user.getActivityPoints() + 5);
            userRepository.save(user);
            transactionService.recordTransaction(user, "VOTE_REWARD", 5, user.getActivityPoints(),
                "Vot acordat pentru propunerea: " + prop.getTitle());

            res.put("hasVoted", true);
            res.put("message", "Mulțumim pentru vot! Ai primit +5 AP.");
        }

        res.put("success", true);
        res.put("votesCount", prop.getVotesCount());
        res.put("newBalance", user.getActivityPoints());
        return res;
    }
}
