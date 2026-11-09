package gtshop.controller;

import gtshop.service.CommunityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/community")
@Tag(name = "Community", description = "Activitatea comunității și statistici publice")
public class CommunityController {
    private final CommunityService community;

    public CommunityController(CommunityService community) {
        this.community = community;
    }

    @GetMapping("/activity")
    @Operation(summary = "Feed-ul live al comunității (comenzile apar anonim)")
    public List<CommunityService.FeedItem> activity(@RequestParam(defaultValue = "20") int limit) {
        return community.activity(limit);
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistici publice pentru prima pagină")
    public Map<String, Object> stats() {
        return community.stats();
    }
}
