package gtshop.service;

import gtshop.model.Reward;
import gtshop.model.UserProfile;
import gtshop.repository.RewardRepository;
import gtshop.repository.UserRepository;
import gtshop.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriUtils;

/** Obiectiv personal cu preț estimat de aceeași logică folosită la checkout. */
@Service
public class SavingGoalService {
    private final JdbcTemplate jdbc;
    private final UserRepository users;
    private final RewardRepository rewards;
    private final WalletService wallet;
    private final QuoteService quotes;
    private final NotificationService notifications;
    private final TimeService time;

    public SavingGoalService(JdbcTemplate jdbc, UserRepository users, RewardRepository rewards, WalletService wallet,
                             QuoteService quotes, NotificationService notifications, TimeService time) {
        this.jdbc = jdbc;
        this.users = users;
        this.rewards = rewards;
        this.wallet = wallet;
        this.quotes = quotes;
        this.notifications = notifications;
        this.time = time;
    }

    private record Goal(String rewardId, Instant selectedAt, Instant notifiedAt) {}
    public record RewardInfo(String id, String name, String image, String fulfillment, boolean active) {}
    public record GoalView(RewardInfo reward, Instant selectedAt, Instant notifiedAt, Integer targetPrice, int balance,
                           Integer missingPoints, int progressPercent, boolean ready, String status, List<String> issues,
                           int flashDiscount, int tierDiscount) {}
    public record Result(GoalView goal) {}

    private Goal load(String userId) {
        var rows = jdbc.query("SELECT reward_id, selected_at, notified_at FROM saving_goals WHERE user_id = ?",
                (rs, row) -> new Goal(rs.getString(1), rs.getTimestamp(2).toInstant(),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()), userId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Transactional(readOnly = true)
    public Result get(String userId) {
        Goal goal = load(userId);
        if (goal == null) return new Result(null);
        UserProfile user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        return new Result(view(user, goal));
    }

    @Transactional
    public Result set(String userId, String rewardId) {
        UserProfile user = wallet.lock(userId);
        rewards.findById(rewardId).filter(Reward::isActive)
                .orElseThrow(() -> ApiException.notFound("Premiul nu a fost găsit."));
        jdbc.update("""
                INSERT INTO saving_goals (user_id, reward_id, selected_at) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET reward_id = excluded.reward_id,
                    selected_at = CASE WHEN saving_goals.reward_id = excluded.reward_id
                        THEN saving_goals.selected_at ELSE excluded.selected_at END,
                    notified_at = CASE WHEN saving_goals.reward_id = excluded.reward_id
                        THEN saving_goals.notified_at ELSE NULL END
                """, userId, rewardId, Timestamp.from(time.now()));
        return new Result(check(user, load(userId)));
    }

    @Transactional
    public void clear(String userId) {
        wallet.lock(userId);
        jdbc.update("DELETE FROM saving_goals WHERE user_id = ?", userId);
    }

    /** Fiecare verificare are tranzacție proprie; blocarea contului împiedică alertele duplicate. */
    @Transactional
    public void check(String userId) {
        if (load(userId) == null) return;
        UserProfile user = wallet.lock(userId);
        Goal goal = load(userId);
        if (goal != null) check(user, goal);
    }

    private GoalView check(UserProfile user, Goal goal) {
        GoalView view = view(user, goal);
        Instant now = time.now();
        jdbc.update("UPDATE saving_goals SET last_checked_at = ? WHERE user_id = ?", Timestamp.from(now), user.getId());
        if (goal.notifiedAt() == null && view.ready()) {
            boolean sent = notifications.notify(user.getId(), "GOAL", "Obiectiv atins: " + view.reward().name(),
                    "Ai suficiente AP pentru premiul țintă, estimat acum la " + view.targetPrice()
                            + " AP. Verifică prețul și stocul la finalizarea comenzii.", "🎯",
                    "#/produs/" + UriUtils.encodePathSegment(goal.rewardId(), StandardCharsets.UTF_8));
            if (sent) {
                jdbc.update("UPDATE saving_goals SET notified_at = ? WHERE user_id = ?", Timestamp.from(now), user.getId());
                return view(user, new Goal(goal.rewardId(), goal.selectedAt(), now));
            }
        }
        return view;
    }

    private GoalView view(UserProfile user, Goal goal) {
        var priced = quotes.quote(user, List.of(new PricingService.CartLine(goal.rewardId(), 1)), null, false);
        var quote = priced.quote();
        Reward reward = priced.rewards().get(goal.rewardId());
        if (reward == null) throw ApiException.notFound("Premiul nu mai există.");
        boolean active = reward.isActive();
        boolean ready = active && quote.valid() && quote.missingPoints() == 0;
        int percent = !active ? 0 : quote.total() == 0 ? 100
                : (int) Math.min(100, user.getActivityPoints() * 100L / quote.total());
        return new GoalView(new RewardInfo(reward.getId(), reward.getName(), reward.getImage(), reward.getFulfillment(), active),
                goal.selectedAt(), goal.notifiedAt(), active ? quote.total() : null, user.getActivityPoints(),
                active ? quote.missingPoints() : null, percent, ready,
                !active ? "UNAVAILABLE" : !quote.valid() ? "BLOCKED" : ready ? "READY" : "SAVING",
                quote.issues(), quote.flashDiscount(), quote.tierDiscount());
    }
}
