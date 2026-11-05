package gtshop.service;

import gtshop.model.HistorySummary;
import gtshop.model.PurchaseRecord;
import gtshop.model.UserProfile;
import java.util.List;

public interface UserService {
    public UserProfile getUser();

    public UserProfile getUserById(String var1);

    public void createUser(UserProfile var1);

    public List<PurchaseRecord> getHistory();

    public List<PurchaseRecord> getHistoryByUserId(String var1);

    public void addPurchase(PurchaseRecord var1);

    public HistorySummary getHistorySummary();

    public boolean updateUserPoints(String var1, int var2);

    public void setUserPoints(int var1);

    public int getUserPoints();
}

