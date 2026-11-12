package gtshop.game;

import static org.assertj.core.api.Assertions.assertThat;

import gtshop.model.PromoCode;
import gtshop.model.Reward;
import gtshop.service.PricingService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PricingBoundaryTest {
    private PricingService.Quote quote(int lines, PromoCode promo) {
        var rewards = new HashMap<String, Reward>();
        var cart = new ArrayList<PricingService.CartLine>();
        for (int i = 0; i < lines; i++) {
            String id = "bulk-" + i;
            Reward reward = new Reward(id, "Premiu", "Detalii", "Detalii", 1_000_000, "", "Digital", true, 1000, false);
            reward.setMaxPerOrder(99);
            rewards.put(id, reward);
            cart.add(new PricingService.CartLine(id, 99));
        }
        return new PricingService().price(cart, new PricingService.Context(10_000, 50_000_000, rewards,
                Map.of(), Map.of(), Set.of(), promo, promo == null ? null : promo.getCode(), false, Instant.parse("2026-11-12T00:00:00Z")));
    }

    @Test
    void tierPercentageDoesNotOverflowAtTheLargestAllowedCart() {
        var quote = quote(20, null);
        assertThat(quote.valid()).isTrue();
        assertThat(quote.subtotal()).isEqualTo(1_980_000_000);
        assertThat(quote.tierDiscount()).isEqualTo(198_000_000);
        assertThat(quote.total()).isEqualTo(1_782_000_000);
    }

    @Test
    void percentagePromoKeepsAnAffordableLargeOrderCorrectlyPriced() {
        PromoCode promo = new PromoCode();
        promo.setCode("LARGE");
        promo.setDiscountPercent(90);
        promo.setActive(true);
        promo.setMaxUses(100);
        var quote = quote(3, promo);
        assertThat(quote.valid()).isTrue();
        assertThat(quote.subtotal()).isEqualTo(297_000_000);
        assertThat(quote.tierDiscount()).isEqualTo(29_700_000);
        assertThat(quote.promoDiscount()).isEqualTo(240_570_000);
        assertThat(quote.total()).isEqualTo(26_730_000);
        assertThat(quote.missingPoints()).isZero();
    }
}
