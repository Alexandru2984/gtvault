package gtshop.service;

import gtshop.model.Reward;
import gtshop.repository.RewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RewardServiceImplTest {

    @Mock
    private RewardRepository rewardRepository;

    @InjectMocks
    private RewardServiceImpl rewardService;

    private Reward testReward;

    @BeforeEach
    void setUp() {
        testReward = new Reward("rew-tricou", "Tricou GT", "Descriere", "Full Descriere", 550, "", "Premii fizice", true, 50, false);
    }

    @Test
    void testGetAllRewards() {
        when(rewardRepository.findAllOrdered()).thenReturn(List.of(testReward));

        List<Reward> result = rewardService.getAllRewards();

        assertEquals(1, result.size());
        assertEquals("Tricou GT", result.get(0).getName());
        verify(rewardRepository).findAllOrdered();
    }

    @Test
    void testSearchRewards_WithQueryAndCategory() {
        when(rewardRepository.searchByQueryAndCategory("Tricou", "Premii fizice")).thenReturn(List.of(testReward));

        List<Reward> result = rewardService.searchRewards("Tricou", "Premii fizice");

        assertEquals(1, result.size());
        verify(rewardRepository).searchByQueryAndCategory("Tricou", "Premii fizice");
    }

    @Test
    void testSearchRewards_WithQueryOnly() {
        when(rewardRepository.searchByQuery("Tricou")).thenReturn(List.of(testReward));

        List<Reward> result = rewardService.searchRewards("Tricou", null);

        assertEquals(1, result.size());
        verify(rewardRepository).searchByQuery("Tricou");
    }

    @Test
    void testSearchRewards_WithCategoryOnly() {
        when(rewardRepository.findByCategoryIgnoreCaseOrdered("Premii fizice")).thenReturn(List.of(testReward));

        List<Reward> result = rewardService.searchRewards(null, "Premii fizice");

        assertEquals(1, result.size());
        verify(rewardRepository).findByCategoryIgnoreCaseOrdered("Premii fizice");
    }

    @Test
    void testSearchRewards_NoParams() {
        when(rewardRepository.findAllOrdered()).thenReturn(List.of(testReward));

        List<Reward> result = rewardService.searchRewards("", "  ");

        assertEquals(1, result.size());
        verify(rewardRepository).findAllOrdered();
    }

    @Test
    void testFindById_Found() {
        when(rewardRepository.findById("rew-tricou")).thenReturn(Optional.of(testReward));

        Reward result = rewardService.findById("rew-tricou");

        assertNotNull(result);
        assertEquals("rew-tricou", result.getId());
    }

    @Test
    void testFindById_NotFound() {
        when(rewardRepository.findById("inexistent")).thenReturn(Optional.empty());

        Reward result = rewardService.findById("inexistent");

        assertNull(result);
    }

    @Test
    void testSaveAll() {
        rewardService.saveAll(List.of(testReward));
        verify(rewardRepository).saveAll(anyList());
    }
}
