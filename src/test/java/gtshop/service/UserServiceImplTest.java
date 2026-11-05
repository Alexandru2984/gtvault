package gtshop.service;

import gtshop.model.HistorySummary;
import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;
import gtshop.repository.PurchaseRecordRepository;
import gtshop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PurchaseRecordRepository purchaseRecordRepository;

    @InjectMocks
    private UserServiceImpl userService;

    private UserProfile testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserProfile("user-123", "Popescu Ion", "https://example.com/avatar.png", 2056);
    }

    @Test
    void testGetUser_Existing() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));

        UserProfile result = userService.getUser();

        assertNotNull(result);
        assertEquals("user-123", result.getId());
        assertEquals("Popescu Ion", result.getName());
    }

    @Test
    void testGetUser_CreatesDefaultIfNotPresent() {
        when(userRepository.findById("user-123")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserProfile.class))).thenAnswer(i -> i.getArgument(0));

        UserProfile result = userService.getUser();

        assertNotNull(result);
        assertEquals("user-123", result.getId());
        assertEquals(2056, result.getActivityPoints());
        verify(userRepository).save(any(UserProfile.class));
    }

    @Test
    void testGetUserById() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));

        UserProfile result = userService.getUserById("user-123");

        assertNotNull(result);
        assertEquals("user-123", result.getId());
    }

    @Test
    void testCreateUser() {
        userService.createUser(testUser);
        verify(userRepository).save(testUser);
    }

    @Test
    void testUpdateUserPoints_Success() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));

        boolean updated = userService.updateUserPoints("user-123", 3000);

        assertTrue(updated);
        assertEquals(3000, testUser.getActivityPoints());
        verify(userRepository).save(testUser);
    }

    @Test
    void testUpdateUserPoints_UserNotFound() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        boolean updated = userService.updateUserPoints("unknown", 3000);

        assertFalse(updated);
        verify(userRepository, never()).save(any());
    }

    @Test
    void testGetHistoryByUserId_UserExists() {
        PurchaseRecord record = new PurchaseRecord();
        record.setId("pr-1");
        record.setRewardName("Tricou GT");
        record.setPointsSpent(550);
        record.setPurchaseDate(Instant.now());

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(purchaseRecordRepository.findByUserOrderByPurchaseDateDesc(testUser)).thenReturn(List.of(record));

        List<PurchaseRecord> history = userService.getHistoryByUserId("user-123");

        assertEquals(1, history.size());
        assertEquals("Tricou GT", history.get(0).getRewardName());
    }

    @Test
    void testGetHistoryByUserId_UserNotFound() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        List<PurchaseRecord> history = userService.getHistoryByUserId("unknown");

        assertTrue(history.isEmpty());
    }

    @Test
    void testGetHistorySummary() {
        PurchaseRecord r1 = new PurchaseRecord();
        r1.setRewardName("Tricou GT");
        r1.setPointsSpent(550);

        PurchaseRecord r2 = new PurchaseRecord();
        r2.setRewardName("Sticker GT");
        r2.setPointsSpent(180);

        when(userRepository.findById("user-123")).thenReturn(Optional.of(testUser));
        when(purchaseRecordRepository.findByUserOrderByPurchaseDateDesc(testUser)).thenReturn(List.of(r1, r2));

        HistorySummary summary = userService.getHistorySummary();

        assertNotNull(summary);
        assertEquals(730, summary.getTotalPoints());
        assertEquals(2, summary.getTotalItems());
        assertEquals(2, summary.getPurchases().size());
        assertEquals(1, summary.getCountsByReward().get("Tricou GT"));
    }
}
