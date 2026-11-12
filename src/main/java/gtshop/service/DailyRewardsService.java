package gtshop.service;

import gtshop.game.Badge;
import gtshop.game.ContentBank;
import gtshop.game.Economy;
import gtshop.game.QuestCatalog.Action;
import gtshop.game.StreakMath;
import gtshop.game.Tiers;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.web.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Activitatile zilnice: bonusul de streak, freeze-uri, quiz-ul tehnic si răvașul zilei. */
@Service
public class DailyRewardsService {
    static final String QUIZ_CORRECT = "QUIZ_CORRECT";
    static final String PAID_SPINS = "WHEEL_PAID";
    static final String CRATES = "CRATES";

    private final UserRepository userRepository;
    private final WalletService wallet;
    private final DailyCounterService counters;
    private final BadgeService badges;
    private final QuestService quests;
    private final NotificationService notifications;
    private final ContentBank content;
    private final TimeService time;
    private final QuizHistoryService quizHistory;

    public DailyRewardsService(UserRepository userRepository, WalletService wallet, DailyCounterService counters,
                               BadgeService badges, QuestService quests, NotificationService notifications,
                               ContentBank content, TimeService time, QuizHistoryService quizHistory) {
        this.userRepository = userRepository;
        this.wallet = wallet;
        this.counters = counters;
        this.badges = badges;
        this.quests = quests;
        this.notifications = notifications;
        this.content = content;
        this.time = time;
        this.quizHistory = quizHistory;
    }

    // ------------------------------------------------------------------ status

    @Transactional(readOnly = true)
    public Map<String, Object> status(String userId) {
        UserProfile u = userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        LocalDate today = time.today();
        LocalDate last = StreakMath.parse(u.getLastDailyClaimDate());
        boolean claimedToday = today.equals(last);
        int effective = StreakMath.effective(last, u.getStreakDays(), u.getStreakFreezes(), today);
        StreakMath.Outcome next = claimedToday ? null : StreakMath.claim(last, u.getStreakDays(), u.getStreakFreezes(), today);
        int cycleStreak = claimedToday ? effective : next.newStreak();
        int position = Math.floorMod(Math.max(1, cycleStreak) - 1, Economy.STREAK_REWARDS.size());

        List<Map<String, Object>> week = new ArrayList<>();
        for (int i = 0; i < Economy.STREAK_REWARDS.size(); i++) {
            boolean done = i < position || (i == position && claimedToday);
            week.add(Map.of("day", i + 1, "reward", Economy.STREAK_REWARDS.get(i), "claimed", done, "today", i == position));
        }

        int paidSpins = counters.get(userId, PAID_SPINS);
        int crates = counters.get(userId, CRATES);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("userId", u.getId());
        r.put("activityPoints", u.getActivityPoints());
        r.put("xp", u.getTotalPointsEarned());
        r.put("tier", Tiers.progress(u.getTotalPointsEarned()));
        r.put("streakDays", effective);
        r.put("longestStreak", Math.max(u.getLongestStreak(), effective));
        r.put("canClaimDaily", !claimedToday);
        int upcoming = claimedToday ? effective + 1 : next.newStreak();
        r.put("nextDailyBonus", Economy.streakReward(upcoming) + Economy.STREAK_MILESTONES.getOrDefault(upcoming, 0));
        r.put("streakWillReset", next != null && next.reset());
        r.put("freezesWillBeUsed", next == null ? 0 : next.freezesUsed());
        r.put("streakFreezes", u.getStreakFreezes());
        r.put("maxStreakFreezes", Economy.MAX_FREEZES);
        r.put("streakFreezeCost", Economy.FREEZE_COST);
        r.put("weekCycle", week);
        r.put("canPlayQuiz", !today.toString().equals(u.getLastQuizDate()));
        r.put("canOpenFortune", !today.toString().equals(u.getLastFortuneDate()));
        r.put("freeSpinAvailable", !today.toString().equals(u.getLastFreeSpinDate()));
        r.put("paidSpinsLeft", Math.max(0, Economy.MAX_PAID_SPINS_PER_DAY - paidSpins));
        r.put("cratesLeft", Math.max(0, Economy.MAX_CRATES_PER_DAY - crates));
        r.put("todayDate", today.toString());
        r.put("resetsAt", time.startOfTomorrow());
        r.put("secondsUntilReset", time.secondsUntilTomorrow());
        return r;
    }

    // ------------------------------------------------------------------ streak

