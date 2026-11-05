package gtshop.controller;

import gtshop.model.UserProfile;
import gtshop.service.GuildService;
import gtshop.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/guilds")
@Tag(name = "Community Guilds", description = "Track factions, guild wars, and leaderboard")
public class GuildController {

    private final GuildService guildService;
    private final UserService userService;

    public GuildController(GuildService guildService, UserService userService) {
        this.guildService = guildService;
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get community track guilds leaderboard and stats")
    public List<Map<String, Object>> getGuilds() {
        return guildService.getGuildsOverview();
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user guild")
    public Map<String, Object> getMyGuild() {
        UserProfile user = userService.getCurrentUser();
        return guildService.getUserGuild(user);
    }

    @PostMapping("/select")
    @Operation(summary = "Select or switch user track guild")
    public ResponseEntity<Map<String, Object>> selectGuild(@RequestBody Map<String, String> body) {
        UserProfile user = userService.getCurrentUser();
        String guildId = body.get("guildId");
        try {
            Map<String, Object> result = guildService.selectGuild(user, guildId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }
}
