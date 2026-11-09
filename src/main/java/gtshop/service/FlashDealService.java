package gtshop.service;

import gtshop.game.ContentBank;
import gtshop.model.FlashDeal;
import gtshop.model.Reward;
import gtshop.repository.FlashDealRepository;
import gtshop.repository.RewardRepository;
import gtshop.util.Ids;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oferte flash reale: reducere aplicata efectiv la plata, stoc limitat si limita per utilizator.
 * Cand administratorul nu are oferte active, se genereaza automat „ofertele zilei” (2 produse, determinist
 * pentru ziua respectiva, deci generarea concurenta nu creeaza duplicate).
 */
@Service
public class FlashDealService {
    private static final Logger log = LoggerFactory.getLogger(FlashDealService.class);
    static final int AUTO_DEALS = 2;
    static final int[] AUTO_PERCENTS = {15, 20, 25};
    static final int AUTO_MAX_QUANTITY = 15;

    private final FlashDealRepository deals;
    private final RewardRepository rewards;
    private final JdbcTemplate jdbc;
    private final TimeService time;
    private final WishlistAlerts alerts;

    public FlashDealService(FlashDealRepository deals, RewardRepository rewards, JdbcTemplate jdbc, WishlistAlerts alerts,
                            TimeService time) {
        this.alerts = alerts;
        this.deals = deals;
        this.rewards = rewards;
        this.jdbc = jdbc;
        this.time = time;
    }

    /** Ofertele active acum, indexate dupa produs (doar pentru produse active, in stoc). */
    @Transactional(readOnly = true)
    public Map<String, FlashDeal> activeByReward() {
        Map<String, FlashDeal> map = new LinkedHashMap<>();
        for (FlashDeal d : deals.findActive(time.now())) {
            if (d.remaining() > 0) {
                map.putIfAbsent(d.getRewardId(), d);
            }
        }
        return map;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureDailyDeals() {
        try {
            Instant now = time.now();
            if (deals.anyManualActive(now)) {
                return;
            }
            String today = time.todayIso();
            List<Reward> candidates = new ArrayList<>(rewards.findActive().stream()
                    .filter(r -> r.getStockCount() >= 5 && !Reward.FULFILLMENT_BADGE.equals(r.getFulfillment()))
                    .sorted((a, b) -> a.getId().compareTo(b.getId()))
                    .toList());
            if (candidates.isEmpty()) {
                return;
            }
            Random seeded = new Random(ContentBank.stableHash("flash|" + today));
            Collections.shuffle(candidates, seeded);
            Instant start = time.startOfToday();
            Instant end = time.startOfTomorrow();
            for (Reward r : candidates.subList(0, Math.min(AUTO_DEALS, candidates.size()))) {
                int percent = AUTO_PERCENTS[seeded.nextInt(AUTO_PERCENTS.length)];
                int created = jdbc.update("""
                        INSERT INTO flash_deals (id, reward_id, discount_percent, starts_at, ends_at, max_quantity,
                                                 sold_quantity, per_user_limit, auto_generated, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, 0, 2, true, ?)
                        ON CONFLICT (reward_id, starts_at) DO NOTHING""",
                        Ids.prefixed("deal-"), r.getId(), percent, java.sql.Timestamp.from(start), java.sql.Timestamp.from(end),
                        Math.min(AUTO_MAX_QUANTITY, r.getStockCount()), java.sql.Timestamp.from(now));
                if (created == 1) {
                    alerts.dealStarted(r.getId(), r.getName(), percent);
                }
            }
        } catch (RuntimeException e) {
            // Ofertele zilei sunt un bonus: o eroare aici nu trebuie sa blocheze catalogul
            log.warn("Nu am putut genera ofertele zilei: {}", e.getMessage());
        }
    }

    public record DealView(String id, String rewardId, String name, String image, String category, int normalPrice,
                           int dealPrice, int discountPercent, Instant startsAt, Instant endsAt, int remaining,
                           int maxQuantity, int claimedPercent, int perUserLimit) {}

    @Transactional(readOnly = true)
    public List<DealView> list() {
        Map<String, FlashDeal> active = activeByReward();
        Map<String, Reward> byId = new LinkedHashMap<>();
        rewards.findAllById(active.keySet()).forEach(r -> byId.put(r.getId(), r));
        List<DealView> out = new ArrayList<>();
        for (FlashDeal d : active.values()) {
            Reward r = byId.get(d.getRewardId());
            if (r == null || !r.isActive() || r.getStockCount() <= 0) {
                continue;
            }
            out.add(view(d, r));
        }
        return out;
    }

    public static DealView view(FlashDeal d, Reward r) {
        int claimed = d.getMaxQuantity() == 0 ? 100 : (int) Math.round(d.getSoldQuantity() * 100.0 / d.getMaxQuantity());
        return new DealView(d.getId(), r.getId(), r.getName(), r.getImage(), r.getCategory(), r.getPrice(),
                d.dealPrice(r.getPrice()), d.getDiscountPercent(), d.getStartsAt(), d.getEndsAt(),
                Math.min(d.remaining(), r.getStockCount()), d.getMaxQuantity(), claimed, d.getPerUserLimit());
    }
}
