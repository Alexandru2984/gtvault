package gtshop.service;

import gtshop.model.PromoCode;
import gtshop.repository.PromoCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional
public class PromoCodeServiceImpl implements PromoCodeService {
    private final PromoCodeRepository promoRepository;

    public PromoCodeServiceImpl(PromoCodeRepository promoRepository) {
        this.promoRepository = promoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PromoValidationResult validatePromo(String code, int orderTotal) {
        if (code == null || code.trim().isEmpty()) {
            return new PromoValidationResult(false, "", 0, orderTotal, "Cod promoțional invalid!");
        }

        Optional<PromoCode> opt = this.promoRepository.findByCodeIgnoreCase(code.trim());
        if (opt.isEmpty()) {
            return new PromoValidationResult(false, code, 0, orderTotal, "Codul promoțional nu există!");
        }

        PromoCode promo = opt.get();
        if (!promo.isActive()) {
            return new PromoValidationResult(false, code, 0, orderTotal, "Acest cod promoțional este inactiv!");
        }

        if (promo.getExpiresAt() != null && promo.getExpiresAt().isBefore(Instant.now())) {
            return new PromoValidationResult(false, code, 0, orderTotal, "Acest cod promoțional a expirat!");
        }

        if (promo.getCurrentUses() >= promo.getMaxUses()) {
            return new PromoValidationResult(false, code, 0, orderTotal, "Numărul maxim de utilizări pentru acest cod a fost atins!");
        }

        if (orderTotal < promo.getMinOrderPoints()) {
            return new PromoValidationResult(false, code, 0, orderTotal, 
                "Comanda minimă pentru a aplica acest cod este de " + promo.getMinOrderPoints() + " AP!");
        }

        int discount = 0;
        if (promo.getDiscountPercent() > 0) {
            discount = (orderTotal * promo.getDiscountPercent()) / 100;
        } else if (promo.getDiscountPoints() > 0) {
            discount = promo.getDiscountPoints();
        }

        // Avoid negative total
        discount = Math.min(discount, orderTotal);
        int finalTotal = orderTotal - discount;

        return new PromoValidationResult(true, promo.getCode(), discount, finalTotal, 
            "Cod aplicat cu succes! Ai economisit " + discount + " AP!");
    }

    @Override
    public int applyPromoAtCheckout(String code, int orderTotal) {
        if (code == null || code.trim().isEmpty()) {
            return 0;
        }

        PromoValidationResult validation = validatePromo(code, orderTotal);
        if (!validation.isValid()) {
            return 0;
        }

        this.promoRepository.findByCodeIgnoreCase(code.trim()).ifPresent(promo -> {
            promo.setCurrentUses(promo.getCurrentUses() + 1);
            this.promoRepository.save(promo);
        });

        return validation.getDiscountPoints();
    }
}
