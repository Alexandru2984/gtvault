package gtshop.service;

import gtshop.model.Reward;
import gtshop.repository.RewardRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RewardServiceImpl implements RewardService {
    private final RewardRepository rewardRepository;

    public RewardServiceImpl(RewardRepository rewardRepository) {
        this.rewardRepository = rewardRepository;
    }

    public List<Reward> getAllRewards() {
        return this.rewardRepository.findAllOrdered();
    }

    public List<Reward> searchRewards(String query, String category) {
        boolean hasCategory;
        boolean hasQuery = query != null && !query.trim().isEmpty();
        boolean bl = hasCategory = category != null && !category.trim().isEmpty();
        if (hasQuery && hasCategory) {
            return this.rewardRepository.searchByQueryAndCategory(query.trim(), category.trim());
        }
        if (hasQuery) {
            return this.rewardRepository.searchByQuery(query.trim());
        }
        if (hasCategory) {
            return this.rewardRepository.findByCategoryIgnoreCaseOrdered(category.trim());
        }
        return this.getAllRewards();
    }

    @Transactional
    public void saveAll(List<Reward> rewards) {
        this.rewardRepository.saveAll(rewards);
    }

    public Reward findById(String id) {
        return this.rewardRepository.findById(id).orElse(null);
    }
}
