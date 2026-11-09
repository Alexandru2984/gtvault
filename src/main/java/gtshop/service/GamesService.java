package gtshop.service;

import gtshop.game.Badge;
import gtshop.game.Economy;
import gtshop.game.Economy.CrateTier;
import gtshop.game.Economy.WheelSegment;
import gtshop.game.GameRandom;
import gtshop.game.QuestCatalog.Action;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Roata Norocului si Cuferele Misterioase: rezultate decise pe server, cu SecureRandom si limite zilnice. */
@Service
public class GamesService {
    private final UserRepository userRepository;
    private final WalletService wallet;
    private final DailyCounterService counters;
    private final BadgeService badges;
    private final QuestService quests;
    private final GameRandom random;
    private final TimeService time;

    public GamesService(UserRepository userRepository, WalletService wallet, DailyCounterService counters,
                        BadgeService badges, QuestService quests, GameRandom random, TimeService time) {
        this.userRepository = userRepository;
        this.wallet = wallet;
        this.counters = counters;
        this.badges = badges;
        this.quests = quests;
        this.random = random;
        this.time = time;
    }

    @Transactional
    public Map<String, Object> spinWheel(String userId) {
        UserProfile u = wallet.lock(userId);
        String today = time.todayIso();
        boolean free = !today.equals(u.getLastFreeSpinDate());
        int paidUsed;
        if (free) {
            u.setLastFreeSpinDate(today);
            userRepository.save(u);
            paidUsed = counters.get(userId, DailyRewardsService.PAID_SPINS);
        } else {
            paidUsed = counters.consume(userId, DailyRewardsService.PAID_SPINS, 1, Economy.MAX_PAID_SPINS_PER_DAY,
                    "Ai atins limita de " + Economy.MAX_PAID_SPINS_PER_DAY + " rotiri pe zi. Revino mâine!");
            wallet.debit(u, Economy.SPIN_COST, TxType.LUCKY_WHEEL_FEE, "Rotire Roata Norocului");
        }

        int index = pickWheel(random.nextInt(Economy.WHEEL_TOTAL_WEIGHT));
        WheelSegment seg = Economy.WHEEL.get(index);
        if (seg.points() > 0) {
            wallet.credit(u, seg.points(), TxType.LUCKY_WHEEL_WIN, "Câștig Roata Norocului: " + seg.label());
        }
        quests.track(u, Action.SPIN_WHEEL, 1);
        List<Badge> unlocked = new ArrayList<>();
        if (badges.award(u, Badge.LUCKY_SPINNER)) {
            unlocked.add(Badge.LUCKY_SPINNER);
        }
        if (seg.points() == maxWheelPrize() && badges.award(u, Badge.JACKPOT)) {
            unlocked.add(Badge.JACKPOT);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("prizeIndex", index);
        r.put("prizeName", seg.label());
        r.put("wonPoints", seg.points());
        r.put("free", free);
        r.put("cost", free ? 0 : Economy.SPIN_COST);
        r.put("paidSpinsLeft", Math.max(0, Economy.MAX_PAID_SPINS_PER_DAY - paidUsed));
        r.put("freeSpinAvailable", false);
        r.put("newBalance", u.getActivityPoints());
        r.put("message", seg.points() > 0 ? "Ai câștigat " + seg.points() + " AP!" : "Nu de data asta… mai încearcă!");
        r.put("badgesUnlocked", unlocked.stream().map(Badge::name).toList());
        return r;
    }

    @Transactional
    public Map<String, Object> openCrate(String userId) {
        UserProfile u = wallet.lock(userId);
        int used = counters.consume(userId, DailyRewardsService.CRATES, 1, Economy.MAX_CRATES_PER_DAY,
                "Ai deschis deja " + Economy.MAX_CRATES_PER_DAY + " cufere azi. Revino mâine!");
        wallet.debit(u, Economy.CRATE_COST, TxType.LOOT_CRATE_FEE, "Deschidere Cufăr Misterios");
        CrateTier tier = Economy.CRATE.get(pickCrate(random.nextInt(Economy.CRATE_TOTAL_WEIGHT)));
        wallet.credit(u, tier.points(), TxType.LOOT_CRATE_WIN, "Cufăr [" + tier.code() + "]: " + tier.label());
        quests.track(u, Action.OPEN_CRATE, 1);
        List<Badge> unlocked = new ArrayList<>(badges.evaluate(u, Badge.CRATE_HUNTER, Badge.CRATE_HOARDER));
        if ("MYTHIC".equals(tier.code()) && badges.award(u, Badge.MYTHIC_LUCK)) {
            unlocked.add(Badge.MYTHIC_LUCK);
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("tier", tier.code());
        r.put("prizeName", tier.label() + " (" + tier.points() + " AP)");
        r.put("wonPoints", tier.points());
        r.put("crateCost", Economy.CRATE_COST);
        r.put("color", tier.color());
        r.put("cratesLeft", Math.max(0, Economy.MAX_CRATES_PER_DAY - used));
        r.put("newBalance", u.getActivityPoints());
        r.put("message", "Ai deblocat: " + tier.label() + " – " + tier.points() + " AP!");
        r.put("badgesUnlocked", unlocked.stream().map(Badge::name).toList());
        return r;
    }

    static int pickWheel(int roll) {
        int acc = 0;
        for (int i = 0; i < Economy.WHEEL.size(); i++) {
            acc += Economy.WHEEL.get(i).weight();
            if (roll < acc) {
                return i;
            }
        }
        throw new IllegalArgumentException("roll in afara intervalului: " + roll);
    }

    static int pickCrate(int roll) {
        int acc = 0;
        for (int i = 0; i < Economy.CRATE.size(); i++) {
            acc += Economy.CRATE.get(i).weightPermille();
            if (roll < acc) {
                return i;
            }
        }
        throw new IllegalArgumentException("roll in afara intervalului: " + roll);
    }

    private static int maxWheelPrize() {
        return Economy.WHEEL.stream().mapToInt(WheelSegment::points).max().orElse(0);
    }
}
