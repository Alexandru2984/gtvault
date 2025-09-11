package gtshop.service;

import gtshop.model.HistorySummary;
import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;
import java.util.List;

public interface UserService {
    UserProfile getUser();
    void createUser(UserProfile user);
    List<PurchaseRecord> getHistory();
    void addPurchase(PurchaseRecord record);

    HistorySummary getHistorySummary(); // sumar pentru "Istoricul meu"
    
    // Metodă nouă pentru actualizarea punctelor
    boolean updateUserPoints(String userId, int newPoints);
}