    @Transactional
    public Map<String, Object> claimDaily(String userId) {
        UserProfile u = wallet.lock(userId);
        LocalDate today = time.today();
        LocalDate last = StreakMath.parse(u.getLastDailyClaimDate());
        if (today.equals(last)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CLAIMED",
                    "Ai revendicat deja bonusul de azi. Revino mâine!");
        }
        StreakMath.Outcome o = StreakMath.claim(last, u.getStreakDays(), u.getStreakFreezes(), today);
        u.setStreakDays(o.newStreak());
        u.setStreakFreezes(u.getStreakFreezes() - o.freezesUsed());
        u.setLongestStreak(Math.max(u.getLongestStreak(), o.newStreak()));
        u.setLastDailyClaimDate(today.toString());

        int bonus = Economy.streakReward(o.newStreak());
        wallet.credit(u, bonus, TxType.DAILY_STREAK, "Bonus streak: ziua " + o.newStreak());
        int milestone = Economy.STREAK_MILESTONES.getOrDefault(o.newStreak(), 0);
        if (milestone > 0) {
            wallet.credit(u, milestone, TxType.DAILY_STREAK, "Bonus prag streak: " + o.newStreak() + " zile");
            notifications.notify(u.getId(), "STREAK", "Prag atins: " + o.newStreak() + " zile de streak! 🚀",
                    "Ai primit un bonus de " + milestone + " AP.", "🚀", "#/activitati");
        }
        if (o.freezesUsed() > 0) {
            notifications.notify(u.getId(), "STREAK", "Streak salvat 🛡️",
                    "Ai folosit " + o.freezesUsed() + " freeze pentru zilele ratate.", "🛡️", "#/activitati");
        }
        quests.track(u, Action.DAILY_LOGIN, 1);
        List<Badge> unlocked = badges.evaluate(u, Badge.STREAK_3, Badge.STREAK_7, Badge.STREAK_30, Badge.STREAK_100);

