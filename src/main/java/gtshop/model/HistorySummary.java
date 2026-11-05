package gtshop.model;

import gtshop.model.PurchaseRecord;
import java.util.List;
import java.util.Map;

public class HistorySummary {
    private int totalPoints;
    private int totalItems;
    private List<PurchaseRecord> purchases;
    private Map<String, Integer> countsByReward;

    public int getTotalPoints() {
        return this.totalPoints;
    }

    public void setTotalPoints(int totalPoints) {
        this.totalPoints = totalPoints;
    }

    public int getTotalItems() {
        return this.totalItems;
    }

    public void setTotalItems(int totalItems) {
        this.totalItems = totalItems;
    }

    public List<PurchaseRecord> getPurchases() {
        return this.purchases;
    }

    public void setPurchases(List<PurchaseRecord> purchases) {
        this.purchases = purchases;
    }

    public Map<String, Integer> getCountsByReward() {
        return this.countsByReward;
    }

    public void setCountsByReward(Map<String, Integer> countsByReward) {
        this.countsByReward = countsByReward;
    }
}

