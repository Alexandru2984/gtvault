package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.CartService;
import gtshop.service.PricingService.Quote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Cart", description = "Coșul de cumpărături (păstrat pe server)")
public class CartController {
    private final CartService cart;
    private final CurrentUser currentUser;

    public CartController(CartService cart, CurrentUser currentUser) {
        this.cart = cart;
        this.currentUser = currentUser;
    }

    public record AddItem(String rewardId, Integer quantity) {}

    public record SetQuantity(Integer quantity) {}

    @GetMapping
    @Operation(summary = "Conținutul coșului cu prețurile curente")
    public Quote view() {
        return cart.view(currentUser.requireId(), null);
    }

    @PostMapping("/items")
    @RateLimit(name = "cart", limit = 60, windowSeconds = 60)
    @Operation(summary = "Adaugă un produs (cantitatea se cumulează)")
    public Quote add(@RequestBody AddItem body) {
        return cart.add(currentUser.requireId(), body.rewardId(), body.quantity());
    }

    @PutMapping("/items/{rewardId}")
    @RateLimit(name = "cart", limit = 60, windowSeconds = 60)
    @Operation(summary = "Setează cantitatea (0 elimină produsul)")
    public Quote set(@PathVariable String rewardId, @RequestBody SetQuantity body) {
        return cart.setQuantity(currentUser.requireId(), rewardId, body.quantity());
    }

    @DeleteMapping("/items/{rewardId}")
    @RateLimit(name = "cart", limit = 60, windowSeconds = 60)
    @Operation(summary = "Elimină un produs din coș")
    public Quote remove(@PathVariable String rewardId) {
        return cart.remove(currentUser.requireId(), rewardId);
    }

    @DeleteMapping
    @Operation(summary = "Golește coșul")
    public Map<String, Object> clear() {
        cart.clear(currentUser.requireId());
        return Map.of("success", true);
    }
}
