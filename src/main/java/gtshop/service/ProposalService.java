package gtshop.service;

import gtshop.game.Badge;
import gtshop.game.Economy;
import gtshop.game.QuestCatalog.Action;
import gtshop.model.ProposalVote;
import gtshop.model.RewardProposal;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.ProposalVoteRepository;
import gtshop.repository.RewardProposalRepository;
import gtshop.util.Ids;
import gtshop.util.InputSanitizer;
import gtshop.web.ApiException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Panoul de propuneri al comunitatii. Votul se poate retrage si redepune oricand, dar AP-ul se acorda
 * o singura data per propunere (voturile retrase raman in tabela ca inactive) si cu limita zilnica.
 */
@Service
public class ProposalService {
    public static final List<String> CATEGORIES = List.of("Premii fizice", "Premii digitale", "Premii exclusive", "Experiențe");
    public static final Set<String> STATUSES = Set.of("PENDING", "APPROVED", "IMPLEMENTED", "REJECTED");
    private static final Set<String> VOTABLE = Set.of("PENDING", "APPROVED");
    private static final List<Integer> VOTE_MILESTONES = List.of(10, 25, 50, 100);
    static final String PROPOSALS_TODAY = "PROPOSALS";
    static final String REWARDED_VOTES = "VOTES_REWARDED";

    private final RewardProposalRepository proposals;
    private final ProposalVoteRepository votes;
    private final WalletService wallet;
    private final DailyCounterService counters;
    private final BadgeService badges;
    private final QuestService quests;
    private final NotificationService notifications;
    private final TimeService time;

    public ProposalService(RewardProposalRepository proposals, ProposalVoteRepository votes, WalletService wallet,
                           DailyCounterService counters, BadgeService badges, QuestService quests,
                           NotificationService notifications, TimeService time) {
        this.proposals = proposals;
        this.votes = votes;
        this.wallet = wallet;
        this.counters = counters;
        this.badges = badges;
        this.quests = quests;
        this.notifications = notifications;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Optional<String> currentUserId, String sort) {
        List<RewardProposal> list = "new".equalsIgnoreCase(sort)
                ? proposals.findAllByOrderByCreatedAtDesc()
                : proposals.findAllByOrderByVotesCountDescCreatedAtDesc();
        Set<String> voted = currentUserId.map(id -> (Set<String>) new HashSet<>(votes.findActiveProposalIdsByUser(id)))
                .orElse(Set.of());
        return list.stream().map(p -> view(p, voted.contains(p.getId()), currentUserId.orElse(null))).toList();
    }

    @Transactional
    public Map<String, Object> create(String userId, String rawTitle, String rawCategory, String rawDescription) {
        UserProfile u = wallet.lock(userId);
        String title = InputSanitizer.cleanText(rawTitle, 5, 80, "Titlul");
        String description = InputSanitizer.cleanText(rawDescription, 10, 255, "Descrierea");
        String category = rawCategory == null ? CATEGORIES.get(1) : rawCategory.trim();
        if (!CATEGORIES.contains(category)) {
            throw ApiException.badRequest("Categoria trebuie să fie una dintre: " + String.join(", ", CATEGORIES) + ".");
        }
        if (proposals.existsActiveWithTitle(title)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_PROPOSAL", "Există deja o propunere cu acest titlu. Votează-o!");
        }
        int today = counters.consume(userId, PROPOSALS_TODAY, 1, Economy.MAX_PROPOSALS_PER_DAY,
                "Poți publica cel mult " + Economy.MAX_PROPOSALS_PER_DAY + " propuneri pe zi.");

        RewardProposal p = new RewardProposal(Ids.prefixed("prop-"), u, u.getName(), title, category, description,
                1, "PENDING", time.now());
        proposals.save(p);
        ProposalVote own = new ProposalVote(Ids.prefixed("vote-"), p, u, time.now());
        votes.save(own);

        int reward = today == 1 ? Economy.PROPOSAL_REWARD : 0;
        if (reward > 0) {
            wallet.credit(u, reward, TxType.COMMUNITY_CONTRIBUTION, "Propunere nouă: " + title);
        }
        List<Badge> unlocked = badges.evaluate(u, Badge.VISIONARY);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("message", reward > 0 ? "Propunere publicată! Ai primit +" + reward + " AP." : "Propunere publicată!");
        r.put("rewardPoints", reward);
        r.put("proposal", view(p, true, userId));
        r.put("newBalance", u.getActivityPoints());
        r.put("badgesUnlocked", unlocked.stream().map(Badge::name).toList());
        return r;
    }

