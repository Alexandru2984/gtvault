package gtshop.controller;

import gtshop.model.ProductReview;
import gtshop.model.UserProfile;
import gtshop.service.ProductReviewService;
import gtshop.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rewards")
@Tag(name = "Reviews", description = "Product ratings and reviews")
public class ProductReviewController {
    private final ProductReviewService reviewService;
    private final UserService userService;

    public ProductReviewController(ProductReviewService reviewService, UserService userService) {
        this.reviewService = reviewService;
        this.userService = userService;
    }

    @GetMapping("/{id}/reviews")
    @Operation(summary = "Get reviews and rating summary for reward")
    public Map<String, Object> getReviews(@PathVariable("id") String rewardId) {
        return this.reviewService.getReviewsSummary(rewardId);
    }

    @PostMapping("/{id}/reviews")
    @Operation(summary = "Add a product review and star rating")
    public ResponseEntity<Map<String, Object>> addReview(
            @PathVariable("id") String rewardId,
            @RequestBody Map<String, Object> body) {
        UserProfile user = this.userService.getCurrentUser();
        int rating = body.containsKey("rating") ? ((Number) body.get("rating")).intValue() : 5;
        String comment = body.containsKey("comment") ? String.valueOf(body.get("comment")) : "";

        try {
            ProductReview saved = this.reviewService.addReview(rewardId, user, rating, comment);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Recenzia ta a fost publicată cu succes!",
                    "reviewId", saved.getId()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}
