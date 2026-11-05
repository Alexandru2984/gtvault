package gtshop.controller;

import gtshop.service.FlashDealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/flash-deals")
@Tag(name = "Flash Deals", description = "Limited-time discounts and happy hour specials")
public class FlashDealController {

    private final FlashDealService flashDealService;

    public FlashDealController(FlashDealService flashDealService) {
        this.flashDealService = flashDealService;
    }

    @GetMapping
    @Operation(summary = "Get current active flash deals with countdown timer")
    public List<Map<String, Object>> getFlashDeals() {
        return flashDealService.getActiveFlashDeals();
    }
}
