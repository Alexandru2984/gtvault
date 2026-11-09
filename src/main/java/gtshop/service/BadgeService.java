package gtshop.service;

import gtshop.game.Badge;
import gtshop.model.TxType;
import gtshop.model.UserBadge;
import gtshop.model.UserProfile;
import gtshop.repository.PointTransactionRepository;
import gtshop.repository.RewardProposalRepository;
import gtshop.repository.UserBadgeRepository;
import gtshop.repository.UserRepository;
import gtshop.util.Ids;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Deblocarea insignelor (cu recompensa in AP si notificare) si catalogul cu progres. */
@Service
public class BadgeService {
    private final UserBadgeRepository badgeRepository;
    private final PointTransactionRepository txRepository;
    private final UserRepository userRepository;
    private final RewardProposalRepository proposalRepository;
    private final WalletService wallet;
    private final NotificationService notifications;
    private final TimeService time;

    public BadgeService(UserBadgeRepository badgeRepository, PointTransactionRepository txRepository,
                        UserRepository userRepository, RewardProposalRepository proposalRepository,
                        WalletService wallet, NotificationService notifications, TimeService time) {
        this.badgeRepository = badgeRepository;
        this.txRepository = txRepository;
        this.userRepository = userRepository;
        this.proposalRepository = proposalRepository;
        this.wallet = wallet;
        this.notifications = notifications;
        this.time = time;
    }

    /** Statistici pentru calculul progresului. */
    private final class Stats {
        private final UserProfile user;
        private Map<String, Long> txCounts;
        private Integer referrals;
        private Long proposals;

        Stats(UserProfile user) {
            this.user = user;
        }

        long value(Badge b) {
            return switch (b.metric()) {
                case TX_COUNT -> txCounts().getOrDefault(b.txType().name(), 0L);
                case LONGEST_STREAK -> Math.max(user.getLongestStreak(), user.getStreakDays());
                case REFERRALS -> referrals();
                case PROPOSALS -> proposals();
                case EVENT -> 0;
            };
        }

        private Map<String, Long> txCounts() {
            if (txCounts == null) {
                txCounts = new LinkedHashMap<>();
                for (Object[] row : txRepository.countByTypeForUser(user.getId())) {
                    txCounts.put((String) row[0], (Long) row[1]);
                }
            }
            return txCounts;
        }

        private int referrals() {
            if (referrals == null) {
                referrals = user.getReferralCode() == null ? 0 : userRepository.countByReferredBy(user.getReferralCode());
            }
            return referrals;
        }

        private long proposals() {
            if (proposals == null) {
                proposals = proposalRepository.countByUserId(user.getId());
            }
            return proposals;
        }
    }

    /** Deblocheaza insigna daca nu exista deja. Utilizatorul trebuie sa fie blocat de apelant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean award(UserProfile lockedUser, Badge badge) {
        return award(lockedUser, badge, badge != Badge.HERO);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean has(String userId, Badge badge) {
        return badgeRepository.existsByUserIdAndBadgeCode(userId, badge.name());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean award(UserProfile lockedUser, Badge badge, boolean withPoints) {
        if (badgeRepository.existsByUserIdAndBadgeCode(lockedUser.getId(), badge.name())) {
            return false;
        }
        UserBadge ub = new UserBadge(Ids.prefixed("badge-"), lockedUser, badge.name(), badge.title(),
                badge.description(), badge.icon());
        ub.setUnlockedAt(time.now());
        badgeRepository.save(ub);
        int reward = withPoints ? badge.rarity().reward() : 0;
        if (reward > 0) {
            wallet.credit(lockedUser, reward, TxType.BADGE_REWARD, "Insignă deblocată: " + badge.title());
        }
        notifications.notify(lockedUser.getId(), "BADGE", "Insignă nouă: " + badge.title() + " " + badge.icon(),
                badge.description() + (reward > 0 ? " Ai primit +" + reward + " AP." : ""), badge.icon(), "#/profil");
        return true;
    }

    /** Verifica insignele candidate pe baza progresului si le deblocheaza pe cele atinse. */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Badge> evaluate(UserProfile lockedUser, Badge... candidates) {
        Stats stats = new Stats(lockedUser);
        List<Badge> unlocked = new ArrayList<>();
        for (Badge b : candidates) {
            if (b.metric() != Badge.Metric.EVENT && stats.value(b) >= b.target() && award(lockedUser, b)) {
                unlocked.add(b);
            }
        }
        return unlocked;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Badge> onOrder(UserProfile lockedUser, int orderTotal) {
        List<Badge> unlocked = new ArrayList<>(evaluate(lockedUser, Badge.FIRST_ORDER, Badge.LOYAL_CUSTOMER));
        if (orderTotal >= 1000 && award(lockedUser, Badge.BIG_SPENDER)) {
            unlocked.add(Badge.BIG_SPENDER);
        }
        return unlocked;
    }

    public record BadgeView(String code, String title, String description, String icon, String rarity, int rewardPoints,
                            boolean unlocked, java.time.Instant unlockedAt, long progress, int target) {}

    @Transactional(readOnly = true)
    public Map<String, Object> catalog(String userId) {
        UserProfile user = userRepository.findById(userId).orElseThrow();
        Map<String, UserBadge> owned = badgeRepository.findByUserIdOrderByUnlockedAtDesc(userId).stream()
                .collect(Collectors.toMap(UserBadge::getBadgeCode, Function.identity(), (a, b) -> a));
        Stats stats = new Stats(user);
        List<BadgeView> items = new ArrayList<>();
        Map<Badge.Rarity, Integer> perRarity = new EnumMap<>(Badge.Rarity.class);
        for (Badge b : Badge.values()) {
            UserBadge ub = owned.get(b.name());
            boolean has = ub != null;
            long progress = has ? b.target() : Math.min(b.target(), stats.value(b));
            items.add(new BadgeView(b.name(), b.title(), b.description(), b.icon(), b.rarity().name(),
                    b == Badge.HERO ? 0 : b.rarity().reward(), has, has ? ub.getUnlockedAt() : null, progress, b.target()));
            if (has) {
                perRarity.merge(b.rarity(), 1, Integer::sum);
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("unlockedCount", owned.keySet().stream().filter(c -> Badge.byCode(c) != null).count());
        res.put("totalCount", Badge.values().length);
        res.put("byRarity", perRarity);
        res.put("badges", items);
        return res;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> publicBadges(String userId) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (UserBadge ub : badgeRepository.findByUserIdOrderByUnlockedAtDesc(userId)) {
            Badge b = Badge.byCode(ub.getBadgeCode());
            if (b == null) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", b.name());
            m.put("title", b.title());
            m.put("icon", b.icon());
            m.put("rarity", b.rarity().name());
            m.put("unlockedAt", ub.getUnlockedAt());
            list.add(m);
        }
        return list;
    }
}
