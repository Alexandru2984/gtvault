package gtshop.service;

import gtshop.model.PointTransaction;
import gtshop.model.UserProfile;
import java.util.List;

public interface TransactionService {
    PointTransaction recordTransaction(UserProfile user, String type, int amount, int balanceAfter, String description);
    List<PointTransaction> getRecentTransactions(UserProfile user);
}
