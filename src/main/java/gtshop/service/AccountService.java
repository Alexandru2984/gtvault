package gtshop.service;

import gtshop.config.AppProperties;
import gtshop.game.Tiers;
import gtshop.model.PointTransaction;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.PointTransactionRepository;
import gtshop.repository.UserRepository;
import gtshop.util.AvatarCatalog;
import gtshop.web.ApiException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Jurnalul de puncte, nivelul (tier) si programul de recomandari pentru utilizatorul curent. */
@Service
public class AccountService {
    /** Filtre pentru istoric, pe categorii usor de inteles. */
    public static final Map<String, List<String>> LEDGER_FILTERS = Map.of(
            "earned", TxType.XP_TYPES,
            "games", List.of("LUCKY_WHEEL_FEE", "LUCKY_WHEEL_WIN", "LOOT_CRATE_FEE", "LOOT_CRATE_WIN"),
            "gifts", List.of("GIFT_SENT", "GIFT_RECEIVED"),
            "orders", List.of("ORDER_PAYMENT", "ORDER_REFUND"));

    private final UserRepository userRepository;
    private final PointTransactionRepository txRepository;
    private final AppProperties props;
    private final TimeService time;

    public AccountService(UserRepository userRepository, PointTransactionRepository txRepository, AppProperties props,
                          TimeService time) {
        this.userRepository = userRepository;
        this.txRepository = txRepository;
        this.props = props;
        this.time = time;
    }

    public record LedgerItem(String id, String type, String label, String icon, int amount, int balanceAfter,
                             Instant createdAt, String description) {}

    @Transactional(readOnly = true)
    public Map<String, Object> ledger(String userId, int page, int size, String filter) {
        int p = Math.max(0, page);
        int s = Math.min(50, Math.max(1, size));
        PageRequest pr = PageRequest.of(p, s);
        Page<PointTransaction> result;
        if (filter == null || filter.isBlank() || "all".equals(filter)) {
            result = txRepository.findByUserIdOrderByCreatedAtDesc(userId, pr);
        } else {
            List<String> types = LEDGER_FILTERS.get(filter);
            if (types == null) {
                throw ApiException.badRequest("Filtru necunoscut. Valori permise: all, " + String.join(", ", LEDGER_FILTERS.keySet()) + ".");
            }
            result = txRepository.findByUserIdAndTypeInOrderByCreatedAtDesc(userId, types, pr);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", result.getContent().stream().map(AccountService::item).toList());
        res.put("page", p);
        res.put("size", s);
        res.put("totalItems", result.getTotalElements());
        res.put("totalPages", result.getTotalPages());
        res.put("filters", Arrays.asList("all", "earned", "games", "gifts", "orders"));
        return res;
    }

    static LedgerItem item(PointTransaction t) {
        TxType type = TxType.parse(t.getType());
        return new LedgerItem(t.getId(), t.getType(), type == null ? t.getType() : type.label(),
                type == null ? "•" : type.icon(), t.getAmount(), t.getBalanceAfter(), t.getCreatedAt(), t.getDescription());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> stats(String userId) {
        UserProfile u = userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("balance", u.getActivityPoints());
        res.put("xp", u.getTotalPointsEarned());
        res.put("xpThisWeek", txRepository.earnedSince(userId, time.startOfWeek(), TxType.XP_TYPES));
        res.put("xpThisMonth", txRepository.earnedSince(userId, time.startOfMonth(), TxType.XP_TYPES));
        res.put("tier", Tiers.progress(u.getTotalPointsEarned()));
        return res;
    }

    @Transactional(readOnly = true)
    public Tiers.TierProgress tier(String userId) {
        UserProfile u = userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        return Tiers.progress(u.getTotalPointsEarned());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> referrals(String userId) {
        UserProfile u = userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        String code = u.getReferralCode();
        int invited = code == null ? 0 : userRepository.countByReferredBy(code);
        int rewarded = Math.min(invited, AuthService.MAX_REWARDED_REFERRALS);
        List<Map<String, Object>> friends = code == null ? List.of()
                : userRepository.findTop20ByReferredByOrderByCreatedAtDesc(code).stream()
                        .map(f -> Map.<String, Object>of("name", firstName(f.getName()), "avatar", AvatarCatalog.safe(f.getAvatar()),
                                "joinedAt", f.getCreatedAt()))
                        .toList();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("referralCode", code);
        res.put("referralLink", code == null ? null : props.getPublicBaseUrl() + "/?ref=" + code);
        res.put("invitedCount", invited);
        res.put("rewardedCount", rewarded);
        res.put("maxRewarded", AuthService.MAX_REWARDED_REFERRALS);
        res.put("earnedPoints", rewarded * AuthService.REFERRAL_REFERRER_BONUS);
        res.put("bonusPerFriend", AuthService.REFERRAL_REFERRER_BONUS);
        res.put("friendBonus", AuthService.REFERRAL_NEW_USER_BONUS);
        res.put("friends", friends);
        return res;
    }

    /** Pentru listele vizibile altor membri afisam doar prenumele. */
    static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "Membru";
        }
        String n = name.trim();
        int sp = n.indexOf(' ');
        return sp > 0 ? n.substring(0, sp) : n;
    }
}
