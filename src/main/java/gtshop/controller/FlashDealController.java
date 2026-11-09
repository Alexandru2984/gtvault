package gtshop.controller;

import gtshop.service.FlashDealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/flash-deals")
@Tag(name = "Flash Deals", description = "Oferte limitate în timp și stoc")
public class FlashDealController {
    private final FlashDealService flashDeals;

    public FlashDealController(FlashDealService flashDeals) {
        this.flashDeals = flashDeals;
    }

    @GetMapping
    @Operation(summary = "Ofertele flash active")
    public List<FlashDealService.DealView> list() {
        flashDeals.ensureDailyDeals();
        return flashDeals.list();
    }
}
