package gtshop.service;

import gtshop.model.HistorySummary;
import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;
import java.util.List;

public interface UserService {
    UserProfile getUser();

    UserProfile getCurrentUser();

    UserProfile getUserById(String id);

    void createUser(UserProfile user);

    List<PurchaseRecord> getHistory();

    List<PurchaseRecord> getHistoryByUserId(String userId);

    void addPurchase(PurchaseRecord record);

    HistorySummary getHistorySummary();

    boolean updateUserPoints(String userId, int points);

    void setUserPoints(int points);

    int getUserPoints();
}
