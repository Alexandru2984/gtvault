package gtshop.controller;

import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gamification")
@Tag(name = "Gamification", description = "Daily streak, tech quiz, and community leaderboard")
public class GamificationController {
    private final UserService userService;
    private final UserRepository userRepository;
    private final gtshop.service.BadgeService badgeService;
    private final gtshop.service.TransactionService transactionService;

    // Daily quiz pool with educational questions
    private static final List<QuizQuestion> QUIZ_BANK = List.of(
        new QuizQuestion(
            "1",
            "Ce adnotare Spring Boot este folosită pentru a marca un bean drept serviciu de business?",
            List.of("@Service", "@Component", "@Repository", "@Controller"),
            0,
            "Adnotarea @Service este o specializare a lui @Component dedicată logicii de business."
        ),
        new QuizQuestion(
            "2",
            "În Java, care dintre următoarele colecții păstrează elementele ordonate conform cheii naturale?",
            List.of("TreeMap", "HashMap", "LinkedHashMap", "HashSet"),
            0,
            "TreeMap implementează SortedMap și menține elementele sortate pe baza cheii."
        ),
        new QuizQuestion(
            "3",
            "Ce metodă HTTP este considerată idempotentă și sigură (safe)?",
            List.of("GET", "POST", "PATCH", "CONNECT"),
            0,
            "Metoda GET este atât sigură (nu modifică starea serverului), cât și idempotentă."
        ),
        new QuizQuestion(
            "4",
            "Ce tip de blocare asigură că două tranzacții concurente nu pot suprascrie stocul aceluiași produs?",
            List.of("Pessimistic Lock (SELECT FOR UPDATE)", "No Lock", "Read Uncommitted", "Dirty Read"),
            0,
            "Pessimistic Write Lock blochează rândul la nivel de DB până la finalizarea tranzacției."
        ),
        new QuizQuestion(
            "5",
            "Ce este un JWT (JSON Web Token)?",
            List.of("Un token compact și sigur pentru transmiterea de claim-uri între părți", "O bază de date NoSQL", "Un framework CSS", "Un protocol de rețea"),
            0,
            "JWT este un standard deschis (RFC 7519) pentru transmiterea securizată de informații ca JSON."
        )
    );

    public GamificationController(UserService userService,
                                  UserRepository userRepository,
                                  gtshop.service.BadgeService badgeService,
                                  gtshop.service.TransactionService transactionService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.badgeService = badgeService;
        this.transactionService = transactionService;
    }

    private String getTodayString() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @GetMapping("/status")
    @Operation(summary = "Get daily streak and challenge status")
    public Map<String, Object> getStatus() {
        UserProfile user = this.userService.getCurrentUser();
        String today = getTodayString();

        boolean canClaimDaily = !today.equals(user.getLastDailyClaimDate());
        boolean canPlayQuiz = !today.equals(user.getLastQuizDate());

        int currentStreak = user.getStreakDays();
        // Calculate next daily bonus: 50 -> 75 -> 100 -> 150 -> 200 -> 250 -> 500
        int nextBonus = getBonusForStreak(canClaimDaily ? currentStreak + 1 : currentStreak);

        Map<String, Object> resp = new HashMap<>();
        resp.put("userId", user.getId());
        resp.put("activityPoints", user.getActivityPoints());
        resp.put("streakDays", currentStreak);
        resp.put("canClaimDaily", canClaimDaily);
        resp.put("canPlayQuiz", canPlayQuiz);
        resp.put("nextDailyBonus", nextBonus);
        resp.put("todayDate", today);
        return resp;
    }

