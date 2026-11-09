package gtshop.controller;

import gtshop.game.Tiers;
import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.AccountService;
import gtshop.service.GiftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@Tag(name = "Account", description = "Jurnal de puncte, nivel, recomandări și cadouri")
public class AccountController {
    private final AccountService account;
    private final GiftService gifts;
    private final CurrentUser currentUser;

    public AccountController(AccountService account, GiftService gifts, CurrentUser currentUser) {
        this.account = account;
        this.gifts = gifts;
        this.currentUser = currentUser;
    }

    public record GiftRequest(String recipient, Integer amount, String message) {}

    @GetMapping("/transactions")
    @Operation(summary = "Jurnalul de puncte (filter = all | earned | games | gifts | orders)")
    public Map<String, Object> transactions(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size,
                                            @RequestParam(defaultValue = "all") String filter) {
        return account.ledger(currentUser.requireId(), page, size, filter);
    }

    @GetMapping("/stats")
    @Operation(summary = "XP pe săptămână / lună și nivelul curent")
    public Map<String, Object> stats() {
        return account.stats(currentUser.requireId());
    }

    @GetMapping("/tier")
    @Operation(summary = "Nivelul, reducerea și beneficiile")
    public Tiers.TierProgress tier() {
        return account.tier(currentUser.requireId());
    }

    @GetMapping("/referrals")
    @Operation(summary = "Codul de recomandare și prietenii invitați")
    public Map<String, Object> referrals() {
        return account.referrals(currentUser.requireId());
    }

    @GetMapping("/gift/limits")
    @Operation(summary = "Eligibilitatea și plafoanele pentru cadouri")
    public Map<String, Object> giftLimits() {
        return gifts.limits(currentUser.requireId());
    }

    @GetMapping("/gift/recipient")
    @RateLimit(name = "gift-lookup", limit = 30, windowSeconds = 60)
    @Operation(summary = "Previzualizarea destinatarului după codul GT")
    public Map<String, Object> recipient(@RequestParam String code) {
        return gifts.preview(currentUser.requireId(), code);
    }

    @PostMapping("/gift")
    @RateLimit(name = "gift-send", limit = 10, windowSeconds = 600)
    @Operation(summary = "Trimite AP cadou unui prieten")
    public Map<String, Object> gift(@RequestBody GiftRequest body) {
        return gifts.send(currentUser.requireId(), body.recipient(), body.amount(), body.message());
    }
}