    @Transactional
    public Map<String, Object> toggleVote(String userId, String proposalId) {
        UserProfile u = wallet.lock(userId);
        RewardProposal p = proposals.findByIdForUpdate(proposalId)
                .orElseThrow(() -> ApiException.notFound("Propunerea nu a fost găsită."));
        Optional<ProposalVote> existing = votes.findByProposalIdAndUserId(proposalId, userId);
        boolean nowVoted;
        int reward = 0;
        if (existing.isPresent() && existing.get().isActive()) {
            existing.get().setActive(false);
            votes.save(existing.get());
            p.setVotesCount(Math.max(0, p.getVotesCount() - 1));
            nowVoted = false;
        } else {
            if (!VOTABLE.contains(p.getStatus())) {
                throw new ApiException(HttpStatus.CONFLICT, "VOTING_CLOSED", "Votul pentru această propunere s-a încheiat.");
            }
            if (existing.isPresent()) {
                existing.get().setActive(true);
                votes.save(existing.get());
            } else {
                votes.save(new ProposalVote(Ids.prefixed("vote-"), p, u, time.now()));
                if (counters.get(userId, REWARDED_VOTES) < Economy.MAX_REWARDED_VOTES_PER_DAY) {
                    counters.add(userId, REWARDED_VOTES, 1);
                    reward = Economy.VOTE_REWARD;
                    wallet.credit(u, reward, TxType.VOTE_REWARD, "Vot pentru: " + p.getTitle());
                }
                quests.track(u, Action.VOTE_PROPOSAL, 1);
            }
            p.setVotesCount(p.getVotesCount() + 1);
            nowVoted = true;
            if (VOTE_MILESTONES.contains(p.getVotesCount()) && p.getUser() != null && !p.getUser().getId().equals(userId)) {
                notifications.notify(p.getUser().getId(), "PROPOSAL", "Propunerea ta a ajuns la " + p.getVotesCount() + " voturi! 📈",
                        p.getTitle(), "📈", "#/comunitate");
            }
        }
        proposals.save(p);
        List<Badge> unlocked = reward > 0 ? badges.evaluate(u, Badge.VOICE_OF_COMMUNITY) : List.of();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("hasVoted", nowVoted);
        r.put("votesCount", p.getVotesCount());
        r.put("rewardPoints", reward);
        r.put("newBalance", u.getActivityPoints());
        r.put("message", !nowVoted ? "Votul tău a fost retras." : reward > 0 ? "Mulțumim pentru vot! +" + reward + " AP" : "Vot înregistrat!");
        r.put("badgesUnlocked", unlocked.stream().map(Badge::name).toList());
        return r;
    }

    private Map<String, Object> view(RewardProposal p, boolean hasVoted, String currentUserId) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("authorName", p.getAuthorName());
        m.put("title", p.getTitle());
        m.put("category", p.getCategory());
        m.put("description", p.getDescription());
        m.put("votesCount", p.getVotesCount());
        m.put("status", p.getStatus());
        m.put("adminNote", p.getAdminNote());
        m.put("createdAt", p.getCreatedAt());
        m.put("hasVoted", hasVoted);
        m.put("isMine", currentUserId != null && p.getUser() != null && currentUserId.equals(p.getUser().getId()));
        return m;
    }
}
