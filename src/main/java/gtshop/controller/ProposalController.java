package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.ProposalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/proposals")
@Tag(name = "Proposals", description = "Panoul de propuneri și voturi al comunității")
public class ProposalController {
    private final ProposalService proposals;
    private final CurrentUser currentUser;

    public ProposalController(ProposalService proposals, CurrentUser currentUser) {
        this.proposals = proposals;
        this.currentUser = currentUser;
    }

    public record NewProposal(String title, String category, String description) {}

    @GetMapping
    @Operation(summary = "Lista propunerilor (sort = top | new)")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "top") String sort) {
        return proposals.list(currentUser.optionalId(), sort);
    }

    @GetMapping("/categories")
    @Operation(summary = "Categoriile permise")
    public List<String> categories() {
        return ProposalService.CATEGORIES;
    }

    @PostMapping
    @RateLimit(name = "proposal-create", limit = 5, windowSeconds = 3600)
    @Operation(summary = "Publică o propunere nouă")
    public Map<String, Object> create(@RequestBody NewProposal body) {
        return proposals.create(currentUser.requireId(), body.title(), body.category(), body.description());
    }

    @PostMapping("/{id}/vote")
    @RateLimit(name = "proposal-vote", limit = 40, windowSeconds = 60)
    @Operation(summary = "Votează sau retrage votul")
    public Map<String, Object> vote(@PathVariable String id) {
        return proposals.toggleVote(currentUser.requireId(), id);
    }
}
