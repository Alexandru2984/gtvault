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
    public static final List<String> SORTS = List.of("featured", "price_asc", "price_desc", "new", "popular", "rating");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    static final int LOW_STOCK = 10;

    private final RewardRepository rewards;
    private final FlashDealService flashDeals;
    private final CatalogStats stats;
    private final TimeService time;

    public CatalogService(RewardRepository rewards, FlashDealService flashDeals, CatalogStats stats, TimeService time) {
        this.rewards = rewards;
        this.flashDeals = flashDeals;
        this.stats = stats;
        this.time = time;
    }

    /** Datele agregate necesare pentru a construi vederile din catalog (o singura citire per cerere). */
    public record Snapshot(Map<String, FlashDeal> deals, Map<String, Long> sold, Map<String, CatalogStats.Rating> ratings,
                           Map<String, Long> wishlists) {
        public RewardView view(Reward r) {
            return CatalogService.view(r, deals.get(r.getId()), sold.getOrDefault(r.getId(), 0L),
                    ratings.get(r.getId()), wishlists.getOrDefault(r.getId(), 0L));
        }
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot() {
        return new Snapshot(flashDeals.activeByReward(), soldLast30Days(), stats.ratings(), stats.wishlistCounts());
    }

    public record Deal(String id, int dealPrice, int discountPercent, java.time.Instant endsAt, int remaining, int perUserLimit) {}

    public record RewardView(String id, String name, String description, String fullDescription, int price, String image,
                             String category, boolean inStock, int stockCount, boolean lowStock, boolean isNew,
                             String fulfillment, int maxPerOrder, String minTier, Deal deal, long soldLast30Days,
                             Double rating, long reviewCount, long wishlistCount) {}

    static String fold(String s) {
        if (s == null) {
            return "";
        }
        return DIACRITICS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
    }

    public Map<String, Object> list(String q, String category, String sort, boolean onlyInStock) {
        try {
            flashDeals.ensureDailyDeals();
        } catch (RuntimeException e) {
            // Ofertele zilei sunt un bonus: catalogul trebuie sa se incarce oricum
            org.slf4j.LoggerFactory.getLogger(CatalogService.class).warn("Ofertele zilei indisponibile: {}", e.getMessage());
        }
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
        Snapshot snap = snapshot();
        List<RewardView> items = rewards.findActive().stream()
                .filter(r -> category == null || category.isBlank() || category.trim().equalsIgnoreCase(r.getCategory()))
                .filter(r -> !onlyInStock || r.getStockCount() > 0)
                .filter(r -> query.isEmpty() || fold(r.getName() + " " + r.getDescription() + " " + r.getFullDescription()
                        + " " + r.getCategory()).contains(query))
                .map(snap::view)
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
        Snapshot snap = snapshot();
        List<RewardView> related = rewards.findActive().stream()
                .filter(o -> !o.getId().equals(r.getId()) && o.getCategory() != null && o.getCategory().equals(r.getCategory()))
                .limit(4)
                .map(snap::view)
                .toList();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reward", snap.view(r));
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
            case "rating" -> Comparator.comparingDouble((RewardView v) -> v.rating() == null ? -1 : v.rating()).reversed()
                    .thenComparing(Comparator.comparingLong(RewardView::reviewCount).reversed())
                    .thenComparing(RewardView::id);
            default -> Comparator.comparing((RewardView v) -> v.deal() == null)
                    .thenComparing(v -> v.category() == null ? "" : v.category())
                    .thenComparingInt(RewardView::price);
        };
    }

    static RewardView view(Reward r, FlashDeal d, long sold, CatalogStats.Rating rating, long wishlists) {
        Deal deal = null;
        if (d != null && r.getStockCount() > 0) {
            deal = new Deal(d.getId(), d.dealPrice(r.getPrice()), d.getDiscountPercent(), d.getEndsAt(),
                    Math.min(d.remaining(), r.getStockCount()), d.getPerUserLimit());
        }
        return new RewardView(r.getId(), r.getName(), r.getDescription(), r.getFullDescription(), r.getPrice(),
                r.getImage(), r.getCategory(), r.getStockCount() > 0, r.getStockCount(),
                r.getStockCount() > 0 && r.getStockCount() <= LOW_STOCK, r.isNew(), r.getFulfillment(),
                r.getMaxPerOrder(), r.getMinTier(), deal, sold, rating == null ? null : rating.average(),
                rating == null ? 0 : rating.count(), wishlists);
    }
}
