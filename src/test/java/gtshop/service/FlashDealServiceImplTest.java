package gtshop.service;

import gtshop.model.Reward;
import gtshop.repository.RewardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlashDealServiceImplTest {

    @Mock
    private RewardRepository rewardRepository;

    @InjectMocks
    private FlashDealServiceImpl flashDealService;

    @Test
    @DisplayName("getActiveFlashDeals returns active deals with 20% discount and expiry")
    void testGetActiveFlashDeals() {
        Reward r1 = new Reward("rew-mouse", "Mouse Logitech", "Mouse desc", "Full desc", 350, "", "Premii fizice", true, 20, false);
        Reward r2 = new Reward("rew-templatecv", "Template CV", "CV desc", "Full desc", 900, "", "Premii digitale", true, 9999, false);

        when(rewardRepository.findById("rew-mouse")).thenReturn(Optional.of(r1));
        when(rewardRepository.findById("rew-templatecv")).thenReturn(Optional.of(r2));

        List<Map<String, Object>> deals = flashDealService.getActiveFlashDeals();

        assertNotNull(deals);
        assertEquals(2, deals.size());

        Map<String, Object> mouseDeal = deals.get(0);
        assertEquals(350, mouseDeal.get("normalPrice"));
        assertEquals(280, mouseDeal.get("dealPrice")); // 350 * 0.8 = 280
        assertEquals(20, mouseDeal.get("discountPercent"));
        assertNotNull(mouseDeal.get("expiresAt"));
    }
}
