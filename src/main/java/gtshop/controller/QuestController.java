package gtshop.controller;

import gtshop.model.UserProfile;
import gtshop.model.UserQuest;
import gtshop.service.QuestService;
import gtshop.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quests")
@Tag(name = "Daily Quests", description = "Daily missions, bounty rewards, and activity tracking")
public class QuestController {

    private final QuestService questService;
    private final UserService userService;

    public QuestController(QuestService questService, UserService userService) {
        this.questService = questService;
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get daily quests and progress for current user")
    public List<UserQuest> getDailyQuests() {
        UserProfile user = userService.getCurrentUser();
        return questService.getDailyQuests(user);
    }

    @PostMapping("/{id}/claim")
    @Operation(summary = "Claim reward for completed daily quest")
    public ResponseEntity<Map<String, Object>> claimQuest(@PathVariable String id) {
        UserProfile user = userService.getCurrentUser();
        Map<String, Object> res = new HashMap<>();
        try {
            UserQuest quest = questService.claimQuest(user, id);
            res.put("success", true);
            res.put("message", "Felicitări! Ai revendicat " + quest.getRewardPoints() + " AP pentru misiunea: " + quest.getTitle());
            res.put("rewardPoints", quest.getRewardPoints());
            res.put("newBalance", user.getActivityPoints());
            res.put("quest", quest);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            res.put("success", false);
            res.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(res);
        }
    }

    @PostMapping("/track-favorite")
    @Operation(summary = "Track user adding an item to wishlist")
    public ResponseEntity<Map<String, Object>> trackFavorite() {
        UserProfile user = userService.getCurrentUser();
        questService.trackAction(user, "FAVORITE_ITEM", 1);
        return ResponseEntity.ok(Map.of("success", true));
    }
}
