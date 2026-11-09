package gtshop.controller;

import gtshop.game.Economy;
import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.BadgeService;
import gtshop.service.DailyRewardsService;
import gtshop.service.GamesService;
import gtshop.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gamification")
@Tag(name = "Gamification", description = "Streak, quiz, răvaș, roată, cufere, insigne și clasament")
public class GamificationController {
    private final DailyRewardsService daily;
    private final GamesService games;
    private final BadgeService badges;
    private final LeaderboardService leaderboard;
    private final CurrentUser currentUser;

    public GamificationController(DailyRewardsService daily, GamesService games, BadgeService badges,
                                  LeaderboardService leaderboard, CurrentUser currentUser) {
        this.daily = daily;
        this.games = games;
        this.badges = badges;
        this.leaderboard = leaderboard;
        this.currentUser = currentUser;
    }

    public record QuizAnswer(String questionId, Integer answerIndex) {}

    @GetMapping("/config")
    @Operation(summary = "Regulile publice ale economiei (inclusiv probabilitățile reale)")
    public Map<String, Object> config() {
        return Economy.publicConfig();
    }

    @GetMapping("/status")
    @Operation(summary = "Starea activităților zilnice")
    public Map<String, Object> status() {
        return daily.status(currentUser.requireId());
    }

    @PostMapping("/daily-claim")
    @RateLimit(name = "daily-claim", limit = 10, windowSeconds = 60)
    @Operation(summary = "Revendică bonusul de streak de azi")
    public Map<String, Object> claimDaily() {
        return daily.claimDaily(currentUser.requireId());
    }

    @PostMapping("/streak-freeze")
    @RateLimit(name = "streak-freeze", limit = 10, windowSeconds = 60)
    @Operation(summary = "Cumpără un streak freeze")
    public Map<String, Object> buyFreeze() {
        return daily.buyStreakFreeze(currentUser.requireId());
    }

    @GetMapping("/quiz")
    @Operation(summary = "Întrebarea zilei")
    public Map<String, Object> quiz() {
        return daily.quiz(currentUser.optionalId());
    }

    @PostMapping("/quiz/submit")
    @RateLimit(name = "quiz", limit = 10, windowSeconds = 60)
    @Operation(summary = "Trimite răspunsul la quiz")
    public Map<String, Object> submitQuiz(@RequestBody QuizAnswer body) {
        return daily.submitQuiz(currentUser.requireId(), body.questionId(), body.answerIndex());
    }

    @PostMapping("/fortune-cookie")
    @RateLimit(name = "fortune", limit = 10, windowSeconds = 60)
    @Operation(summary = "Deschide răvașul zilei")
    public Map<String, Object> fortune() {
        return daily.openFortune(currentUser.requireId());
    }

    @PostMapping("/spin-wheel")
    @RateLimit(name = "spin", limit = 20, windowSeconds = 60)
    @Operation(summary = "Învârte Roata Norocului (prima rotire din zi e gratuită)")
    public Map<String, Object> spin() {
        return games.spinWheel(currentUser.requireId());
    }

    @PostMapping("/open-crate")
    @RateLimit(name = "crate", limit = 20, windowSeconds = 60)
    @Operation(summary = "Deschide un Cufăr Misterios")
    public Map<String, Object> crate() {
        return games.openCrate(currentUser.requireId());
    }

    @GetMapping("/badges")
    @Operation(summary = "Catalogul de insigne cu progresul utilizatorului")
    public Map<String, Object> badges() {
        return badges.catalog(currentUser.requireId());
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Clasament (alias pentru /api/leaderboard)")
    public Map<String, Object> leaderboard(@RequestParam(defaultValue = "week") String period,
                                           @RequestParam(defaultValue = "20") int limit) {
        return leaderboard.leaderboard(period, limit, currentUser.optionalId());
    }
}
