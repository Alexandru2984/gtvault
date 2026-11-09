package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
@Tag(name = "Leaderboard", description = "Clasamente pe săptămână, lună, all-time și streak")
public class LeaderboardController {
    private final LeaderboardService leaderboard;
    private final CurrentUser currentUser;

    public LeaderboardController(LeaderboardService leaderboard, CurrentUser currentUser) {
        this.leaderboard = leaderboard;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Clasament: period = week | month | all | streak")
    public Map<String, Object> get(@RequestParam(defaultValue = "week") String period,
                                   @RequestParam(defaultValue = "20") int limit) {
        return leaderboard.leaderboard(period, limit, currentUser.optionalId());
    }
}
