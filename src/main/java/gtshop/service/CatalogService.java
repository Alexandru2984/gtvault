package gtshop.service;

import gtshop.model.FlashDeal;
import gtshop.model.Reward;
import gtshop.repository.RewardRepository;
import gtshop.web.ApiException;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogul public de recompense: cautare (fara diacritice), filtre, sortari si oferta flash activa. */
@Service
public class CatalogService {
    public static final List<String> SORTS = List.of("featured", "price_asc", "price_desc", "new", "popular");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    static final int LOW_STOCK = 10;

    private final RewardRepository rewards;
    private final FlashDealService flashDeals;
    private final TimeService time;

    public CatalogService(RewardRepository rewards, FlashDealService flashDeals, TimeService time) {
        this.rewards = rewards;
        this.flashDeals = flashDeals;
        this.time = time;
    }

    public record Deal(String id, int dealPrice, int discountPercent, java.time.Instant endsAt, int remaining, int perUserLimit) {}

    public record RewardView(String id, String name, String description, String fullDescription, int price, String image,
                             String category, boolean inStock, int stockCount, boolean lowStock, boolean isNew,
                             String fulfillment, int maxPerOrder, String minTier, Deal deal, long soldLast30Days) {}

    static String fold(String s) {
        if (s == null) {
            return "";
        }
        return DIACRITICS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public Map<String, Object> list(String q, String category, String sort, boolean onlyInStock) {
        flashDeals.ensureDailyDeals();
        return listInternal(q, category, sort, onlyInStock);
    }

    private Map<String, Object> listInternal(String q, String category, String sort, boolean onlyInStock) {
        String s = sort == null || sort.isBlank() ? "featured" : sort;
        if (!SORTS.contains(s)) {
            throw ApiException.badRequest("Sortare invalidă. Valori permise: " + String.join(", ", SORTS) + ".");
        }
        String query = q == null ? "" : fold(q.trim());
        if (query.length() > 60) {
            throw ApiException.badRequest("Termenul de căutare este prea lung.");
        }
        Map<String, FlashDeal> deals = flashDeals.activeByReward();
        Map<String, Long> sold = soldLast30Days();
        List<RewardView> items = rewards.findActive().stream()
                .filter(r -> category == null || category.isBlank() || category.trim().equalsIgnoreCase(r.getCategory()))
                .filter(r -> !onlyInStock || r.getStockCount() > 0)
                .filter(r -> query.isEmpty() || fold(r.getName() + " " + r.getDescription() + " " + r.getFullDescription()
                        + " " + r.getCategory()).contains(query))
                .map(r -> view(r, deals.get(r.getId()), sold.getOrDefault(r.getId(), 0L)))
                .sorted(comparator(s))
                .toList();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("rewards", items);
        res.put("total", items.size());
        res.put("categories", rewards.findActiveCategories());
        res.put("sorts", SORTS);
        return res;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> get(String id) {
        Reward r = rewards.findById(id).filter(Reward::isActive)
                .orElseThrow(() -> ApiException.notFound("Produsul nu a fost găsit."));
        Map<String, FlashDeal> deals = flashDeals.activeByReward();
        Map<String, Long> sold = soldLast30Days();
        List<RewardView> related = rewards.findActive().stream()
                .filter(o -> !o.getId().equals(r.getId()) && o.getCategory() != null && o.getCategory().equals(r.getCategory()))
                .limit(4)
                .map(o -> view(o, deals.get(o.getId()), sold.getOrDefault(o.getId(), 0L)))
                .toList();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reward", view(r, deals.get(r.getId()), sold.getOrDefault(r.getId(), 0L)));
        res.put("related", related);
        return res;
    }

    private Map<String, Long> soldLast30Days() {
        Map<String, Long> m = new HashMap<>();
        for (Object[] row : rewards.unitsSoldSince(time.now().minus(Duration.ofDays(30)))) {
            m.put((String) row[0], ((Number) row[1]).longValue());
        }
        return m;
    }

    private static Comparator<RewardView> comparator(String sort) {
        return switch (sort) {
            case "price_asc" -> Comparator.comparingInt(RewardView::price).thenComparing(RewardView::id);
            case "price_desc" -> Comparator.comparingInt(RewardView::price).reversed().thenComparing(RewardView::id);
            case "new" -> Comparator.comparing(RewardView::isNew).reversed().thenComparing(RewardView::id);
            case "popular" -> Comparator.comparingLong(RewardView::soldLast30Days).reversed().thenComparing(RewardView::id);
            default -> Comparator.comparing((RewardView v) -> v.deal() == null)
                    .thenComparing(v -> v.category() == null ? "" : v.category())
                    .thenComparingInt(RewardView::price);
        };
    }

    static RewardView view(Reward r, FlashDeal d, long sold) {
        Deal deal = null;
        if (d != null && r.getStockCount() > 0) {
            deal = new Deal(d.getId(), d.dealPrice(r.getPrice()), d.getDiscountPercent(), d.getEndsAt(),
                    Math.min(d.remaining(), r.getStockCount()), d.getPerUserLimit());
        }
        return new RewardView(r.getId(), r.getName(), r.getDescription(), r.getFullDescription(), r.getPrice(),
                r.getImage(), r.getCategory(), r.getStockCount() > 0, r.getStockCount(),
                r.getStockCount() > 0 && r.getStockCount() <= LOW_STOCK, r.isNew(), r.getFulfillment(),
                r.getMaxPerOrder(), r.getMinTier(), deal, sold);
    }
}
