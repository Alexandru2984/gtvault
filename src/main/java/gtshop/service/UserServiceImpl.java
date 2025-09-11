package gtshop.service;

import gtshop.model.HistorySummary;
import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements UserService {
    private UserProfile user;
    private final List<PurchaseRecord> history = new ArrayList<>();

    @Override public UserProfile getUser() { return user; }
    @Override public void createUser(UserProfile user) { this.user = user; }
    @Override public List<PurchaseRecord> getHistory() { return new ArrayList<>(history); }
    @Override public void addPurchase(PurchaseRecord record) { history.add(record); }

    @Override
    public HistorySummary getHistorySummary() {
        HistorySummary s = new HistorySummary();
        s.setPurchases(getHistory());
        int totalPoints = history.stream().mapToInt(PurchaseRecord::getPointsSpent).sum();
        s.setTotalPoints(totalPoints);
        Map<String, Integer> counts = history.stream()
                .collect(Collectors.toMap(PurchaseRecord::getRewardName, p -> 1, Integer::sum));
        int totalItems = counts.values().stream().mapToInt(Integer::intValue).sum();
        s.setCountsByReward(counts);
        s.setTotalItems(totalItems);
        return s;
    }

    @Override
    public boolean updateUserPoints(String userId, int newPoints) {
        if (user != null && user.getId().equals(userId)) {
            user.setActivityPoints(newPoints);
            return true;
        }
        return false;
    }
}
