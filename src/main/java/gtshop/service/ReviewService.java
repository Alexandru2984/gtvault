package gtshop.service;

import gtshop.game.Badge;
import gtshop.game.Economy;
import gtshop.game.QuestCatalog.Action;
import gtshop.game.Tiers;
import gtshop.model.ProductReview;
import gtshop.model.Reward;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.ProductReviewRepository;
import gtshop.repository.RewardRepository;
import gtshop.util.AvatarCatalog;
import gtshop.util.Ids;
import gtshop.util.InputSanitizer;
import gtshop.web.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recenzii verificate: doar cine a primit produsul (comanda livrata sau finalizata) poate scrie,
 * o singura recenzie per produs, editabila. Recompensa in AP se acorda o singura data per produs,
 * cu plafon zilnic, deci stergerea si rescrierea nu genereaza puncte.
 */
@Service
public class ReviewService {
    static final String COUNTER = "REVIEWS_REWARDED";
    public static final List<String> SORTS = List.of("helpful", "recent", "rating_desc", "rating_asc");
    public static final List<String> REPORT_REASONS = List.of("SPAM", "OFFENSIVE", "OFF_TOPIC", "FAKE", "OTHER");
    private static final Set<Integer> HELPFUL_MILESTONES = Set.of(1, 5, 10, 25, 50, 100);
    private static final int MAX_PAGE_SIZE = 20;

    /** O linie de comanda „primita”: comenzile digitale sunt finalizate imediat, cele fizice dupa livrare. */
    static final String RECEIVED = """
            EXISTS (SELECT 1 FROM purchase_records p JOIN orders o ON o.id = p.order_id
                    WHERE p.user_id = %s AND p.reward_id = %s AND o.status IN ('DELIVERED', 'FULFILLED'))""";

    private static final String SELECT_VIEW = """
            SELECT r.id, r.reward_id, r.user_id, r.rating, r.comment, r.created_at, r.updated_at, r.helpful_count, r.hidden,
                   u.name, u.avatar, u.total_points_earned, u.public_profile, w.name AS reward_name,
                   %s AS verified, %s AS voted
            FROM product_reviews r
            JOIN users u ON u.id = r.user_id
            JOIN rewards w ON w.id = r.reward_id
            """.formatted(RECEIVED.formatted("r.user_id", "r.reward_id"),
            "EXISTS (SELECT 1 FROM review_votes v WHERE v.review_id = r.id AND v.user_id = ?)");

    private final ProductReviewRepository reviews;
    private final RewardRepository rewards;
    private final JdbcTemplate jdbc;
    private final WalletService wallet;
    private final DailyCounterService counters;
    private final BadgeService badges;
    private final QuestService quests;
    private final NotificationService notifications;
    private final TimeService time;

    public ReviewService(ProductReviewRepository reviews, RewardRepository rewards, JdbcTemplate jdbc, WalletService wallet,
                         DailyCounterService counters, BadgeService badges, QuestService quests,
                         NotificationService notifications, TimeService time) {
        this.reviews = reviews;
        this.rewards = rewards;
        this.jdbc = jdbc;
        this.wallet = wallet;
        this.counters = counters;
        this.badges = badges;
        this.quests = quests;
        this.notifications = notifications;
        this.time = time;
    }

    public record Author(String id, String name, String avatar, String tier, String tierIcon) {}

    public record ReviewView(String id, String rewardId, String rewardName, int rating, String comment, Instant createdAt,
                             Instant updatedAt, boolean verified, int helpfulCount, boolean votedHelpful, boolean mine,
                             boolean hidden, Author author) {}

    // ------------------------------------------------------------------ citire

