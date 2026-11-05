package gtshop.service;

import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional
public class GuildServiceImpl implements GuildService {

    private final UserRepository userRepository;

    public static final Map<String, GuildMeta> GUILD_META = Map.of(
        "JAVA_KNIGHTS", new GuildMeta("JAVA_KNIGHTS", "Cavalerii Java", "☕", "#f97316", "Maeștrii arhitecturii backend, Spring Boot și performanței enterprise."),
        "PYTHON_WIZARDS", new GuildMeta("PYTHON_WIZARDS", "Vrăjitorii Python", "🐍", "#3b82f6", "Alchimiștii inteligenței artificiale, data science și scripturilor rapide."),
        "DEVOPS_TITANS", new GuildMeta("DEVOPS_TITANS", "Titanii DevOps & Cloud", "☁️", "#06b6d4", "Păzitorii containerelor, CI/CD și infrastructurii de înaltă disponibilitate."),
        "FRONTEND_NINJAS", new GuildMeta("FRONTEND_NINJAS", "Maeștrii Frontend", "⚛️", "#a855f7", "Artiștii designului modern, animațiilor fluide și experienței mobile top.")
    );

    public GuildServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getGuildsOverview() {
        List<UserProfile> allUsers = userRepository.findAll();
        Map<String, Integer> memberCounts = new HashMap<>();
        Map<String, Long> totalPoints = new HashMap<>();

        for (String id : GUILD_META.keySet()) {
            memberCounts.put(id, 0);
            totalPoints.put(id, 0L);
        }

        for (UserProfile u : allUsers) {
            String gId = u.getGuildId();
            if (gId == null || !GUILD_META.containsKey(gId)) {
                gId = "JAVA_KNIGHTS";
            }
            memberCounts.put(gId, memberCounts.getOrDefault(gId, 0) + 1);
            long userXp = Math.max(u.getTotalPointsEarned(), u.getActivityPoints());
            totalPoints.put(gId, totalPoints.getOrDefault(gId, 0L) + userXp);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (GuildMeta meta : GUILD_META.values()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", meta.id);
            map.put("name", meta.name);
            map.put("icon", meta.icon);
            map.put("color", meta.color);
            map.put("description", meta.description);
            map.put("members", memberCounts.getOrDefault(meta.id, 0));
            map.put("totalXp", totalPoints.getOrDefault(meta.id, 0L));
            result.add(map);
        }

        result.sort((a, b) -> Long.compare((Long) b.get("totalXp"), (Long) a.get("totalXp")));

        // Assign ranks 1 to 4
        int rank = 1;
        for (Map<String, Object> g : result) {
            g.put("rank", rank++);
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getUserGuild(UserProfile user) {
        String guildId = (user != null && user.getGuildId() != null) ? user.getGuildId() : "JAVA_KNIGHTS";
        GuildMeta meta = GUILD_META.getOrDefault(guildId, GUILD_META.get("JAVA_KNIGHTS"));

        Map<String, Object> res = new HashMap<>();
        res.put("id", meta.id);
        res.put("name", meta.name);
        res.put("icon", meta.icon);
        res.put("color", meta.color);
        res.put("description", meta.description);
        return res;
    }

    @Override
    public Map<String, Object> selectGuild(UserProfile user, String guildId) {
        if (user == null) {
            throw new IllegalArgumentException("Utilizator neautentificat.");
        }
        if (!GUILD_META.containsKey(guildId)) {
            throw new IllegalArgumentException("Ghilda selectată nu este validă.");
        }

        user.setGuildId(guildId);
        userRepository.save(user);

        GuildMeta meta = GUILD_META.get(guildId);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "Te-ai alăturat cu succes facțiunii " + meta.name + " " + meta.icon + "!");
        res.put("guild", getUserGuild(user));
        return res;
    }

    public static class GuildMeta {
        public final String id;
        public final String name;
        public final String icon;
        public final String color;
        public final String description;

        public GuildMeta(String id, String name, String icon, String color, String description) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.color = color;
            this.description = description;
        }
    }
}
