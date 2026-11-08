package gtshop.service;

import gtshop.model.HistorySummary;
import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;
import java.util.List;

public interface UserService {
    /** Utilizatorul autentificat; arunca 401 daca cererea este anonima. Nu exista utilizator implicit. */
    UserProfile getCurrentUser();

    UserProfile getUserById(String id);

    List<PurchaseRecord> getHistory();

    void addPurchase(PurchaseRecord record);

    HistorySummary getHistorySummary();
}
