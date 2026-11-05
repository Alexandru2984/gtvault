package gtshop.service;

import gtshop.model.ProductReview;
import gtshop.model.Reward;
import gtshop.model.UserProfile;
import gtshop.repository.ProductReviewRepository;
import gtshop.repository.PurchaseRecordRepository;
import gtshop.repository.RewardRepository;
import gtshop.repository.UserBadgeRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductReviewServiceImplTest {

    @Mock
    private ProductReviewRepository reviewRepository;

    @Mock
    private RewardRepository rewardRepository;

    @Mock
    private PurchaseRecordRepository purchaseRecordRepository;

    @Mock
    private UserBadgeRepository badgeRepository;

    @InjectMocks
    private ProductReviewServiceImpl reviewService;

    private Reward testReward;
    private UserProfile testUser;

    @BeforeEach
    void setUp() {
        testReward = new Reward("rew-1", "Tricou GT", "Desc", "Full", 550, "tricou.svg", "Fizic", true, 50, false);
        testUser = new UserProfile("u-1", "Popescu Ion", "avatar.png", 500);
    }

    @Test
    @DisplayName("Get review summary returns average rating and review list")
    void testGetReviewsSummary() {
        ProductReview r1 = new ProductReview("rev-1", testReward, testUser, "Popescu Ion", "avatar.png", 5, "Super!");
        when(reviewRepository.findByRewardIdOrderByCreatedAtDesc("rew-1")).thenReturn(List.of(r1));
        when(reviewRepository.getAverageRatingForReward("rew-1")).thenReturn(4.8);
        when(reviewRepository.countByRewardId("rew-1")).thenReturn(1L);

        Map<String, Object> summary = reviewService.getReviewsSummary("rew-1");

        assertNotNull(summary);
        assertEquals(4.8, summary.get("averageRating"));
        assertEquals(1L, summary.get("totalReviews"));
        assertEquals(1, ((List<?>) summary.get("reviews")).size());
    }

    @Test
    @DisplayName("Add review succeeds with valid rating and non-empty comment")
    void testAddReview_Success() {
        when(rewardRepository.findById("rew-1")).thenReturn(Optional.of(testReward));
        when(reviewRepository.save(any(ProductReview.class))).thenAnswer(i -> i.getArgument(0));

        ProductReview created = reviewService.addReview("rew-1", testUser, 5, "Material excelent!");

        assertNotNull(created);
        assertEquals(5, created.getRating());
        assertEquals("Material excelent!", created.getComment());
        verify(reviewRepository).save(any(ProductReview.class));
    }

    @Test
    @DisplayName("Add review fails if rating is outside 1-5")
    void testAddReview_InvalidRating() {
        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.addReview("rew-1", testUser, 6, "Comentariu");
        });
    }

    @Test
    @DisplayName("Add review fails if comment is empty")
    void testAddReview_EmptyComment() {
        assertThrows(IllegalArgumentException.class, () -> {
            reviewService.addReview("rew-1", testUser, 5, "   ");
        });
    }
}
