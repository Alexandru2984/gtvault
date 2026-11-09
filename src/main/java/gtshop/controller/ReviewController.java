package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Reviews", description = "Recenzii verificate, voturi „util” și raportări")
public class ReviewController {
    private final ReviewService reviews;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviews, CurrentUser currentUser) {
        this.reviews = reviews;
        this.currentUser = currentUser;
    }

    public record ReviewRequest(Integer rating, String comment) {}

    public record ReportRequest(String reason) {}

    @GetMapping("/rewards/{id}/reviews")
    @Operation(summary = "Recenziile unui produs: medie, distribuție, paginare și sortare")
    public Map<String, Object> list(@PathVariable String id, @RequestParam(required = false) String sort,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return reviews.list(id, sort, page, size, currentUser.optionalId());
    }

    @PostMapping("/rewards/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @RateLimit(name = "review-write", limit = 10, windowSeconds = 3600)
    @Operation(summary = "Scrie o recenzie (doar pentru produsele primite)")
    public Map<String, Object> create(@PathVariable String id, @RequestBody ReviewRequest body) {
        return reviews.create(currentUser.requireId(), id, body.rating(), body.comment());
    }

    @PutMapping("/rewards/{id}/reviews/mine")
    @RateLimit(name = "review-write", limit = 10, windowSeconds = 3600)
    @Operation(summary = "Editează recenzia proprie")
    public Map<String, Object> update(@PathVariable String id, @RequestBody ReviewRequest body) {
        return reviews.update(currentUser.requireId(), id, body.rating(), body.comment());
    }

    @DeleteMapping("/rewards/{id}/reviews/mine")
    @Operation(summary = "Șterge recenzia proprie")
    public Map<String, Object> delete(@PathVariable String id) {
        return reviews.delete(currentUser.requireId(), id);
    }

    @PostMapping("/reviews/{reviewId}/helpful")
    @RateLimit(name = "review-helpful", limit = 60, windowSeconds = 3600)
    @Operation(summary = "Marchează / demarchează o recenzie ca utilă")
    public Map<String, Object> helpful(@PathVariable String reviewId) {
        return reviews.toggleHelpful(currentUser.requireId(), reviewId);
    }

    @PostMapping("/reviews/{reviewId}/report")
    @RateLimit(name = "review-report", limit = 20, windowSeconds = 3600)
    @Operation(summary = "Raportează o recenzie unui moderator")
    public Map<String, Object> report(@PathVariable String reviewId, @RequestBody ReportRequest body) {
        return reviews.report(currentUser.requireId(), reviewId, body.reason());
    }

    @GetMapping("/user/reviews")
    @Operation(summary = "Recenziile mele")
    public List<ReviewService.ReviewView> mine() {
        return reviews.mine(currentUser.requireId());
    }

    @GetMapping("/user/reviews/pending")
    @Operation(summary = "Produse primite care așteaptă o recenzie")
    public List<Map<String, Object>> pending() {
        return reviews.pending(currentUser.requireId());
    }
}