    @Transactional(readOnly = true)
    public Map<String, Object> list(String rewardId, String sort, int page, int size, Optional<String> me) {
        Reward reward = rewards.findById(rewardId).filter(Reward::isActive)
                .orElseThrow(() -> ApiException.notFound("Produsul nu a fost găsit."));
        String s = sort == null || sort.isBlank() ? "helpful" : sort;
        if (!SORTS.contains(s)) {
            throw ApiException.badRequest("Sortare invalidă. Valori permise: " + String.join(", ", SORTS) + ".");
        }
        int pageSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        int pageNo = Math.min(500, Math.max(0, page));
        String order = switch (s) {
            case "recent" -> "r.created_at DESC, r.id";
            case "rating_desc" -> "r.rating DESC, r.created_at DESC, r.id";
            case "rating_asc" -> "r.rating ASC, r.created_at DESC, r.id";
            default -> "verified DESC, r.helpful_count DESC, r.created_at DESC, r.id";
        };
        String viewer = me.orElse("");
        List<ReviewView> items = jdbc.query(SELECT_VIEW + " WHERE r.reward_id = ? AND NOT r.hidden AND NOT u.banned ORDER BY "
                        + order + " LIMIT ? OFFSET ?",
                (rs, i) -> map(rs, viewer), viewer, rewardId, pageSize + 1, pageNo * pageSize);
        boolean hasMore = items.size() > pageSize;
        if (hasMore) {
            items = items.subList(0, pageSize);
        }

        Map<Integer, Long> distribution = new LinkedHashMap<>();
        for (int star = 5; star >= 1; star--) {
            distribution.put(star, 0L);
        }
        long[] totals = {0, 0};
        jdbc.query("""
                SELECT r.rating, count(*) FROM product_reviews r JOIN users u ON u.id = r.user_id
                WHERE r.reward_id = ? AND NOT r.hidden AND NOT u.banned GROUP BY r.rating""", rs -> {
            long c = rs.getLong(2);
            distribution.put(rs.getInt(1), c);
            totals[0] += c;
            totals[1] += c * rs.getInt(1);
        }, rewardId);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("rewardId", reward.getId());
        res.put("averageRating", totals[0] == 0 ? null : CatalogStats.round1((double) totals[1] / totals[0]));
        res.put("totalReviews", totals[0]);
        res.put("distribution", distribution);
        res.put("sort", s);
        res.put("sorts", SORTS);
        res.put("page", pageNo);
        res.put("hasMore", hasMore);
        res.put("reviews", items);
        if (me.isPresent()) {
            res.put("myReview", find(me.get(), rewardId).orElse(null));
            res.put("eligibility", eligibility(me.get(), rewardId));
        } else {
            res.put("myReview", null);
            res.put("eligibility", Map.of("canReview", false, "reason", "LOGIN_REQUIRED", "rewardPoints", 0));
        }
        return res;
    }

