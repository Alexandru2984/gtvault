package gtshop.service;

import gtshop.model.ProposalVote;
import gtshop.model.RewardProposal;
import gtshop.model.UserProfile;
import gtshop.repository.ProposalVoteRepository;
import gtshop.repository.RewardProposalRepository;
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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposalServiceImplTest {

    @Mock
    private RewardProposalRepository proposalRepository;

    @Mock
    private ProposalVoteRepository voteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionService transactionService;

    @InjectMocks
    private ProposalServiceImpl proposalService;

    private UserProfile testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserProfile("u1", "Alex Dev", "avatar.png", 500);
    }

    @Test
    @DisplayName("getProposals returns list with hasVoted flag")
    void testGetProposals() {
        RewardProposal p1 = new RewardProposal("p1", testUser, "Alex Dev", "Curs React Native", "Premii digitale", "Descriere detaliata curs", 5, "PENDING", Instant.now());
        when(proposalRepository.findAllByOrderByVotesCountDescCreatedAtDesc()).thenReturn(List.of(p1));
        when(voteRepository.findByUser(testUser)).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = proposalService.getProposals(testUser);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("p1", result.get(0).get("id"));
        assertFalse((Boolean) result.get(0).get("hasVoted"));
    }

    @Test
    @DisplayName("createProposal persists proposal and awards author +15 AP")
    void testCreateProposal_Success() {
        when(proposalRepository.save(any(RewardProposal.class))).thenAnswer(inv -> inv.getArgument(0));

        RewardProposal created = proposalService.createProposal(testUser, "Tastatura Mecanica GT", "Premii fizice", "Tastatura mecanica RGB pentru codat.");

        assertNotNull(created);
        assertEquals("Tastatura Mecanica GT", created.getTitle());
        assertEquals(1, created.getVotesCount()); // Author vote
        assertEquals(515, testUser.getActivityPoints()); // 500 + 15
        verify(voteRepository).save(any(ProposalVote.class));
        verify(userRepository).save(testUser);
        verify(transactionService).recordTransaction(eq(testUser), eq("COMMUNITY_CONTRIBUTION"), eq(15), eq(515), anyString());
    }

    @Test
    @DisplayName("toggleVote adds vote and awards +5 AP when user hasn't voted")
    void testToggleVote_AddVote() {
        RewardProposal p = new RewardProposal("p1", testUser, "Alex Dev", "Curs AWS", "Premii digitale", "Descriere detaliata", 3, "PENDING", Instant.now());
        when(proposalRepository.findById("p1")).thenReturn(Optional.of(p));
        when(voteRepository.findByProposalAndUser(p, testUser)).thenReturn(Optional.empty());

        Map<String, Object> res = proposalService.toggleVote(testUser, "p1");

        assertTrue((Boolean) res.get("success"));
        assertTrue((Boolean) res.get("hasVoted"));
        assertEquals(4, res.get("votesCount"));
        assertEquals(505, testUser.getActivityPoints()); // 500 + 5
        verify(voteRepository).save(any(ProposalVote.class));
        verify(userRepository).save(testUser);
    }
}
