package gtshop.controller;

import gtshop.service.RewardService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/rewards")
public class RewardController {
    private final RewardService rewardService;
    public RewardController(RewardService rewardService) { this.rewardService = rewardService; }

    // Suportă /api/rewards?search=...&category=Premii%20fizice
    @GetMapping
    public Map<String,Object> getRewards(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "category", required = false) String category) {
        java.util.List<gtshop.model.Reward> list = (search != null || category != null)
                ? rewardService.searchRewards(search, category)
                : rewardService.getAllRewards();
        Map<String,Object> resp = new HashMap<>();
        resp.put("rewards", list);
        return resp;
    }
}
