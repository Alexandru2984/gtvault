package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.CheckoutService;
import gtshop.service.PricingService.Quote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/checkout")
@Tag(name = "Checkout", description = "Previzualizarea și plasarea comenzii")
public class CheckoutController {
    private final CheckoutService checkout;
    private final CurrentUser currentUser;

    public CheckoutController(CheckoutService checkout, CurrentUser currentUser) {
        this.checkout = checkout;
        this.currentUser = currentUser;
    }

    public record QuoteRequest(String promoCode) {}

    @PostMapping("/quote")
    @RateLimit(name = "checkout-quote", limit = 60, windowSeconds = 60)
    @Operation(summary = "Calculează totalul (flash deal, reducere de nivel, cod promo) fără să plaseze comanda")
    public Quote quote(@RequestBody(required = false) QuoteRequest body) {
        return checkout.quote(currentUser.requireId(), body == null ? null : body.promoCode());
    }

    @PostMapping
    @RateLimit(name = "checkout", limit = 10, windowSeconds = 60)
    @Operation(summary = "Plasează comanda din coș (antet obligatoriu: Idempotency-Key)")
    public CheckoutService.CheckoutResult place(@RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                @RequestBody(required = false) CheckoutService.CheckoutRequest body) {
        return checkout.placeOrder(currentUser.requireId(), key, body);
    }
}
