package gtshop.service;

import gtshop.model.PromoCode;
import gtshop.repository.PromoCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceImplTest {

    @Mock
    private PromoCodeRepository promoRepository;

    @InjectMocks
    private PromoCodeServiceImpl promoCodeService;

    private PromoCode pointsPromo;
    private PromoCode percentPromo;

    @BeforeEach
    void setUp() {
        pointsPromo = new PromoCode("p1", "GT2026", 100, 0, 150, 10, 0, true);
        percentPromo = new PromoCode("p2", "COMMUNITY", 0, 20, 200, 10, 0, true);
    }

    @Test
    @DisplayName("Validate promo should succeed when conditions are met")
    void testValidatePromo_Success() {
        when(promoRepository.findByCodeIgnoreCase("GT2026")).thenReturn(Optional.of(pointsPromo));

        PromoCodeService.PromoValidationResult result = promoCodeService.validatePromo("GT2026", 300);

        assertTrue(result.isValid());
        assertEquals(100, result.getDiscountPoints());
        assertEquals(200, result.getFinalTotal()); // 300 - 100 = 200
    }

    @Test
    @DisplayName("Validate percentage promo calculates correct discount")
    void testValidatePromo_PercentageDiscount() {
        when(promoRepository.findByCodeIgnoreCase("COMMUNITY")).thenReturn(Optional.of(percentPromo));

        PromoCodeService.PromoValidationResult result = promoCodeService.validatePromo("COMMUNITY", 500);

        assertTrue(result.isValid());
        assertEquals(100, result.getDiscountPoints()); // 20% of 500 = 100
        assertEquals(400, result.getFinalTotal());
    }

    @Test
    @DisplayName("Validate promo fails when order total is below minimum")
    void testValidatePromo_BelowMinimum() {
        when(promoRepository.findByCodeIgnoreCase("GT2026")).thenReturn(Optional.of(pointsPromo));

        PromoCodeService.PromoValidationResult result = promoCodeService.validatePromo("GT2026", 100); // min is 150

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("Comanda minimă"));
    }

    @Test
    @DisplayName("Validate promo fails when promo is expired")
    void testValidatePromo_Expired() {
        pointsPromo.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(promoRepository.findByCodeIgnoreCase("GT2026")).thenReturn(Optional.of(pointsPromo));

        PromoCodeService.PromoValidationResult result = promoCodeService.validatePromo("GT2026", 300);

        assertFalse(result.isValid());
        assertTrue(result.getMessage().contains("a expirat"));
    }

    @Test
    @DisplayName("Apply promo at checkout increments usage count")
    void testApplyPromoAtCheckout_IncrementsUsage() {
        when(promoRepository.findByCodeIgnoreCase("GT2026")).thenReturn(Optional.of(pointsPromo));

        int discount = promoCodeService.applyPromoAtCheckout("GT2026", 300);

        assertEquals(100, discount);
        assertEquals(1, pointsPromo.getCurrentUses());
        verify(promoRepository).save(pointsPromo);
    }
}
