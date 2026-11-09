package gtshop.service;

import gtshop.game.StreakMath;
import gtshop.game.Tiers;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.PointTransactionRepository;
import gtshop.repository.UserRepository;
import gtshop.util.AvatarCatalog;
import gtshop.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Clasamente: XP pe saptamana / luna / all-time si streak-uri active. Se bazeaza pe XP (puncte castigate
 * prin activitate), nu pe sold, deci cadourile si jocurile de noroc nu urca pe nimeni in clasament.
 */
@Service
public class LeaderboardService {
    public static final List<String> PERIODS = List.of("week", "month", "all", "streak");

    private final UserRepository userRepository;
    private final PointTransactionRepository txRepository;
    private final TimeService time;

    public LeaderboardService(UserRepository userRepository, PointTransactionRepository txRepository, TimeService time) {
        this.userRepository = userRepository;
        this.txRepository = txRepository;
        this.time = time;
    }

    public record Entry(int rank, String id, String name, String avatar, long value, String tier, String tierIcon,
                        String guildId, int streakDays, boolean isMe) {}

    @Transactional(readOnly = true)
    public Map<String, Object> leaderboard(String period, int limit, Optional<String> meId) {
        String p = period == null || period.isBlank() ? "week" : period.toLowerCase();
        if (!PERIODS.contains(p)) {
            throw ApiException.badRequest("Perioadă invalidă. Valori permise: " + String.join(", ", PERIODS) + ".");
        }
        int n = Math.min(50, Math.max(3, limit));
        String me = meId.orElse(null);
        List<Entry> entries = new ArrayList<>();
        switch (p) {
            case "all" -> {
                int rank = 1;
                for (UserProfile u : userRepository.findTopByXp(PageRequest.of(0, n))) {
                    entries.add(entry(rank++, u, u.getTotalPointsEarned(), me));
                }
            }
            case "streak" -> {
                int rank = 1;
                for (UserProfile u : userRepository.findTopAliveStreaks(time.todayIso(), n)) {
                    entries.add(entry(rank++, u, u.getStreakDays(), me));
                }
            }
            default -> {
                List<Object[]> rows = txRepository.topEarnersSince(since(p), TxType.XP_TYPES, PageRequest.of(0, n));
                Map<String, UserProfile> users = userRepository.findAllById(rows.stream().map(r -> (String) r[0]).toList())
                        .stream().collect(Collectors.toMap(UserProfile::getId, Function.identity()));
                int rank = 1;
                for (Object[] row : rows) {
                    UserProfile u = users.get((String) row[0]);
                    if (u != null) {
                        entries.add(entry(rank++, u, ((Number) row[1]).longValue(), me));
                    }
                }
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("period", p);
        res.put("since", "all".equals(p) || "streak".equals(p) ? null : since(p));
        res.put("entries", entries);
        if (me != null) {
            res.put("me", entries.stream().filter(Entry::isMe).findFirst().orElseGet(() -> myEntry(p, me)));
        }
        return res;
    }

    private Instant since(String period) {
        return "month".equals(period) ? time.startOfMonth() : time.startOfWeek();
    }

    private Entry myEntry(String period, String userId) {
        UserProfile u = userRepository.findById(userId).orElse(null);
        if (u == null || u.isBanned()) {
            return null;
        }
        long value;
        long rank;
        switch (period) {
            case "all" -> {
                value = u.getTotalPointsEarned();
                rank = userRepository.countWithXpGreaterThan(u.getTotalPointsEarned()) + 1;
            }
            case "streak" -> {
                value = StreakMath.effective(StreakMath.parse(u.getLastDailyClaimDate()), u.getStreakDays(),
                        u.getStreakFreezes(), time.today());
                rank = userRepository.countAliveStreaksAbove(time.todayIso(), (int) value) + 1;
            }
            default -> {
                value = txRepository.earnedSince(userId, since(period), TxType.XP_TYPES);
                rank = txRepository.countEarnersAbove(since(period), TxType.XP_TYPES, value) + 1;
            }
        }
        return entry((int) rank, u, value, userId);
    }

    private Entry entry(int rank, UserProfile u, long value, String me) {
        Tiers.Tier t = Tiers.forXp(u.getTotalPointsEarned());
        int streak = StreakMath.effective(StreakMath.parse(u.getLastDailyClaimDate()), u.getStreakDays(),
                u.getStreakFreezes(), time.today());
        return new Entry(rank, u.getId(), u.getName(), AvatarCatalog.safe(u.getAvatar()), value, t.code(), t.icon(),
                u.getGuildId(), streak, u.getId().equals(me));
    }
}
