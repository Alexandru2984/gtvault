package gtshop.service;

import gtshop.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TierServiceImplTest {

    private TierServiceImpl tierService;

    @BeforeEach
    void setUp() {
        tierService = new TierServiceImpl();
    }

    @Test
    @DisplayName("Null user should return default Bronze level 1 tier")
    void testCalculateTier_NullUser() {
        Map<String, Object> tier = tierService.calculateTierAndLevel(null);
        assertEquals(1, tier.get("level"));
        assertEquals("BRONZE", tier.get("tier"));
        assertEquals("Junior Coder", tier.get("title"));
        assertEquals(0, tier.get("discountPercent"));
    }

    @Test
    @DisplayName("User with < 1000 points should be Level 1 Bronze")
    void testCalculateTier_Level1() {
        UserProfile user = new UserProfile("u1", "Cadet", "avatar.png", 500);
        user.setTotalPointsEarned(500);

        Map<String, Object> res = tierService.calculateTierAndLevel(user);
        assertEquals(1, res.get("level"));
        assertEquals("BRONZE", res.get("tier"));
        assertEquals("Junior Coder", res.get("title"));
        assertEquals(0, res.get("discountPercent"));
        assertEquals(50, res.get("progressPercent"));
    }

    @Test
    @DisplayName("User with 1500 points should be Level 2 Silver with 3% discount")
    void testCalculateTier_Level2() {
        UserProfile user = new UserProfile("u2", "Explorer", "avatar.png", 1500);
        user.setTotalPointsEarned(1500);

        Map<String, Object> res = tierService.calculateTierAndLevel(user);
        assertEquals(2, res.get("level"));
        assertEquals("SILVER", res.get("tier"));
        assertEquals("Tech Explorer", res.get("title"));
        assertEquals(3, res.get("discountPercent"));
    }

    @Test
    @DisplayName("User with 3000 points should be Level 3 Gold with 5% discount")
    void testCalculateTier_Level3() {
        UserProfile user = new UserProfile("u3", "Artisan", "avatar.png", 3000);
        user.setTotalPointsEarned(3000);

        Map<String, Object> res = tierService.calculateTierAndLevel(user);
        assertEquals(3, res.get("level"));
        assertEquals("GOLD", res.get("tier"));
        assertEquals("Code Artisan", res.get("title"));
        assertEquals(5, res.get("discountPercent"));
    }

    @Test
    @DisplayName("User with 7500 points should be Level 4 Platinum with 7% discount")
    void testCalculateTier_Level4() {
        UserProfile user = new UserProfile("u4", "Hacker", "avatar.png", 7500);
        user.setTotalPointsEarned(7500);

        Map<String, Object> res = tierService.calculateTierAndLevel(user);
        assertEquals(4, res.get("level"));
        assertEquals("PLATINUM", res.get("tier"));
        assertEquals("Full-Stack Hacker", res.get("title"));
        assertEquals(7, res.get("discountPercent"));
    }

    @Test
    @DisplayName("User with 15000 points should be Level 5 Diamond with 10% discount")
    void testCalculateTier_Level5() {
        UserProfile user = new UserProfile("u5", "Architect", "avatar.png", 15000);
        user.setTotalPointsEarned(15000);

        Map<String, Object> res = tierService.calculateTierAndLevel(user);
        assertEquals(5, res.get("level"));
        assertEquals("DIAMOND", res.get("tier"));
        assertEquals("Tech Architect", res.get("title"));
        assertEquals(10, res.get("discountPercent"));
        assertEquals(100, res.get("progressPercent"));
    }
}
