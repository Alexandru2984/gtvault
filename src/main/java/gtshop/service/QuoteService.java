package gtshop.service;

import gtshop.game.Badge;
import gtshop.model.FlashDeal;
import gtshop.model.PromoCode;
import gtshop.model.Reward;
import gtshop.model.UserProfile;
import gtshop.repository.FlashDealRepository;
import gtshop.repository.PromoCodeRepository;
import gtshop.repository.PromoRedemptionRepository;
import gtshop.repository.PurchaseRecordRepository;
import gtshop.repository.RewardRepository;
import gtshop.service.PricingService.CartLine;
import gtshop.service.PricingService.Context;
import gtshop.service.PricingService.Quote;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Incarca datele pentru {@link PricingService}. La plata ({@code lock = true}) produsele, ofertele si codul
 * promo sunt blocate (SELECT FOR UPDATE, in ordinea id-urilor) ca stocul si limitele sa fie respectate exact.
 */
@Service
public class QuoteService {
    private final RewardRepository rewards;
    private final FlashDealRepository dealsRepo;
    private final FlashDealService flashDeals;
    private final PromoCodeRepository promos;
    private final PromoRedemptionRepository redemptions;
    private final PurchaseRecordRepository purchases;
    private final BadgeService badges;
    private final PricingService pricing;
    private final TimeService time;
    private final gtshop.util.Locks locks;

    public QuoteService(RewardRepository rewards, FlashDealRepository dealsRepo, FlashDealService flashDeals,
                        PromoCodeRepository promos, PromoRedemptionRepository redemptions, PurchaseRecordRepository purchases,
                        BadgeService badges, PricingService pricing, TimeService time, gtshop.util.Locks locks) {
        this.rewards = rewards;
        this.dealsRepo = dealsRepo;
        this.flashDeals = flashDeals;
        this.promos = promos;
        this.redemptions = redemptions;
        this.purchases = purchases;
        this.badges = badges;
        this.pricing = pricing;
        this.time = time;
        this.locks = locks;
    }

    /** Rezultatul calculului impreuna cu entitatile folosite (blocate, daca s-a cerut). */
    public record Priced(Quote quote, Map<String, Reward> rewards, Map<String, FlashDeal> deals, PromoCode promo) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public Priced quote(UserProfile user, List<CartLine> lines, String promoInput, boolean lock) {
        Set<String> ids = new TreeSet<>();
        lines.forEach(l -> ids.add(l.rewardId()));

        Map<String, Reward> rewardMap = new HashMap<>();
        (lock ? rewards.findAllByIdForUpdate(ids) : rewards.findAllById(ids))
                .forEach(r -> rewardMap.put(r.getId(), lock ? locks.refresh(r) : r));

        Map<String, FlashDeal> deals = new HashMap<>();
        Map<String, FlashDeal> active = flashDeals.activeByReward();
        for (String id : ids) {
            FlashDeal d = active.get(id);
            if (d != null) {
                deals.put(id, lock ? dealsRepo.findByIdForUpdate(d.getId()).map(locks::refresh).orElse(d) : d);
            }
        }
        Map<String, Long> bought = new HashMap<>();
        for (FlashDeal d : deals.values()) {
            bought.put(d.getId(), purchases.flashUnitsBought(user.getId(), d.getId()));
        }
        Set<String> ownedBadges = new HashSet<>();
        for (Reward r : rewardMap.values()) {
            if (Reward.FULFILLMENT_BADGE.equals(r.getFulfillment()) && badges.has(user.getId(), Badge.HERO)) {
                ownedBadges.add(r.getId());
            }
        }
        PromoCode promo = null;
        boolean used = false;
        if (promoInput != null && !promoInput.isBlank()) {
            String code = promoInput.trim();
            if (code.length() <= 64) {
                promo = (lock ? promos.findByCodeForUpdate(code).map(locks::refresh) : promos.findByCodeIgnoreCase(code)).orElse(null);
            }
            used = promo != null && redemptions.existsByPromoIdAndUserId(promo.getId(), user.getId());
        }
        Context ctx = new Context(user.getTotalPointsEarned(), user.getActivityPoints(), rewardMap, deals, bought,
                ownedBadges, promo, promoInput, used, time.now());
        return new Priced(pricing.price(lines, ctx), rewardMap, deals, promo);
    }
}
