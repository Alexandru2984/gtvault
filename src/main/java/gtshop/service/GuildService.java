package gtshop.service;

import gtshop.game.Economy;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.PointTransactionRepository;
import gtshop.repository.UserRepository;
import gtshop.util.AvatarCatalog;
import gtshop.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ghildele comunitatii si „Războiul ghildelor” saptamanal (clasament dupa XP-ul castigat in saptamana curenta). */
@Service
public class GuildService {
    public static final String DEFAULT_GUILD = "JAVA_KNIGHTS";

    public record Guild(String id, String name, String icon, String color, String description) {}

    public static final List<Guild> GUILDS = List.of(
            new Guild("JAVA_KNIGHTS", "Cavalerii Java", "☕", "#f97316", "Maeștrii backend-ului, Spring Boot și performanței enterprise."),
            new Guild("PYTHON_WIZARDS", "Vrăjitorii Python", "🐍", "#3b82f6", "Alchimiștii inteligenței artificiale, data science și automatizărilor."),
            new Guild("DEVOPS_TITANS", "Titanii DevOps & Cloud", "☁️", "#06b6d4", "Păzitorii containerelor, CI/CD și infrastructurii care nu cade."),
            new Guild("FRONTEND_NINJAS", "Maeștrii Frontend", "⚛️", "#a855f7", "Artiștii interfețelor moderne, animațiilor fluide și experienței mobile."));

    private final UserRepository userRepository;
    private final PointTransactionRepository txRepository;
    private final WalletService wallet;
    private final TimeService time;

    public GuildService(UserRepository userRepository, PointTransactionRepository txRepository, WalletService wallet,
                        TimeService time) {
        this.userRepository = userRepository;
        this.txRepository = txRepository;
        this.wallet = wallet;
        this.time = time;
    }

    public static Guild byId(String id) {
        return GUILDS.stream().filter(g -> g.id().equals(id)).findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Map<String, long[]> totals = new HashMap<>();
        for (Object[] row : userRepository.guildTotals()) {
            String id = row[0] == null ? DEFAULT_GUILD : (String) row[0];
            long[] acc = totals.computeIfAbsent(id, k -> new long[2]);
            acc[0] += ((Number) row[1]).longValue();
            acc[1] += ((Number) row[2]).longValue();
        }
        Map<String, Long> weekly = new HashMap<>();
        for (Object[] row : txRepository.guildEarningsSince(time.startOfWeek(), TxType.XP_TYPES)) {
            String id = row[0] == null ? DEFAULT_GUILD : (String) row[0];
            weekly.merge(id, ((Number) row[1]).longValue(), Long::sum);
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Guild g : GUILDS) {
            long[] t = totals.getOrDefault(g.id(), new long[2]);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.id());
            m.put("name", g.name());
            m.put("icon", g.icon());
            m.put("color", g.color());
            m.put("description", g.description());
            m.put("members", t[0]);
            m.put("totalXp", t[1]);
            m.put("weeklyXp", weekly.getOrDefault(g.id(), 0L));
            m.put("topMembers", userRepository.findTopInGuild(g.id(), PageRequest.of(0, 3)).stream()
                    .map(u -> Map.of("id", u.getId(), "name", u.getName(), "avatar", AvatarCatalog.safe(u.getAvatar()),
                            "xp", u.getTotalPointsEarned()))
                    .toList());
            list.add(m);
        }
        list.sort(Comparator.<Map<String, Object>>comparingLong(m -> (Long) m.get("weeklyXp")).reversed()
                .thenComparing(Comparator.<Map<String, Object>>comparingLong(m -> (Long) m.get("totalXp")).reversed()));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).put("rank", i + 1);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("guilds", list);
        res.put("weekStartedAt", time.startOfWeek());
        res.put("weekEndsAt", time.startOfNextWeek());
        return res;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> mine(String userId) {
        UserProfile u = userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        return mineView(u);
    }

    private Map<String, Object> mineView(UserProfile u) {
        Guild g = byId(u.getGuildId());
        if (g == null) {
            g = byId(DEFAULT_GUILD);
        }
        Instant canSwitchAt = u.getGuildChangedAt() == null ? null
                : u.getGuildChangedAt().plus(Duration.ofDays(Economy.GUILD_SWITCH_COOLDOWN_DAYS));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.id());
        m.put("name", g.name());
        m.put("icon", g.icon());
        m.put("color", g.color());
        m.put("description", g.description());
        m.put("canSwitch", canSwitchAt == null || !canSwitchAt.isAfter(time.now()));
        m.put("canSwitchAt", canSwitchAt);
        return m;
    }

    @Transactional
    public Map<String, Object> select(String userId, String guildId) {
        Guild g = byId(guildId);
        if (g == null) {
            throw ApiException.badRequest("Ghilda selectată nu există.");
        }
        UserProfile u = wallet.lock(userId);
        if (g.id().equals(u.getGuildId())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_MEMBER", "Faci deja parte din " + g.name() + ".");
        }
        Instant now = time.now();
        if (u.getGuildChangedAt() != null
                && u.getGuildChangedAt().plus(Duration.ofDays(Economy.GUILD_SWITCH_COOLDOWN_DAYS)).isAfter(now)) {
            throw new ApiException(HttpStatus.CONFLICT, "GUILD_COOLDOWN",
                    "Poți schimba ghilda o dată la " + Economy.GUILD_SWITCH_COOLDOWN_DAYS + " zile.");
        }
        u.setGuildId(g.id());
        u.setGuildChangedAt(now);
        userRepository.save(u);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", "Bun venit în " + g.name() + " " + g.icon() + "!");
        res.put("guild", mineView(u));
        return res;
    }
}
