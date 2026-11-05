package gtshop.service;

import gtshop.model.HistorySummary;
import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;
import gtshop.repository.PurchaseRecordRepository;
import gtshop.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PurchaseRecordRepository purchaseRecordRepository;
    private static final String DEFAULT_USER_ID = "user-123";

    public UserServiceImpl(UserRepository userRepository, PurchaseRecordRepository purchaseRecordRepository) {
        this.userRepository = userRepository;
        this.purchaseRecordRepository = purchaseRecordRepository;
    }

    public UserProfile getUser() {
        return this.userRepository.findById(DEFAULT_USER_ID).orElseGet(() -> {
            UserProfile defaultUser = new UserProfile(DEFAULT_USER_ID, "Popescu Ion", "https://example.com/avatar.png", 2056);
            return this.userRepository.save(defaultUser);
        });
    }

    public UserProfile getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String principal = auth.getName();
            UserProfile user = this.userRepository.findById(principal)
                .or(() -> this.userRepository.findByEmail(principal))
                .orElse(null);
            if (user != null) {
                return user;
            }
        }
        return this.getUser();
    }

    public UserProfile getUserById(String userId) {
        return this.userRepository.findById(userId).orElse(null);
    }

    public void createUser(UserProfile user) {
        this.userRepository.save(user);
    }

    public List<PurchaseRecord> getHistory() {
        UserProfile user = this.getCurrentUser();
        return this.purchaseRecordRepository.findByUserOrderByPurchaseDateDesc(user);
    }

    public List<PurchaseRecord> getHistoryByUserId(String userId) {
        UserProfile user = this.userRepository.findById(userId).orElse(null);
        if (user == null) {
            return List.of();
        }
        return this.purchaseRecordRepository.findByUserOrderByPurchaseDateDesc(user);
    }

    public void addPurchase(PurchaseRecord record) {
        UserProfile user = this.getCurrentUser();
        record.setUser(user);
        this.purchaseRecordRepository.save(record);
    }

    public HistorySummary getHistorySummary() {
        List<PurchaseRecord> history = this.getHistory();
        HistorySummary s = new HistorySummary();
        s.setPurchases(history);
        int totalPoints = history.stream().mapToInt(PurchaseRecord::getPointsSpent).sum();
        s.setTotalPoints(totalPoints);
        Map<String, Integer> counts = history.stream().collect(Collectors.toMap(PurchaseRecord::getRewardName, p -> 1, Integer::sum));
        int totalItems = counts.values().stream().mapToInt(Integer::intValue).sum();
        s.setCountsByReward(counts);
        s.setTotalItems(totalItems);
        return s;
    }

    public boolean updateUserPoints(String userId, int newPoints) {
        return this.userRepository.findById(userId).map(user -> {
            user.setActivityPoints(newPoints);
            this.userRepository.save(user);
            return true;
        }).orElse(false);
    }

    public void setUserPoints(int points) {
        UserProfile user = this.getCurrentUser();
        user.setActivityPoints(points);
        this.userRepository.save(user);
    }

    public int getUserPoints() {
        return this.getCurrentUser().getActivityPoints();
    }
}
