package gtshop.service;

import gtshop.model.PointTransaction;
import gtshop.model.UserProfile;
import gtshop.repository.PointTransactionRepository;
import gtshop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock
    private PointTransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TransactionServiceImpl transactionService;

    private UserProfile testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserProfile("u-1", "Test Dev", "avatar.png", 1000);
        testUser.setTotalPointsEarned(1000);
    }

    @Test
    @DisplayName("Recording positive points increases totalPointsEarned and persists transaction")
    void testRecordTransaction_PositivePoints() {
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PointTransaction tx = transactionService.recordTransaction(testUser, "DAILY_STREAK", 100, 1100, "Bonus Daily Streak: Ziua 3");

        assertNotNull(tx);
        assertEquals("DAILY_STREAK", tx.getType());
        assertEquals(100, tx.getAmount());
        assertEquals(1100, tx.getBalanceAfter());
        assertEquals(1100, testUser.getTotalPointsEarned());
        verify(userRepository).save(testUser);
        verify(transactionRepository).save(any(PointTransaction.class));
    }

    @Test
    @DisplayName("Recording negative points does not increment totalPointsEarned")
    void testRecordTransaction_NegativePoints() {
        when(transactionRepository.save(any(PointTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PointTransaction tx = transactionService.recordTransaction(testUser, "ORDER_PAYMENT", -300, 700, "Comandă #tx-123");

        assertNotNull(tx);
        assertEquals("ORDER_PAYMENT", tx.getType());
        assertEquals(-300, tx.getAmount());
        assertEquals(700, tx.getBalanceAfter());
        assertEquals(1000, testUser.getTotalPointsEarned()); // Remains unchanged
        verify(userRepository, never()).save(testUser);
        verify(transactionRepository).save(any(PointTransaction.class));
    }

    @Test
    @DisplayName("Null user should return null without errors")
    void testRecordTransaction_NullUser() {
        PointTransaction tx = transactionService.recordTransaction(null, "BONUS", 50, 50, "Test");
        assertNull(tx);
        verifyNoInteractions(transactionRepository);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("getRecentTransactions should query repository")
    void testGetRecentTransactions() {
        PointTransaction tx1 = new PointTransaction("tx-1", testUser, "DAILY_STREAK", 50, 1050, Instant.now(), "Streak Day 1");
        when(transactionRepository.findTop25ByUserOrderByCreatedAtDesc(testUser)).thenReturn(List.of(tx1));

        List<PointTransaction> result = transactionService.getRecentTransactions(testUser);
        assertEquals(1, result.size());
        assertEquals("DAILY_STREAK", result.get(0).getType());
    }

    @Test
    @DisplayName("getRecentTransactions with null user returns empty list")
    void testGetRecentTransactions_NullUser() {
        List<PointTransaction> result = transactionService.getRecentTransactions(null);
        assertTrue(result.isEmpty());
        verifyNoInteractions(transactionRepository);
    }
}
