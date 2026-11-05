package gtshop.service;

import gtshop.model.CartItem;
import gtshop.model.CheckoutResponse;
import gtshop.model.PurchaseRecord;
import gtshop.model.Reward;
import gtshop.model.UserProfile;
import gtshop.repository.RewardRepository;
import gtshop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplTest {

    @Mock
    private UserService userService;

    @Mock
    private CartService cartService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RewardRepository rewardRepository;

    @InjectMocks
    private CheckoutServiceImpl checkoutService;

    private UserProfile testUser;
    private Reward testReward;

    @BeforeEach
    void setUp() {
        testUser = new UserProfile("user-1", "Test User", "avatar.png", 500);
        testReward = new Reward("rew-1", "GT T-Shirt", "Desc", "Full Desc", 200, "tshirt.png", "Fizic", true, 10, false);
    }

    @Test
    @DisplayName("Checkout should fail when cart is empty")
    void testCheckout_EmptyCart() {
        CheckoutResponse response = checkoutService.checkout(Collections.emptyList());
        assertFalse(response.isSuccess());
        assertEquals("Coșul este gol!", response.getMessage());
    }

    @Test
    @DisplayName("SEC-01: Client price tampering should be prevented and server DB price enforced")
    void testCheckout_PriceTamperingPrevented() {
        when(userService.getCurrentUser()).thenReturn(testUser);
        when(rewardRepository.findByIdForUpdate("rew-1")).thenReturn(Optional.of(testReward));

        // Malicious client claims price is 1 point instead of 200
        CartItem tamperedItem = new CartItem("rew-1", "GT T-Shirt", 1, 2);

        CheckoutResponse response = checkoutService.checkout(List.of(tamperedItem));

        assertTrue(response.isSuccess());
        // Server enforced 200 per item -> 2 * 200 = 400 total
        // User had 500 -> 500 - 400 = 100 new balance
        assertEquals(100, response.getNewBalance());
        assertEquals(200, tamperedItem.getPrice());
        verify(userRepository).save(testUser);
        verify(cartService).clearCart();
    }

    @Test
    @DisplayName("Checkout should fail when user has insufficient activity points")
    void testCheckout_InsufficientPoints() {
        testUser.setActivityPoints(50); // only 50 AP, reward is 200
        when(userService.getCurrentUser()).thenReturn(testUser);
        when(rewardRepository.findByIdForUpdate("rew-1")).thenReturn(Optional.of(testReward));

        CartItem item = new CartItem("rew-1", "GT T-Shirt", 200, 1);
        CheckoutResponse response = checkoutService.checkout(List.of(item));

        assertFalse(response.isSuccess());
        assertEquals(150, response.getMissingPoints());
        assertTrue(response.getMessage().contains("Puncte insuficiente"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Checkout should fail when reward has insufficient stock")
    void testCheckout_InsufficientStock() {
        testReward.setStockCount(1);
        when(userService.getCurrentUser()).thenReturn(testUser);
        when(rewardRepository.findByIdForUpdate("rew-1")).thenReturn(Optional.of(testReward));

        CartItem item = new CartItem("rew-1", "GT T-Shirt", 200, 5); // wants 5, only 1 in stock
        CheckoutResponse response = checkoutService.checkout(List.of(item));

        assertFalse(response.isSuccess());
        assertTrue(response.getMessage().contains("Stoc insuficient"));
    }

    @Test
    @DisplayName("Successful checkout should generate digital voucher and deduct stock and points")
    void testCheckout_SuccessWithVoucherGeneration() {
        when(userService.getCurrentUser()).thenReturn(testUser);
        when(rewardRepository.findByIdForUpdate("rew-1")).thenReturn(Optional.of(testReward));

        CartItem item = new CartItem("rew-1", "GT T-Shirt", 200, 1);
        CheckoutResponse response = checkoutService.checkout(List.of(item));

        assertTrue(response.isSuccess());
        assertEquals(300, testUser.getActivityPoints()); // 500 - 200 = 300
        assertEquals(9, testReward.getStockCount()); // 10 - 1 = 9

        ArgumentCaptor<PurchaseRecord> recordCaptor = ArgumentCaptor.forClass(PurchaseRecord.class);
        verify(userService).addPurchase(recordCaptor.capture());
        PurchaseRecord record = recordCaptor.getValue();

        assertNotNull(record.getVoucherCode());
        assertTrue(record.getVoucherCode().startsWith("GT-"));
        assertEquals("GT T-Shirt", record.getRewardName());
    }
}
