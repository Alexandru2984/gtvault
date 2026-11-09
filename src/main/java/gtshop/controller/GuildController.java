package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.GuildService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guilds")
@Tag(name = "Guilds", description = "Ghilde și războiul săptămânal al ghildelor")
public class GuildController {
    private final GuildService guilds;
    private final CurrentUser currentUser;

    public GuildController(GuildService guilds, CurrentUser currentUser) {
        this.guilds = guilds;
        this.currentUser = currentUser;
    }

    public record GuildChoice(String guildId) {}

    @GetMapping
    @Operation(summary = "Clasamentul ghildelor pentru săptămâna curentă")
    public Map<String, Object> overview() {
        return guilds.overview();
    }

    @GetMapping("/me")
    @Operation(summary = "Ghilda utilizatorului curent")
    public Map<String, Object> mine() {
        return guilds.mine(currentUser.requireId());
    }

    @PostMapping("/select")
    @RateLimit(name = "guild-select", limit = 10, windowSeconds = 60)
    @Operation(summary = "Alătură-te unei ghilde (cooldown 7 zile)")
    public Map<String, Object> select(@RequestBody GuildChoice body) {
        return guilds.select(currentUser.requireId(), body.guildId());
    }
}
