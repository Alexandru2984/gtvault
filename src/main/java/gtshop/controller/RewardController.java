package gtshop.controller;

import gtshop.service.RewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/rewards"})
@Tag(name="Rewards", description="Reward catalog endpoints")
public class RewardController {
    private final RewardService rewardService;

    public RewardController(RewardService rewardService) {
        this.rewardService = rewardService;
    }

    @GetMapping
    @Operation(summary="Get all rewards", description="Supports search and category filtering")
    public Map<String, Object> getRewards(@RequestParam(value="search", required=false) String search, @RequestParam(value="category", required=false) String category) {
        List list = search != null || category != null ? this.rewardService.searchRewards(search, category) : this.rewardService.getAllRewards();
        HashMap<String, Object> resp = new HashMap<String, Object>();
        resp.put("rewards", list);
        return resp;
    }
}

