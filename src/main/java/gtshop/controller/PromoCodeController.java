package gtshop.controller;

import gtshop.service.PromoCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/promo")
@Tag(name = "Promo", description = "Discount codes validation")
public class PromoCodeController {
    private final PromoCodeService promoCodeService;

    public PromoCodeController(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate promo code for cart total")
    public ResponseEntity<PromoCodeService.PromoValidationResult> validatePromo(@RequestBody Map<String, Object> body) {
        String code = body.containsKey("code") ? String.valueOf(body.get("code")) : "";
        int orderTotal = body.containsKey("orderTotal") ? ((Number) body.get("orderTotal")).intValue() : 0;

        PromoCodeService.PromoValidationResult result = this.promoCodeService.validatePromo(code, orderTotal);
        if (!result.isValid()) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }
}
