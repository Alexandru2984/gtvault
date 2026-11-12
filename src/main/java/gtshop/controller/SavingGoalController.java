package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.SavingGoalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/saving-goal")
@Tag(name = "Saving goal", description = "Premiul țintă și progresul meu AP")
public class SavingGoalController {
    private final SavingGoalService goals;
    private final CurrentUser user;

    public SavingGoalController(SavingGoalService goals, CurrentUser user) {
        this.goals = goals;
        this.user = user;
    }

    public record GoalRequest(@NotBlank @Size(max = 255) String rewardId) {}

    @GetMapping
    @Operation(summary = "Obiectivul meu cu prețul curent eligibil")
    public SavingGoalService.Result get() { return goals.get(user.requireId()); }

    @PutMapping
    @RateLimit(name = "saving-goal", limit = 30, windowSeconds = 86400)
    @Operation(summary = "Alege sau schimbă premiul țintă, fără rezervare de AP sau stoc")
    public SavingGoalService.Result set(@Valid @RequestBody GoalRequest request) {
        return goals.set(user.requireId(), request.rewardId());
    }

    @DeleteMapping
    @RateLimit(name = "saving-goal", limit = 30, windowSeconds = 86400)
    @Operation(summary = "Elimină obiectivul curent")
    public Map<String, Object> clear() {
        goals.clear(user.requireId());
        return Map.of("success", true);
    }
}
