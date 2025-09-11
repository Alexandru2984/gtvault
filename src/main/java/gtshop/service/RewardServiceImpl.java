package gtshop.service;

import gtshop.model.Reward;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class RewardServiceImpl implements RewardService {
    private final List<Reward> rewards = new ArrayList<>();

    @Override
    public List<Reward> getAllRewards() {
        return new ArrayList<>(rewards);
    }

    @Override
    public List<Reward> searchRewards(String q, String category) {
        return rewards.stream().filter(r -> {
            boolean ok = true;

            if (q != null && !q.trim().isEmpty()) {
                String qq = q.toLowerCase(Locale.ROOT);
                ok &= (r.getName() != null && r.getName().toLowerCase(Locale.ROOT).contains(qq))
                   || (r.getDescription() != null && r.getDescription().toLowerCase(Locale.ROOT).contains(qq))
                   || (r.getFullDescription() != null && r.getFullDescription().toLowerCase(Locale.ROOT).contains(qq));
            }

            if (category != null && !category.trim().isEmpty()) {
                ok &= category.equalsIgnoreCase(r.getCategory());
            }
            return ok;
        }).collect(Collectors.toList());
    }

    @Override
    public void saveAll(List<Reward> seed) {
        this.rewards.clear();
        this.rewards.addAll(seed);
    }
}
