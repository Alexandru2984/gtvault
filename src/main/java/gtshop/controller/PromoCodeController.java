package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.CheckoutService;
import gtshop.service.PricingService.Quote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/promo")
@Tag(name = "Promo", description = "Validarea codurilor promoționale pe coșul curent")
public class PromoCodeController {
    private final CheckoutService checkout;
    private final CurrentUser currentUser;

    public PromoCodeController(CheckoutService checkout, CurrentUser currentUser) {
        this.checkout = checkout;
        this.currentUser = currentUser;
    }

    public record PromoRequest(String code) {}

    @PostMapping("/validate")
    @RateLimit(name = "promo-validate", limit = 20, windowSeconds = 60)
    @Operation(summary = "Aplică (de probă) un cod promo pe coșul curent")
    public Quote validate(@RequestBody PromoRequest body) {
        return checkout.quote(currentUser.requireId(), body == null ? null : body.code());
    }
}
