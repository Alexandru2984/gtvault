package gtshop.service;

import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GuildServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private GuildServiceImpl guildService;

    private UserProfile user1;
    private UserProfile user2;

    @BeforeEach
    void setUp() {
        user1 = new UserProfile("u1", "Knight Ion", "a1.png", 1000);
        user1.setTotalPointsEarned(1200);
        user1.setGuildId("JAVA_KNIGHTS");

        user2 = new UserProfile("u2", "Wizard Dan", "a2.png", 800);
        user2.setTotalPointsEarned(900);
        user2.setGuildId("PYTHON_WIZARDS");
    }

    @Test
    @DisplayName("getGuildsOverview should aggregate member counts and XP")
    void testGetGuildsOverview() {
        when(userRepository.findAll()).thenReturn(List.of(user1, user2));

        List<Map<String, Object>> guilds = guildService.getGuildsOverview();

        assertNotNull(guilds);
        assertEquals(4, guilds.size()); // 4 factions
        assertEquals(1, guilds.get(0).get("rank")); // Highest XP is rank 1
        assertEquals("JAVA_KNIGHTS", guilds.get(0).get("id"));
        assertEquals(1, guilds.get(0).get("members"));
    }

    @Test
    @DisplayName("selectGuild should change user guild and persist")
    void testSelectGuild_Success() {
        Map<String, Object> result = guildService.selectGuild(user1, "DEVOPS_TITANS");

        assertTrue((Boolean) result.get("success"));
        assertEquals("DEVOPS_TITANS", user1.getGuildId());
        verify(userRepository).save(user1);
    }

    @Test
    @DisplayName("selectGuild with invalid guildId should throw")
    void testSelectGuild_InvalidId() {
        assertThrows(IllegalArgumentException.class, () -> guildService.selectGuild(user1, "NON_EXISTENT_GUILD"));
        verify(userRepository, never()).save(user1);
    }
}