    @PostMapping("/daily-claim")
    @Operation(summary = "Claim daily login streak bonus")
    public ResponseEntity<Map<String, Object>> claimDaily() {
        UserProfile user = this.userService.getCurrentUser();
        String today = getTodayString();

        Map<String, Object> resp = new HashMap<>();
        if (today.equals(user.getLastDailyClaimDate())) {
            resp.put("success", false);
            resp.put("message", "Ai revendicat deja bonusul zilnic pentru astăzi! Revino mâine.");
            return ResponseEntity.badRequest().body(resp);
        }

        int newStreak = user.getStreakDays() + 1;
        int bonus = getBonusForStreak(newStreak);

        user.setStreakDays(newStreak);
        user.setLastDailyClaimDate(today);
        user.setActivityPoints(user.getActivityPoints() + bonus);
        this.userRepository.save(user);

        // Check and unlock streak badges
        this.badgeService.checkStreakBadges(user, newStreak);

        // Record point ledger transaction
        this.transactionService.recordTransaction(user, "DAILY_STREAK", bonus, user.getActivityPoints(),
            "Bonus Daily Streak: Ziua " + newStreak);

        resp.put("success", true);
        resp.put("message", "Felicitări! Ai primit " + bonus + " AP pentru ziua " + newStreak + " de streak!");
        resp.put("bonusPoints", bonus);
        resp.put("bonusAwarded", bonus);
        resp.put("streakDays", newStreak);
        resp.put("newBalance", user.getActivityPoints());
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/quiz")
    @Operation(summary = "Get daily tech challenge quiz")
    public Map<String, Object> getDailyQuiz() {
        UserProfile user = this.userService.getCurrentUser();
        String today = getTodayString();
        boolean alreadyAnswered = today.equals(user.getLastQuizDate());

        // Pick question based on day of year
        int dayIndex = LocalDate.now().getDayOfYear() % QUIZ_BANK.size();
        QuizQuestion q = QUIZ_BANK.get(dayIndex);

        Map<String, Object> resp = new HashMap<>();
        resp.put("questionId", q.id);
        resp.put("question", q.question);
        resp.put("options", q.options);
        resp.put("alreadyAnswered", alreadyAnswered);
        resp.put("rewardPoints", 100);
        return resp;
    }

    @PostMapping("/quiz/submit")
    @Operation(summary = "Submit answer for daily quiz")
    public ResponseEntity<Map<String, Object>> submitQuiz(@RequestBody Map<String, Object> body) {
        UserProfile user = this.userService.getCurrentUser();
        String today = getTodayString();

        Map<String, Object> resp = new HashMap<>();
        if (today.equals(user.getLastQuizDate())) {
            resp.put("success", false);
            resp.put("message", "Ai participat deja la testul tehnic de astăzi! Revino mâine pentru o nouă întrebare.");
            return ResponseEntity.badRequest().body(resp);
        }

        int selectedIndex = body.containsKey("answerIndex") ? ((Number) body.get("answerIndex")).intValue() : -1;
        int dayIndex = LocalDate.now().getDayOfYear() % QUIZ_BANK.size();
        QuizQuestion q = QUIZ_BANK.get(dayIndex);

        boolean isCorrect = (selectedIndex == q.correctIndex);
        user.setLastQuizDate(today);

        if (isCorrect) {
            int rewardPoints = 100;
            user.setActivityPoints(user.getActivityPoints() + rewardPoints);
            this.userRepository.save(user);

            // Unlock quiz champion badge
            this.badgeService.checkQuizBadge(user);

            // Record point ledger transaction
            this.transactionService.recordTransaction(user, "QUIZ_REWARD", rewardPoints, user.getActivityPoints(),
                "Recompensă Quiz Tehnic: Întrebarea " + q.id);

            resp.put("success", true);
            resp.put("isCorrect", true);
            resp.put("message", "Excelent! Răspuns corect. Ai câștigat " + rewardPoints + " AP!");
            resp.put("explanation", q.explanation);
            resp.put("newBalance", user.getActivityPoints());
        } else {
            this.userRepository.save(user);
            resp.put("success", true);
            resp.put("isCorrect", false);
            resp.put("message", "Răspuns incorect! Răspunsul corect era: " + q.options.get(q.correctIndex));
            resp.put("explanation", q.explanation);
            resp.put("newBalance", user.getActivityPoints());
        }

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Get community leaderboard")
    public List<Map<String, Object>> getLeaderboard() {
        List<UserProfile> topUsers = this.userRepository.findTop10ByOrderByActivityPointsDesc();
        List<Map<String, Object>> result = new ArrayList<>();

        int rank = 1;
        for (UserProfile u : topUsers) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("rank", rank++);
            entry.put("id", u.getId());
            entry.put("name", u.getName());
            entry.put("avatar", u.getAvatar());
            entry.put("points", u.getActivityPoints());
            entry.put("streakDays", u.getStreakDays());
            result.add(entry);
        }
        return result;
    }

    @PostMapping("/spin-wheel")
    @Operation(summary = "Spin the lucky wheel for prizes")
    public ResponseEntity<Map<String, Object>> spinWheel() {
        UserProfile user = this.userService.getCurrentUser();
        Map<String, Object> resp = new HashMap<>();

        int spinCost = 50;
        if (user.getActivityPoints() < spinCost) {
            resp.put("success", false);
            resp.put("message", "Ai nevoie de cel puțin " + spinCost + " AP pentru a învârti roata!");
            return ResponseEntity.badRequest().body(resp);
        }

        user.setActivityPoints(user.getActivityPoints() - spinCost);

        // Outcomes:
        // 0: +25 AP (Net -25)
        // 1: +50 AP (Net 0)
        // 2: +100 AP (Net +50)
        // 3: +200 AP (Net +150)
        // 4: +500 AP Jackpot (Net +450)
        // 5: Bonus Badge
        int[] prizePoints = {25, 50, 100, 200, 500, 75};
        String[] prizeNames = {
            "25 AP",
            "50 AP (Garanție)",
            "100 AP (Dublu!)",
            "200 AP (Super Bonus!)",
            "JACKPOT: 500 AP!",
            "75 AP + Lucky Bonus"
        };

        int randomIndex = new Random().nextInt(prizePoints.length);
        int wonPoints = prizePoints[randomIndex];
        String prizeName = prizeNames[randomIndex];

        user.setActivityPoints(user.getActivityPoints() + wonPoints);
        this.userRepository.save(user);

        // Unlock wheel badge
        this.badgeService.checkWheelBadge(user);

        // Record fee and win in point ledger
        this.transactionService.recordTransaction(user, "LUCKY_WHEEL_FEE", -spinCost, user.getActivityPoints() - wonPoints,
            "Taxă rotire Roata Norocului");
        this.transactionService.recordTransaction(user, "LUCKY_WHEEL_WIN", wonPoints, user.getActivityPoints(),
            "Câștig Roata Norocului: " + prizeName);

        resp.put("success", true);
        resp.put("prizeIndex", randomIndex);
        resp.put("prizeName", prizeName);
        resp.put("wonPoints", wonPoints);
        resp.put("newBalance", user.getActivityPoints());
        resp.put("message", "Ai câștigat: " + prizeName + "!");
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/badges")
    @Operation(summary = "Get user badges and achievements")
    public List<gtshop.model.UserBadge> getUserBadges() {
        UserProfile user = this.userService.getCurrentUser();
        return this.badgeService.getUserBadges(user.getId());
    }

    private int getBonusForStreak(int streak) {
        if (streak <= 1) return 50;
        if (streak == 2) return 75;
        if (streak == 3) return 100;
        if (streak == 4) return 150;
        if (streak == 5) return 200;
        if (streak == 6) return 250;
        return 500; // Day 7+
    }

    private static class QuizQuestion {
        String id;
        String question;
        List<String> options;
        int correctIndex;
        String explanation;

        QuizQuestion(String id, String question, List<String> options, int correctIndex, String explanation) {
            this.id = id;
            this.question = question;
            this.options = options;
            this.correctIndex = correctIndex;
            this.explanation = explanation;
        }
    }
}
