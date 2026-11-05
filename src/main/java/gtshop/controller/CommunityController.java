package gtshop.controller;

import gtshop.service.ProductReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community")
@Tag(name = "Community", description = "Community activity and social feed")
public class CommunityController {
    private final ProductReviewService reviewService;

    public CommunityController(ProductReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/activity")
    @Operation(summary = "Get live community activity feed")
    public List<Map<String, Object>> getActivityFeed() {
        return this.reviewService.getRecentCommunityActivity();
    }
}
