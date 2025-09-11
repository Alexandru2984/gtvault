package gtshop.service;

import gtshop.model.Reward;
import java.util.List;

public interface RewardService {
    List<Reward> getAllRewards();
    List<Reward> searchRewards(String q, String category); // suport căutare/filtrare
    void saveAll(List<Reward> rewards);
}
