package gtshop.service;

import gtshop.model.UserProfile;
import gtshop.model.UserQuest;
import gtshop.repository.UserQuestRepository;
import gtshop.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuestServiceImplTest {

    @Mock
    private UserQuestRepository questRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private QuestServiceImpl questService;

    private UserProfile testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserProfile("u-1", "Tech Cadet", "avatar.png", 500);
        testUser.setLastDailyClaimDate("2026-01-01");
        testUser.setLastQuizDate("2026-01-01");
    }

    @Test
    @DisplayName("getDailyQuests should initialize 5 default quests if none exist for today")
    void testGetDailyQuests_InitializesPool() {
        when(questRepository.findByUserAndQuestDate(eq(testUser), anyString())).thenReturn(Collections.emptyList());
        when(questRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<UserQuest> quests = questService.getDailyQuests(testUser);

        assertNotNull(quests);
        assertEquals(5, quests.size());
        verify(questRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("trackAction should increment progress and mark completed when target reached")
    void testTrackAction_CompletesQuest() {
        UserQuest quest = new UserQuest("q-1", testUser, "DAILY_LOGIN", "DAILY", "Title", "Desc", 30, 0, 1, false, false, "2026-11-05", Instant.now());
        when(questRepository.findByUserAndQuestDate(eq(testUser), anyString())).thenReturn(List.of(quest));

        questService.trackAction(testUser, "DAILY_LOGIN", 1);

        assertEquals(1, quest.getCurrentProgress());
        assertTrue(quest.isCompleted());
        verify(questRepository).save(quest);
    }

    @Test
    @DisplayName("claimQuest succeeds when quest is completed and not claimed")
    void testClaimQuest_Success() {
        UserQuest quest = new UserQuest("q-1", testUser, "DAILY_LOGIN", "DAILY", "Conectare Zilnică", "Desc", 30, 1, 1, true, false, "2026-11-05", Instant.now());
        when(questRepository.findByIdAndUser("q-1", testUser)).thenReturn(Optional.of(quest));

        UserQuest claimed = questService.claimQuest(testUser, "q-1");

        assertTrue(claimed.isClaimed());
        assertEquals(530, testUser.getActivityPoints()); // 500 + 30
        verify(userRepository).save(testUser);
        verify(transactionService).recordTransaction(eq(testUser), eq("QUEST_REWARD"), eq(30), eq(530), anyString());
    }

    @Test
    @DisplayName("claimQuest throws when quest is not completed")
    void testClaimQuest_NotCompletedThrows() {
        UserQuest quest = new UserQuest("q-2", testUser, "SOLVE_QUIZ", "DAILY", "Quiz", "Desc", 40, 0, 1, false, false, "2026-11-05", Instant.now());
        when(questRepository.findByIdAndUser("q-2", testUser)).thenReturn(Optional.of(quest));

        assertThrows(IllegalStateException.class, () -> questService.claimQuest(testUser, "q-2"));
        assertFalse(quest.isClaimed());
        verify(userRepository, never()).save(testUser);
    }
}
