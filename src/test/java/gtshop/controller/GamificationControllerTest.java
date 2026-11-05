package gtshop.controller;

import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GamificationControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GamificationController gamificationController;

    private UserProfile testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserProfile("user-1", "Tech Cadet", "avatar.png", 300);
        testUser.setStreakDays(2);
        testUser.setLastDailyClaimDate("2026-01-01"); // old date
        testUser.setLastQuizDate("2026-01-01"); // old date
    }

    @Test
    @DisplayName("Status should reflect streak and available challenges")
    void testGetStatus() {
        when(userService.getCurrentUser()).thenReturn(testUser);

        Map<String, Object> status = gamificationController.getStatus();

        assertNotNull(status);
        assertEquals(2, status.get("streakDays"));
        assertTrue((Boolean) status.get("canClaimDaily"));
        assertTrue((Boolean) status.get("canPlayQuiz"));
        assertEquals(100, status.get("nextDailyBonus")); // Streak 3 bonus = 100
    }

    @Test
    @DisplayName("Claiming daily reward should increment streak and award AP")
    void testClaimDaily_Success() {
        when(userService.getCurrentUser()).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = gamificationController.claimDaily();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("success"));
        assertEquals(3, body.get("streakDays")); // incremented from 2 to 3
        assertEquals(100, body.get("bonusAwarded")); // streak 3 = 100 AP
        assertEquals(400, body.get("newBalance")); // 300 + 100 = 400
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Duplicate daily claim on same day should be rejected")
    void testClaimDaily_DuplicateFails() {
        when(userService.getCurrentUser()).thenReturn(testUser);

        // First claim
        gamificationController.claimDaily();

        // Second claim on same day
        ResponseEntity<Map<String, Object>> response = gamificationController.claimDaily();
        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertFalse((Boolean) body.get("success"));
        assertTrue(((String) body.get("message")).contains("Ai revendicat deja"));
    }

    @Test
    @DisplayName("Submitting correct quiz answer awards AP")
    void testQuizSubmit_CorrectAnswer() {
        when(userService.getCurrentUser()).thenReturn(testUser);

        // First answer in pool (index 0 is correct answer in our bank)
        ResponseEntity<Map<String, Object>> response = gamificationController.submitQuiz(Map.of("answerIndex", 0));

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("success"));
        assertTrue((Boolean) body.get("isCorrect"));
        assertEquals(400, body.get("newBalance")); // 300 + 100 = 400
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Submitting duplicate quiz answer on same day should fail")
    void testQuizSubmit_DuplicateFails() {
        when(userService.getCurrentUser()).thenReturn(testUser);

        gamificationController.submitQuiz(Map.of("answerIndex", 0));

        ResponseEntity<Map<String, Object>> response = gamificationController.submitQuiz(Map.of("answerIndex", 0));
        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertFalse((Boolean) body.get("success"));
    }

    @Test
    @DisplayName("Lucky wheel should reject if user has under 50 AP")
    void testSpinWheel_InsufficientPoints() {
        testUser.setActivityPoints(30); // less than 50
        when(userService.getCurrentUser()).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = gamificationController.spinWheel();
        assertEquals(400, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertFalse((Boolean) body.get("success"));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("Lucky wheel should succeed and update points if user has >= 50 AP")
    void testSpinWheel_Success() {
        when(userService.getCurrentUser()).thenReturn(testUser);

        ResponseEntity<Map<String, Object>> response = gamificationController.spinWheel();
        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("success"));
        assertNotNull(body.get("prizeName"));
        verify(userRepository).save(testUser);
    }

    @Test
    @DisplayName("Leaderboard should return top 10 users ranked")
    void testLeaderboard() {
        UserProfile u1 = new UserProfile("u1", "Alice", "a1.png", 1000);
        UserProfile u2 = new UserProfile("u2", "Bob", "a2.png", 800);
        when(userRepository.findTop10ByOrderByActivityPointsDesc()).thenReturn(List.of(u1, u2));

        List<Map<String, Object>> leaderboard = gamificationController.getLeaderboard();

        assertEquals(2, leaderboard.size());
        assertEquals(1, leaderboard.get(0).get("rank"));
        assertEquals("Alice", leaderboard.get(0).get("name"));
        assertEquals(2, leaderboard.get(1).get("rank"));
        assertEquals("Bob", leaderboard.get(1).get("name"));
    }
}
