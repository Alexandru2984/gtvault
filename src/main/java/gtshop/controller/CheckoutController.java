package gtshop.controller;

import gtshop.model.CheckoutRequest;
import gtshop.model.CheckoutResponse;
import gtshop.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
@Tag(name = "Checkout", description = "Order checkout endpoint")
public class CheckoutController {
    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping
    @Operation(summary = "Checkout cart items")
    public CheckoutResponse checkout(@RequestBody CheckoutRequest req) {
        return this.checkoutService.checkout(req.getItems(), req.getPromoCode());
    }
}
