package gtshop.service;

import gtshop.game.Tiers;
import gtshop.model.FlashDeal;
import gtshop.model.PromoCode;
import gtshop.model.Reward;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Calculul autoritar al pretului unei comenzi. Ordinea reducerilor: flash deal (per unitate, in limita
 * stocului ofertei si a limitei per utilizator) -> reducerea de nivel (tier) -> codul promotional.
 * Este o functie pura: aceeasi logica se foloseste pentru previzualizare si pentru plata.
 */
@Service
public class PricingService {

    public record CartLine(String rewardId, int quantity) {}

    public record Line(String rewardId, String name, String image, String category, String fulfillment, int quantity,
                       int unitPrice, int dealUnitPrice, int dealUnits, String flashDealId, int lineSubtotal,
                       int lineFlashDiscount, int lineTotal, int stockCount, int maxPerOrder, String issue) {}

    public record PromoResult(String code, boolean valid, String message, int discount) {}

    public record Quote(List<Line> lines, int itemCount, int subtotal, int flashDiscount, String tier, int tierPercent,
                        int tierDiscount, PromoResult promo, int promoDiscount, int total, boolean requiresShipping,
                        List<String> issues, int balance, int missingPoints) {
        public boolean valid() {
            return issues.isEmpty() && !lines.isEmpty();
        }
    }

    /** Date necesare calculului (incarcate de apelant, eventual cu blocare). */
    public record Context(int userXp, int balance, Map<String, Reward> rewards, Map<String, FlashDeal> deals,
                          Map<String, Long> flashUnitsBought, Set<String> ownedBadgeRewards,
                          PromoCode promo, String promoInput, boolean promoAlreadyUsed, Instant now) {}

    public Quote price(List<CartLine> cart, Context ctx) {
        Tiers.Tier tier = Tiers.forXp(ctx.userXp());
        List<Line> lines = new ArrayList<>();
        List<String> issues = new ArrayList<>();
        int subtotal = 0;
        int flash = 0;
        int itemCount = 0;
        boolean shipping = false;

        for (CartLine c : cart) {
            Reward r = ctx.rewards().get(c.rewardId());
            if (r == null || !r.isActive()) {
                String issue = "Un produs din coș nu mai este disponibil și trebuie eliminat.";
                issues.add(issue);
                lines.add(new Line(c.rewardId(), r == null ? "Produs indisponibil" : r.getName(), null, null, null,
                        c.quantity(), 0, 0, 0, null, 0, 0, 0, 0, 0, issue));
                continue;
            }
            String issue = null;
            if (r.getStockCount() <= 0) {
                issue = r.getName() + ": stoc epuizat.";
            } else if (c.quantity() > r.getStockCount()) {
                issue = r.getName() + ": stoc insuficient (disponibil: " + r.getStockCount() + ").";
            } else if (c.quantity() > r.getMaxPerOrder()) {
                issue = r.getName() + ": maximum " + r.getMaxPerOrder() + " bucăți per comandă.";
            } else if (r.getMinTier() != null && tierLevel(r.getMinTier()) > tier.level()) {
                issue = r.getName() + ": disponibil începând cu nivelul " + r.getMinTier() + ".";
            } else if (Reward.FULFILLMENT_BADGE.equals(r.getFulfillment()) && ctx.ownedBadgeRewards().contains(r.getId())) {
                issue = r.getName() + ": ai deja această insignă.";
            }
            if (issue != null) {
                issues.add(issue);
            }

            int unit = r.getPrice();
            FlashDeal d = ctx.deals().get(r.getId());
            int dealUnits = 0;
            int dealPrice = unit;
            if (d != null && !d.getStartsAt().isAfter(ctx.now()) && d.getEndsAt().isAfter(ctx.now())) {
                long bought = ctx.flashUnitsBought().getOrDefault(d.getId(), 0L);
                int userLeft = (int) Math.max(0, d.getPerUserLimit() - bought);
                dealUnits = Math.max(0, Math.min(c.quantity(), Math.min(d.remaining(), userLeft)));
                dealPrice = d.dealPrice(unit);
            }
            int lineSubtotal = unit * c.quantity();
            int lineFlash = (unit - dealPrice) * dealUnits;
            lines.add(new Line(r.getId(), r.getName(), r.getImage(), r.getCategory(), r.getFulfillment(), c.quantity(),
                    unit, dealPrice, dealUnits, dealUnits > 0 ? d.getId() : null, lineSubtotal, lineFlash,
                    lineSubtotal - lineFlash, r.getStockCount(), r.getMaxPerOrder(), issue));
            subtotal += lineSubtotal;
            flash += lineFlash;
            itemCount += c.quantity();
            shipping |= r.isPhysical();
        }

        int afterFlash = subtotal - flash;
        int tierDiscount = (int) ((long) afterFlash * tier.discountPercent() / 100);
        int afterTier = afterFlash - tierDiscount;

        PromoResult promo = null;
        int promoDiscount = 0;
        if (ctx.promoInput() != null && !ctx.promoInput().isBlank()) {
            promo = evaluatePromo(ctx, afterTier);
            promoDiscount = promo.valid() ? promo.discount() : 0;
        }
        int total = Math.max(0, afterTier - promoDiscount);
        return new Quote(List.copyOf(lines), itemCount, subtotal, flash, tier.code(), tier.discountPercent(), tierDiscount,
                promo, promoDiscount, total, shipping, List.copyOf(issues), ctx.balance(),
                Math.max(0, total - ctx.balance()));
    }

    private PromoResult evaluatePromo(Context ctx, int amount) {
        String input = ctx.promoInput().trim().toUpperCase(java.util.Locale.ROOT);
        PromoCode p = ctx.promo();
        if (p == null) {
            return new PromoResult(input, false, "Codul promoțional nu există.", 0);
        }
        if (!p.isActive()) {
            return new PromoResult(p.getCode(), false, "Acest cod promoțional nu mai este activ.", 0);
        }
        if (p.getExpiresAt() != null && !p.getExpiresAt().isAfter(ctx.now())) {
            return new PromoResult(p.getCode(), false, "Acest cod promoțional a expirat.", 0);
        }
        if (p.getCurrentUses() >= p.getMaxUses()) {
            return new PromoResult(p.getCode(), false, "Codul a atins numărul maxim de utilizări.", 0);
        }
        if (ctx.promoAlreadyUsed()) {
            return new PromoResult(p.getCode(), false, "Ai folosit deja acest cod promoțional.", 0);
        }
        if (amount < p.getMinOrderPoints()) {
            return new PromoResult(p.getCode(), false,
                    "Comanda minimă pentru acest cod este de " + p.getMinOrderPoints() + " AP.", 0);
        }
        int discount;
        if (p.getDiscountPercent() > 0) {
            discount = (int) ((long) amount * p.getDiscountPercent() / 100);
            if (p.getMaxDiscount() != null && p.getMaxDiscount() > 0) {
                discount = Math.min(discount, p.getMaxDiscount());
            }
        } else {
            discount = p.getDiscountPoints();
        }
        discount = Math.max(0, Math.min(discount, amount));
        if (discount == 0) {
            return new PromoResult(p.getCode(), false, "Codul nu oferă nicio reducere pentru această comandă.", 0);
        }
        return new PromoResult(p.getCode(), true, "Cod aplicat: -" + discount + " AP.", discount);
    }

    static int tierLevel(String code) {
        return Tiers.ALL.stream().filter(t -> t.code().equalsIgnoreCase(code)).map(Tiers.Tier::level).findFirst().orElse(99);
    }
}
