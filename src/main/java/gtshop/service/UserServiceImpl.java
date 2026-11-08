package gtshop.service;

import gtshop.model.HistorySummary;
import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;
import gtshop.repository.PurchaseRecordRepository;
import gtshop.repository.UserRepository;
import gtshop.security.CurrentUser;
import gtshop.web.ApiException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PurchaseRecordRepository purchaseRecordRepository;
    private final CurrentUser currentUser;

    public UserServiceImpl(UserRepository userRepository, PurchaseRecordRepository purchaseRecordRepository, CurrentUser currentUser) {
        this.userRepository = userRepository;
        this.purchaseRecordRepository = purchaseRecordRepository;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile getCurrentUser() {
        String id = currentUser.requireId();
        return userRepository.findById(id).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfile getUserById(String userId) {
        return this.userRepository.findById(userId).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PurchaseRecord> getHistory() {
        UserProfile user = this.getCurrentUser();
        return this.purchaseRecordRepository.findByUserOrderByPurchaseDateDesc(user);
    }

    @Override
    public void addPurchase(PurchaseRecord record) {
        this.purchaseRecordRepository.save(record);
    }

    @Override
    @Transactional(readOnly = true)
    public HistorySummary getHistorySummary() {
        List<PurchaseRecord> history = this.getHistory();
        HistorySummary s = new HistorySummary();
        s.setPurchases(history);
        s.setTotalPoints(history.stream().mapToInt(PurchaseRecord::getPointsSpent).sum());
        Map<String, Integer> counts = history.stream()
                .collect(Collectors.toMap(p -> String.valueOf(p.getRewardName()), p -> 1, Integer::sum));
        s.setCountsByReward(counts);
        s.setTotalItems(counts.values().stream().mapToInt(Integer::intValue).sum());
        return s;
    }
}
