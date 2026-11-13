package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.RepeatOrderService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart/repeat-order")
public class RepeatOrderController {
    private final RepeatOrderService repeat;
    private final CurrentUser currentUser;

    public RepeatOrderController(RepeatOrderService repeat, CurrentUser currentUser) {
        this.repeat = repeat;
        this.currentUser = currentUser;
    }

    public record Confirmation(@NotBlank @Pattern(regexp = "[0-9a-f]{64}") String version) {}

    @GetMapping("/{id}")
    @RateLimit(name = "repeat-order-read", limit = 60, windowSeconds = 60)
    @Operation(summary = "Previzualizează adăugarea produselor unei comenzi proprii la coșul curent")
    public RepeatOrderService.Preview preview(@PathVariable String id) {
        return repeat.preview(currentUser.requireId(), id);
    }

    @PostMapping("/{id}")
    @RateLimit(name = "cart", limit = 60, windowSeconds = 60)
    @Operation(summary = "Adaugă integral produsele comenzii, numai dacă previzualizarea este încă actuală")
    public RepeatOrderService.Result add(@PathVariable String id, @Valid @RequestBody Confirmation request) {
        return repeat.add(currentUser.requireId(), id, request.version());
    }
}