        String msg = o.reset()
                ? "Streak-ul a luat-o de la capăt. Ai primit " + bonus + " AP – nu te opri acum!"
                : "Ziua " + o.newStreak() + " de streak! Ai primit " + (bonus + milestone) + " AP.";
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("message", msg);
        r.put("bonusPoints", bonus + milestone);
        r.put("milestoneBonus", milestone);
        r.put("streakDays", o.newStreak());
        r.put("streakReset", o.reset());
        r.put("freezesUsed", o.freezesUsed());
        r.put("newBalance", u.getActivityPoints());
        r.put("badgesUnlocked", unlocked.stream().map(Badge::name).toList());
        return r;
    }

    @Transactional
    public Map<String, Object> buyStreakFreeze(String userId) {
        UserProfile u = wallet.lock(userId);
        if (u.getStreakFreezes() >= Economy.MAX_FREEZES) {
            throw new ApiException(HttpStatus.CONFLICT, "MAX_FREEZES",
                    "Ai deja numărul maxim de " + Economy.MAX_FREEZES + " freeze-uri.");
        }
        wallet.debit(u, Economy.FREEZE_COST, TxType.STREAK_FREEZE_PURCHASE, "Cumpărare streak freeze");
        u.setStreakFreezes(u.getStreakFreezes() + 1);
        userRepository.save(u);
        return Map.of("success", true, "message", "Freeze activat! Streak-ul tău e protejat pentru o zi ratată. 🛡️",
                "streakFreezes", u.getStreakFreezes(), "newBalance", u.getActivityPoints());
    }

    // ------------------------------------------------------------------ quiz

    @Transactional(readOnly = true)
    public Map<String, Object> quiz(Optional<String> userId) {
        LocalDate today = time.today();
        Optional<QuizHistoryService.Attempt> saved = userId.flatMap(id -> quizHistory.find(id, today));
        if (saved.isPresent()) {
            QuizHistoryService.Attempt a = saved.get();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("questionId", a.questionId());
            r.put("category", a.category());
            r.put("question", a.question());
            r.put("options", a.options());
            r.put("rewardPoints", a.rewardPoints());
            r.put("requiresLogin", false);
            r.put("alreadyAnswered", true);
            r.put("wasCorrect", a.isCorrect());
            r.put("selectedIndex", a.selectedIndex());
            r.put("correctIndex", a.correctIndex());
            r.put("explanation", a.explanation());
            r.put("resetsAt", time.startOfTomorrow());
            return r;
        }
        ContentBank.DailyQuestion dq = content.questionFor(userId.orElse("public"), today);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("questionId", dq.source().id());
        r.put("questionVersion", dq.version());
        r.put("category", dq.source().category());
        r.put("question", dq.source().question());
        r.put("options", dq.options());
        r.put("rewardPoints", Economy.QUIZ_REWARD);
        r.put("requiresLogin", userId.isEmpty());
        boolean answered = false;
        if (userId.isPresent()) {
            UserProfile u = userRepository.findById(userId.get()).orElse(null);
            answered = u != null && today.toString().equals(u.getLastQuizDate());
            if (answered) {
                r.put("wasCorrect", counters.get(u.getId(), QUIZ_CORRECT) > 0);
                r.put("correctIndex", dq.correctIndex());
                r.put("explanation", dq.source().explanation());
            }
        }
        r.put("alreadyAnswered", answered);
        r.put("resetsAt", time.startOfTomorrow());
        return r;
    }

    @Transactional
    public Map<String, Object> submitQuiz(String userId, String questionId, Integer answerIndex, String questionVersion) {
        UserProfile u = wallet.lock(userId);
        LocalDate today = time.today();
        if (today.toString().equals(u.getLastQuizDate()) || quizHistory.find(userId, today).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_ANSWERED",
                    "Ai răspuns deja la quiz-ul de azi. Revino mâine pentru o nouă întrebare!");
        }
        ContentBank.DailyQuestion dq = content.questionFor(userId, today);
        if ((questionId != null && !questionId.equals(dq.source().id()))
                || (questionVersion != null && !questionVersion.equals(dq.version()))) {
            throw new ApiException(HttpStatus.CONFLICT, "QUIZ_CHANGED", "Întrebarea zilei s-a schimbat. Reîncarcă pagina.");
        }
        if (answerIndex == null || answerIndex < 0 || answerIndex >= dq.options().size()) {
            throw ApiException.badRequest("Alege una dintre variantele de răspuns.");
        }
        boolean correct = answerIndex == dq.correctIndex();
        quizHistory.record(userId, today, dq, answerIndex, correct ? Economy.QUIZ_REWARD : 0);
        u.setLastQuizDate(today.toString());
        userRepository.save(u);
        quests.track(u, Action.SOLVE_QUIZ, 1);
        List<Badge> unlocked = List.of();
        if (correct) {
            counters.add(userId, QUIZ_CORRECT, 1);
            wallet.credit(u, Economy.QUIZ_REWARD, TxType.QUIZ_REWARD, "Quiz tehnic corect: " + dq.source().category());
            quests.track(u, Action.CORRECT_QUIZ, 1);
            unlocked = badges.evaluate(u, Badge.TECH_BRAIN, Badge.QUIZ_MASTER);
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("isCorrect", correct);
        r.put("correctIndex", dq.correctIndex());
        r.put("explanation", dq.source().explanation());
        r.put("rewardPoints", correct ? Economy.QUIZ_REWARD : 0);
        r.put("message", correct ? "Corect! Ai câștigat " + Economy.QUIZ_REWARD + " AP. 🧠"
                : "Nu de data asta. Răspunsul corect: " + dq.options().get(dq.correctIndex()));
        r.put("newBalance", u.getActivityPoints());
        r.put("badgesUnlocked", unlocked.stream().map(Badge::name).toList());
        return r;
    }

    // ------------------------------------------------------------------ răvaș

    @Transactional
    public Map<String, Object> openFortune(String userId) {
        UserProfile u = wallet.lock(userId);
        LocalDate today = time.today();
        if (today.toString().equals(u.getLastFortuneDate())) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CLAIMED", "Ai deschis deja răvașul zilei. Revino mâine!");
        }
        ContentBank.Fortune f = content.fortuneFor(userId, today);
        u.setLastFortuneDate(today.toString());
        wallet.credit(u, Economy.FORTUNE_REWARD, TxType.FORTUNE_COOKIE, "Răvașul zilei: " + f.author());
        quests.track(u, Action.FORTUNE, 1);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("quote", f.quote());
        r.put("author", f.author());
        r.put("tip", f.tip());
        r.put("bonusPoints", Economy.FORTUNE_REWARD);
        r.put("newBalance", u.getActivityPoints());
        r.put("message", "Răvaș deschis: +" + Economy.FORTUNE_REWARD + " AP!");
        return r;
    }
}