    private Map<String, Object> eligibility(String userId, String rewardId) {
        boolean reviewed = reviews.findByRewardIdAndUserId(rewardId, userId).isPresent();
        boolean received = received(userId, rewardId);
        boolean rewarded = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM review_rewards WHERE user_id = ? AND reward_id = ?)", Boolean.class, userId, rewardId));
        String reason = reviewed ? "ALREADY_REVIEWED" : received ? null : "PURCHASE_REQUIRED";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("canReview", reason == null);
        m.put("reason", reason);
        m.put("rewardPoints", reason == null && !rewarded ? Economy.REVIEW_REWARD : 0);
        return m;
    }

    /** Recenziile proprii (inclusiv cele ascunse de moderator). */
    @Transactional(readOnly = true)
    public List<ReviewView> mine(String userId) {
        return jdbc.query(SELECT_VIEW + " WHERE r.user_id = ? ORDER BY r.created_at DESC LIMIT 100",
                (rs, i) -> map(rs, userId), userId, userId);
    }

    /** Produse primite pentru care utilizatorul nu a scris inca recenzie. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pending(String userId) {
        return jdbc.query("""
                SELECT w.id, w.name, w.image, max(o.created_at) AS bought_at,
                       NOT EXISTS (SELECT 1 FROM review_rewards rr WHERE rr.user_id = ? AND rr.reward_id = w.id) AS rewardable
                FROM purchase_records p
                JOIN orders o ON o.id = p.order_id
                JOIN rewards w ON w.id = p.reward_id
                WHERE p.user_id = ? AND o.status IN ('DELIVERED', 'FULFILLED') AND w.active
                  AND NOT EXISTS (SELECT 1 FROM product_reviews r WHERE r.user_id = p.user_id AND r.reward_id = w.id)
                GROUP BY w.id, w.name, w.image
                ORDER BY bought_at DESC
                LIMIT 50""", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rewardId", rs.getString("id"));
            m.put("name", rs.getString("name"));
            m.put("image", rs.getString("image"));
            m.put("boughtAt", rs.getTimestamp("bought_at").toInstant());
            m.put("rewardPoints", rs.getBoolean("rewardable") ? Economy.REVIEW_REWARD : 0);
            return m;
        }, userId, userId);
    }

    @Transactional(readOnly = true)
    public List<ReviewView> recentByUser(String userId, int limit) {
        return jdbc.query(SELECT_VIEW + " WHERE r.user_id = ? AND NOT r.hidden ORDER BY r.created_at DESC LIMIT ?",
                (rs, i) -> map(rs, ""), "", userId, limit);
    }

    // ------------------------------------------------------------------ scriere

    @Transactional
    public Map<String, Object> create(String userId, String rewardId, Integer rating, String comment) {
        UserProfile u = wallet.lock(userId);
        Reward reward = rewards.findById(rewardId).filter(Reward::isActive)
                .orElseThrow(() -> ApiException.notFound("Produsul nu a fost găsit."));
        if (reviews.findByRewardIdAndUserId(rewardId, userId).isPresent()) {
            throw ApiException.conflict("ALREADY_REVIEWED", "Ai scris deja o recenzie pentru acest produs. O poți edita oricând.");
        }
        if (!received(userId, rewardId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PURCHASE_REQUIRED",
                    "Poți scrie o recenzie doar pentru produsele primite (comenzi livrate sau finalizate).");
        }
        int stars = validRating(rating);
        String text = validComment(comment);
        ProductReview review = reviews.saveAndFlush(new ProductReview(Ids.prefixed("rev-"), rewardId, userId, stars, text, time.now()));

        int rewardPoints = 0;
        List<Badge> unlocked = List.of();
        if (counters.get(userId, COUNTER) < Economy.MAX_REWARDED_REVIEWS_PER_DAY
                && jdbc.update("INSERT INTO review_rewards (user_id, reward_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                userId, rewardId, Timestamp.from(time.now())) == 1) {
            counters.add(userId, COUNTER, 1);
            wallet.credit(u, Economy.REVIEW_REWARD, TxType.REVIEW_REWARD, "Recenzie: " + reward.getName());
            rewardPoints = Economy.REVIEW_REWARD;
            unlocked = badges.evaluate(u, Badge.CRITIC);
        }
        quests.track(u, Action.WRITE_REVIEW, 1);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", rewardPoints > 0
                ? "Mulțumim pentru recenzie! Ai primit " + rewardPoints + " AP. ⭐"
                : "Mulțumim pentru recenzie! A fost publicată.");
        res.put("review", find(userId, review.getRewardId()).orElse(null));
        res.put("rewardPoints", rewardPoints);
        res.put("newBalance", u.getActivityPoints());
        res.put("badgesUnlocked", unlocked.stream().map(Badge::name).toList());
        return res;
    }

    @Transactional
    public Map<String, Object> update(String userId, String rewardId, Integer rating, String comment) {
        ProductReview review = reviews.findByRewardIdAndUserId(rewardId, userId)
                .orElseThrow(() -> ApiException.notFound("Nu ai o recenzie pentru acest produs."));
        review.setRating(validRating(rating));
        review.setComment(validComment(comment));
        review.setUpdatedAt(time.now());
        reviews.saveAndFlush(review);
        return Map.of("success", true, "message", "Recenzia a fost actualizată.",
                "review", find(userId, rewardId).orElseThrow());
    }

    @Transactional
    public Map<String, Object> delete(String userId, String rewardId) {
        ProductReview review = reviews.findByRewardIdAndUserId(rewardId, userId)
                .orElseThrow(() -> ApiException.notFound("Nu ai o recenzie pentru acest produs."));
        reviews.delete(review);
        return Map.of("success", true, "message", "Recenzia a fost ștearsă.");
    }

    /** Comuta votul „util”. Contorul se actualizeaza atomic in SQL (fara pierderi la voturi simultane). */
    @Transactional
    public Map<String, Object> toggleHelpful(String userId, String reviewId) {
        Map<String, Object> row = jdbc.queryForList("""
                SELECT r.user_id, r.reward_id, w.name FROM product_reviews r JOIN rewards w ON w.id = r.reward_id
                WHERE r.id = ? AND NOT r.hidden""", reviewId).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Recenzia nu a fost găsită."));
        String author = (String) row.get("user_id");
        if (userId.equals(author)) {
            throw ApiException.badRequest("OWN_REVIEW", "Nu îți poți vota propria recenzie.");
        }
        boolean helpful;
        Integer count;
        if (jdbc.update("INSERT INTO review_votes (review_id, user_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                reviewId, userId, Timestamp.from(time.now())) == 1) {
            helpful = true;
            count = jdbc.queryForObject("UPDATE product_reviews SET helpful_count = helpful_count + 1 WHERE id = ? RETURNING helpful_count",
                    Integer.class, reviewId);
            if (count != null && HELPFUL_MILESTONES.contains(count)) {
                notifications.notify(author, "REVIEW", count == 1
                                ? "Cuiva i-a fost utilă recenzia ta 👍"
                                : count + " persoane au găsit utilă recenzia ta 👍",
                        "Recenzia ta pentru „" + row.get("name") + "” ajută comunitatea.", "👍",
                        "#/produs/" + row.get("reward_id"));
            }
        } else {
            helpful = false;
            if (jdbc.update("DELETE FROM review_votes WHERE review_id = ? AND user_id = ?", reviewId, userId) == 1) {
                count = jdbc.queryForObject("UPDATE product_reviews SET helpful_count = greatest(helpful_count - 1, 0) WHERE id = ? RETURNING helpful_count",
                        Integer.class, reviewId);
            } else {
                count = jdbc.queryForObject("SELECT helpful_count FROM product_reviews WHERE id = ?", Integer.class, reviewId);
            }
        }
        return Map.of("success", true, "helpful", helpful, "helpfulCount", count == null ? 0 : count);
    }

    @Transactional
    public Map<String, Object> report(String userId, String reviewId, String reason) {
        String r = reason == null ? "" : reason.trim().toUpperCase(java.util.Locale.ROOT);
        if (!REPORT_REASONS.contains(r)) {
            throw ApiException.badRequest("Motiv invalid. Valori permise: " + String.join(", ", REPORT_REASONS) + ".");
        }
        List<String> author = jdbc.queryForList("SELECT user_id FROM product_reviews WHERE id = ? AND NOT hidden", String.class, reviewId);
        if (author.isEmpty()) {
            throw ApiException.notFound("Recenzia nu a fost găsită.");
        }
        if (author.get(0).equals(userId)) {
            throw ApiException.badRequest("OWN_REVIEW", "Nu îți poți raporta propria recenzie.");
        }
        jdbc.update("INSERT INTO review_reports (review_id, user_id, reason, created_at) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                reviewId, userId, r, Timestamp.from(time.now()));
        return Map.of("success", true, "message", "Mulțumim! Un moderator va verifica recenzia.");
    }

    // ------------------------------------------------------------------ utilitare

    private boolean received(String userId, String rewardId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT " + RECEIVED.formatted("?", "?"), Boolean.class, userId, rewardId));
    }

    private Optional<ReviewView> find(String userId, String rewardId) {
        return jdbc.query(SELECT_VIEW + " WHERE r.user_id = ? AND r.reward_id = ?", (rs, i) -> map(rs, userId),
                userId, userId, rewardId).stream().findFirst();
    }

    private static int validRating(Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw ApiException.badRequest("Nota trebuie să fie între 1 și 5 stele.");
        }
        return rating;
    }

    private static String validComment(String comment) {
        return InputSanitizer.cleanText(comment, Economy.REVIEW_MIN_LENGTH, Economy.REVIEW_MAX_LENGTH, "Recenzia");
    }

    private static ReviewView map(ResultSet rs, String viewer) throws SQLException {
        String authorId = rs.getString("user_id");
        boolean isPublic = rs.getBoolean("public_profile");
        Tiers.Tier tier = Tiers.forXp(rs.getInt("total_points_earned"));
        Timestamp updated = rs.getTimestamp("updated_at");
        Author author = new Author(isPublic || authorId.equals(viewer) ? authorId : null, rs.getString("name"),
                AvatarCatalog.safe(rs.getString("avatar")), tier.code(), tier.icon());
        return new ReviewView(rs.getString("id"), rs.getString("reward_id"), rs.getString("reward_name"), rs.getInt("rating"),
                rs.getString("comment"), rs.getTimestamp("created_at").toInstant(), updated == null ? null : updated.toInstant(),
                rs.getBoolean("verified"), rs.getInt("helpful_count"), rs.getBoolean("voted"), authorId.equals(viewer),
                rs.getBoolean("hidden"), author);
    }
}
