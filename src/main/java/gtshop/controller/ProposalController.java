package gtshop.controller;

import gtshop.model.RewardProposal;
import gtshop.model.UserProfile;
import gtshop.service.ProposalService;
import gtshop.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/proposals")
@Tag(name = "Reward Proposals", description = "Community wish board, proposals, and upvotes")
public class ProposalController {

    private final ProposalService proposalService;
    private final UserService userService;

    public ProposalController(ProposalService proposalService, UserService userService) {
        this.proposalService = proposalService;
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get community reward proposals with vote status")
    public List<Map<String, Object>> getProposals() {
        UserProfile user = userService.getCurrentUser();
        return proposalService.getProposals(user);
    }

    @PostMapping
    @Operation(summary = "Submit a new reward proposal")
    public ResponseEntity<Map<String, Object>> createProposal(@RequestBody Map<String, String> body) {
        UserProfile user = userService.getCurrentUser();
        try {
            RewardProposal prop = proposalService.createProposal(
                user,
                body.get("title"),
                body.get("category"),
                body.get("description")
            );
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Propunerea ta a fost publicată cu succes! Ai primit +15 AP.",
                "proposal", prop,
                "newBalance", user != null ? user.getActivityPoints() : 0
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/vote")
    @Operation(summary = "Upvote or retract vote on a proposal")
    public ResponseEntity<Map<String, Object>> toggleVote(@PathVariable String id) {
        UserProfile user = userService.getCurrentUser();
        try {
            Map<String, Object> res = proposalService.toggleVote(user, id);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", ex.getMessage()));
        }
    }
}
