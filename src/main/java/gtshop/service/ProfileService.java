package gtshop.service;

import gtshop.game.StreakMath;
import gtshop.game.Tiers;
import gtshop.model.UserProfile;
import gtshop.repository.ProductReviewRepository;
import gtshop.repository.UserRepository;
import gtshop.util.AvatarCatalog;
import gtshop.web.ApiException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Profilul public al unui membru. Nu contine date private (email, sold, comenzi, adrese).
 * Cine isi seteaza profilul ca privat apare doar cu numele si avatarul.
 */
@Service
public class ProfileService {
    private final UserRepository users;
    private final ProductReviewRepository reviewRepository;
    private final BadgeService badges;
    private final ReviewService reviews;
    private final WordleService wordle;
    private final JdbcTemplate jdbc;
    private final TimeService time;

    public ProfileService(UserRepository users, ProductReviewRepository reviewRepository, BadgeService badges,
                          ReviewService reviews, WordleService wordle, JdbcTemplate jdbc, TimeService time) {
        this.users = users;
        this.reviewRepository = reviewRepository;
        this.badges = badges;
        this.reviews = reviews;
        this.wordle = wordle;
        this.jdbc = jdbc;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> publicProfile(String id, Optional<String> viewer) {
        UserProfile u = users.findById(id).filter(x -> !x.isBanned())
                .orElseThrow(() -> ApiException.notFound("Profilul nu a fost găsit."));
        boolean me = viewer.map(id::equals).orElse(false);
        Tiers.Tier tier = Tiers.forXp(u.getTotalPointsEarned());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", u.getId());
        r.put("name", u.getName());
        r.put("avatar", AvatarCatalog.safe(u.getAvatar()));
        r.put("tier", Map.of("code", tier.code(), "title", tier.title(), "icon", tier.icon(), "level", tier.level()));
        r.put("isMe", me);
        r.put("publicProfile", u.isPublicProfile());
        if (!u.isPublicProfile() && !me) {
            r.put("private", true);
            return r;
        }
        r.put("private", false);
        r.put("bio", u.getBio());
        r.put("guildId", u.getGuildId());
        r.put("guild", GuildService.GUILDS.stream().filter(g -> g.id().equals(u.getGuildId())).findFirst().orElse(null));
        r.put("memberSince", u.getCreatedAt());
        r.put("xp", u.getTotalPointsEarned());
        Integer rank = jdbc.queryForObject("SELECT count(*) + 1 FROM users WHERE NOT banned AND total_points_earned > ?",
                Integer.class, u.getTotalPointsEarned());
        r.put("xpRank", rank);
        r.put("streakDays", StreakMath.effective(StreakMath.parse(u.getLastDailyClaimDate()), u.getStreakDays(),
                u.getStreakFreezes(), time.today()));
        r.put("longestStreak", u.getLongestStreak());
        List<Map<String, Object>> owned = badges.publicBadges(u.getId());
        r.put("badgeCount", owned.size());
        r.put("badges", owned);
        r.put("reviewCount", reviewRepository.countByUserIdAndHiddenFalse(u.getId()));
        r.put("recentReviews", reviews.recentByUser(u.getId(), 3));
        r.put("wordle", wordle.publicStats(u.getId()));
        return r;
    }
}
