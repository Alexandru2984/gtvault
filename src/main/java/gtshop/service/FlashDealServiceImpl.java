package gtshop.service;

import gtshop.model.Reward;
import gtshop.repository.RewardRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FlashDealServiceImpl implements FlashDealService {

    private final RewardRepository rewardRepository;

    public FlashDealServiceImpl(RewardRepository rewardRepository) {
        this.rewardRepository = rewardRepository;
    }

    @Override
    public List<Map<String, Object>> getActiveFlashDeals() {
        List<Map<String, Object>> deals = new ArrayList<>();

        // Target rewards for flash deal
        List<String> targetIds = List.of("rew-mouse", "rew-templatecv");
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
        String expiresIso = endOfDay.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        int index = 0;
        int[] claimedPcts = {72, 85};

        for (String id : targetIds) {
            Reward r = rewardRepository.findById(id).orElse(null);
            if (r != null) {
                int discountPercent = 20;
                int dealPrice = (int) Math.round(r.getPrice() * (1.0 - (discountPercent / 100.0)));

                Map<String, Object> deal = new HashMap<>();
                deal.put("id", "flash-" + r.getId());
                deal.put("rewardId", r.getId());
                deal.put("name", r.getName());
                deal.put("category", r.getCategory());
                deal.put("normalPrice", r.getPrice());
                deal.put("dealPrice", dealPrice);
                deal.put("discountPercent", discountPercent);
                deal.put("claimedPercent", claimedPcts[index % claimedPcts.length]);
                deal.put("expiresAt", expiresIso);
                deal.put("badge", "⚡ FLASH DEAL -20%");
                deals.add(deal);
                index++;
            }
        }

        return deals;
    }
}
