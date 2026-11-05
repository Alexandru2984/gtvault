package gtshop.service;

import gtshop.model.Reward;
import java.util.List;

public interface RewardService {
    public List<Reward> getAllRewards();

    public List<Reward> searchRewards(String var1, String var2);

    public void saveAll(List<Reward> var1);

    public Reward findById(String var1);
}

