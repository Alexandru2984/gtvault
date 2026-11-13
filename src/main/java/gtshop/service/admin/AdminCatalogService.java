package gtshop.service.admin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gtshop.game.Tiers;
import gtshop.model.FlashDeal;
import gtshop.model.PromoCode;
import gtshop.model.Reward;
import gtshop.repository.FlashDealRepository;
import gtshop.repository.PromoCodeRepository;
import gtshop.repository.RewardRepository;
import gtshop.service.TimeService;
import gtshop.service.WishlistAlerts;
import gtshop.util.Ids;
import gtshop.util.InputSanitizer;
import gtshop.util.Locks;
import gtshop.web.ApiException;
import java.sql.Timestamp;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Administrarea catalogului: produse, stoc, oferte flash si coduri promotionale. */
@Service
public class AdminCatalogService {
    private static final Pattern IMAGE = Pattern.compile("^images/[a-z0-9/_-]{1,100}\\.(svg|png|jpg|jpeg|webp)$");
    private static final Pattern PROMO = Pattern.compile("^[A-Z0-9_-]{3,32}$");
    private static final Set<String> FULFILLMENTS = Set.of(Reward.FULFILLMENT_PHYSICAL, Reward.FULFILLMENT_DIGITAL);
    private static final int MAX_STOCK = 1_000_000;
    private static final int MAX_PRICE = 1_000_000;
    private static final Duration MAX_DEAL = Duration.ofDays(30);

    private final RewardRepository rewards;
    private final FlashDealRepository deals;
    private final PromoCodeRepository promos;
    private final WishlistAlerts alerts;
    private final AdminAudit audit;
    private final JdbcTemplate jdbc;
    private final Locks locks;
    private final TimeService time;
    private final ObjectMapper json;

    public AdminCatalogService(RewardRepository rewards, FlashDealRepository deals, PromoCodeRepository promos,
                               WishlistAlerts alerts, AdminAudit audit, JdbcTemplate jdbc, Locks locks, TimeService time, ObjectMapper json) {
        this.rewards = rewards;
        this.deals = deals;
        this.promos = promos;
        this.alerts = alerts;
        this.audit = audit;
        this.jdbc = jdbc;
        this.locks = locks;
        this.time = time;
        this.json = json;
    }

    // ================================================================== produse

    public record RewardForm(String name, String description, String fullDescription, Integer price, String image,
                             String category, Integer stockCount, Boolean active, Boolean isNew, String fulfillment,
                             Integer maxPerOrder, String minTier, String version, Integer expectedStock) {}

    /** Stocul este separat de metadate: o vânzare nu invalidează editarea descrierii. */
    private record Metadata(String id, String name, String description, String fullDescription, int price, String image,
                            String category, boolean active, boolean isNew, String fulfillment, int maxPerOrder, String minTier) {}
    public record Editor(Reward product, String version) {}

    @Transactional(readOnly = true)
    public Editor editor(String id) {
        Reward reward = rewards.findById(id).orElseThrow(() -> ApiException.notFound("Produsul nu a fost găsit."));
        return new Editor(reward, version(reward));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rewards() {
        return jdbc.queryForList("""
                SELECT w.id, w.name, w.description, w.full_description, w.price, w.image, w.category, w.stock_count,
                       w.active, w.is_new, w.fulfillment, w.max_per_order, w.min_tier, w.created_at,
                       (SELECT coalesce(sum(p.quantity), 0) FROM purchase_records p JOIN orders o ON o.id = p.order_id
                        WHERE p.reward_id = w.id AND o.status <> 'CANCELLED' AND o.created_at > ?) AS sold_last_30_days,
                       (SELECT count(*) FROM wishlist_items wi WHERE wi.reward_id = w.id) AS wishlist_count,
                       (SELECT round(avg(r.rating)::numeric, 1) FROM product_reviews r WHERE r.reward_id = w.id AND NOT r.hidden) AS rating
                FROM rewards w ORDER BY w.active DESC, w.category, w.name""",
                Timestamp.from(time.now().minus(Duration.ofDays(30))));
    }

    @Transactional
    public Reward createReward(RewardForm f) {
        Reward r = new Reward();
        r.setId(newRewardId(f.name()));
        r.setCreatedAt(time.now());
        apply(r, f, true);
        if (r.getStockCount() == 0 && f.stockCount() == null) {
            throw ApiException.badRequest("Stocul este obligatoriu.");
        }
        rewards.save(r);
        audit.record("REWARD_CREATE", "REWARD", r.getId(), r.getName() + " | " + r.getPrice() + " AP | stoc " + r.getStockCount());
        return r;
    }

    @Transactional
    public Reward updateReward(String id, RewardForm f) {
        Reward r = lockReward(id);
        if (f.version() == null || !f.version().matches("[0-9a-f]{64}")) {
            throw ApiException.badRequest("PRODUCT_VERSION_REQUIRED", "Reîncarcă formularul produsului înainte de salvare.");
        }
        if (!version(r).equals(f.version())) {
            throw new ApiException(HttpStatus.CONFLICT, "PRODUCT_CHANGED", "Produsul a fost modificat între timp. Reîncarcă datele și verifică schimbările înainte de salvare.");
        }
        if (f.stockCount() != null) {
            if (f.expectedStock() == null || f.expectedStock() < 0 || f.expectedStock() > MAX_STOCK) {
                throw ApiException.badRequest("STOCK_VERSION_REQUIRED", "Pentru înlocuirea stocului este necesară și valoarea citită inițial.");
            }
            if (f.expectedStock() != r.getStockCount()) {
                throw new ApiException(HttpStatus.CONFLICT, "STOCK_CHANGED", "Stocul s-a schimbat între timp. Folosește ajustarea de stoc sau reîncarcă datele.");
            }
        }
        int stockBefore = r.getStockCount();
        boolean wasActive = r.isActive();
        String before = summary(r);
        apply(r, f, false);
        rewards.save(r);
        if (r.isActive() && r.getStockCount() > 0 && (stockBefore == 0 || !wasActive)) {
            alerts.backInStock(r.getId(), r.getName());
        }
        audit.record("REWARD_UPDATE", "REWARD", r.getId(), before + " => " + summary(r));
        return r;
    }

    @Transactional
    public Reward adjustStock(String id, Integer delta, String reason) {
        if (delta == null || delta == 0 || delta < -MAX_STOCK || delta > MAX_STOCK) {
            throw ApiException.badRequest("Modificarea de stoc trebuie să fie un număr nenul.");
        }
        String why = InputSanitizer.cleanOptional(reason, 200, "Motivul");
        Reward r = lockReward(id);
        int before = r.getStockCount();
        long next = (long) before + delta;
        if (next < 0 || next > MAX_STOCK) {
            throw ApiException.badRequest("Stocul rezultat trebuie să fie între 0 și " + MAX_STOCK + ".");
        }
        r.setStockCount((int) next);
        r.setInStock(next > 0);
        rewards.save(r);
        if (before == 0 && next > 0 && r.isActive()) {
            alerts.backInStock(r.getId(), r.getName());
        }
        audit.record("REWARD_STOCK", "REWARD", r.getId(), before + " -> " + next + (why != null ? ": " + why : ""));
        return r;
    }

    private Reward lockReward(String id) {
        return rewards.findAllByIdForUpdate(List.of(id)).stream().findFirst().map(locks::refresh)
                .orElseThrow(() -> ApiException.notFound("Produsul nu a fost găsit."));
    }

    private void apply(Reward r, RewardForm f, boolean creating) {
        if (creating || f.name() != null) {
            r.setName(InputSanitizer.cleanText(f.name(), 2, 80, "Numele"));
        }
        if (creating || f.description() != null) {
            r.setDescription(InputSanitizer.cleanText(f.description(), 5, 500, "Descrierea scurtă"));
        }
        if (creating || f.fullDescription() != null) {
            r.setFullDescription(InputSanitizer.cleanText(f.fullDescription(), 5, 2000, "Descrierea completă"));
        }
        if (creating || f.price() != null) {
            if (f.price() == null || f.price() < 1 || f.price() > MAX_PRICE) {
                throw ApiException.badRequest("Prețul trebuie să fie între 1 și " + MAX_PRICE + " AP.");
            }
            r.setPrice(f.price());
        }
        if (creating || f.image() != null) {
            String img = f.image() == null ? "" : f.image().trim();
            if (!img.isEmpty() && !IMAGE.matcher(img).matches()) {
                throw ApiException.badRequest("Imaginea trebuie să fie o cale locală de forma images/nume.svg (svg, png, jpg, webp).");
            }
            r.setImage(img);
        }
        if (creating || f.category() != null) {
            r.setCategory(InputSanitizer.cleanText(f.category(), 2, 40, "Categoria"));
        }
        if (creating || f.stockCount() != null) {
            int stock = f.stockCount() == null ? 0 : f.stockCount();
            if (stock < 0 || stock > MAX_STOCK) {
                throw ApiException.badRequest("Stocul trebuie să fie între 0 și " + MAX_STOCK + ".");
            }
            r.setStockCount(stock);
            r.setInStock(stock > 0);
        }
        if (creating || f.active() != null) {
            r.setActive(f.active() == null || f.active());
        }
        if (creating || f.isNew() != null) {
            r.setNew(f.isNew() != null && f.isNew());
        }
        if (creating || f.fulfillment() != null) {
            String ful = f.fulfillment() == null ? Reward.FULFILLMENT_DIGITAL : f.fulfillment().trim().toUpperCase(Locale.ROOT);
            boolean keepsBadge = !creating && Reward.FULFILLMENT_BADGE.equals(r.getFulfillment()) && Reward.FULFILLMENT_BADGE.equals(ful);
            if (!keepsBadge && !FULFILLMENTS.contains(ful)) {
                throw ApiException.badRequest("Tip de livrare invalid (PHYSICAL sau DIGITAL).");
            }
            if (!creating && Reward.FULFILLMENT_BADGE.equals(r.getFulfillment()) && !keepsBadge) {
                throw ApiException.badRequest("Tipul de livrare al produsului „Badge Hero” nu poate fi schimbat.");
            }
            r.setFulfillment(ful);
        }
        if (creating || f.maxPerOrder() != null) {
            int max = f.maxPerOrder() == null ? 5 : f.maxPerOrder();
            if (max < 1 || max > 99) {
                throw ApiException.badRequest("Cantitatea maximă per comandă trebuie să fie între 1 și 99.");
            }
            if (Reward.FULFILLMENT_BADGE.equals(r.getFulfillment()) && max != 1) {
                throw ApiException.badRequest("Insigna se poate cumpăra o singură dată.");
            }
            r.setMaxPerOrder(max);
        }
        if (creating || f.minTier() != null) {
            String tier = f.minTier() == null || f.minTier().isBlank() ? null : f.minTier().trim().toUpperCase(Locale.ROOT);
            if (tier != null && Tiers.ALL.stream().noneMatch(t -> t.code().equals(tier))) {
                throw ApiException.badRequest("Nivel minim invalid.");
            }
            r.setMinTier(tier);
        }
    }

    private String newRewardId(String name) {
        String base = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.length() > 40) {
            base = base.substring(0, 40).replaceAll("-$", "");
        }
        return Ids.prefixed("rew-" + (base.isEmpty() ? "produs" : base) + "-");
    }

    private String version(Reward reward) {
        Metadata metadata = new Metadata(reward.getId(), reward.getName(), reward.getDescription(), reward.getFullDescription(), reward.getPrice(),
                reward.getImage(), reward.getCategory(), reward.isActive(), reward.isNew(), reward.getFulfillment(), reward.getMaxPerOrder(), reward.getMinTier());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(metadata)));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Versiunea produsului nu poate fi calculată", e);
        }
    }

    private static String summary(Reward r) {
        return r.getName() + " | " + r.getPrice() + " AP | stoc " + r.getStockCount() + " | " + (r.isActive() ? "activ" : "inactiv");
    }

    // ================================================================== oferte flash

    public record DealForm(String rewardId, Integer discountPercent, Instant startsAt, Instant endsAt, Integer maxQuantity,
                           Integer perUserLimit) {}

    @Transactional(readOnly = true)
    public List<Map<String, Object>> deals() {
        Timestamp now = Timestamp.from(time.now());
        return jdbc.queryForList("""
                SELECT d.id, d.reward_id, w.name AS reward_name, w.price, d.discount_percent, d.starts_at, d.ends_at,
                       d.max_quantity, d.sold_quantity, d.per_user_limit, d.auto_generated,
                       CASE WHEN d.starts_at > ? THEN 'UPCOMING' WHEN d.ends_at > ? THEN 'ACTIVE' ELSE 'ENDED' END AS state
                FROM flash_deals d JOIN rewards w ON w.id = d.reward_id
                WHERE d.ends_at > ?
                ORDER BY d.starts_at DESC LIMIT 200""", now, now, Timestamp.from(time.now().minus(Duration.ofDays(30))));
    }

    @Transactional
    public FlashDeal createDeal(DealForm f) {
        Reward r = rewards.findById(f.rewardId() == null ? "" : f.rewardId()).filter(Reward::isActive)
                .orElseThrow(() -> ApiException.notFound("Produsul nu a fost găsit."));
        if (Reward.FULFILLMENT_BADGE.equals(r.getFulfillment())) {
            throw ApiException.badRequest("Insignele nu pot intra în oferte flash.");
        }
        int percent = f.discountPercent() == null ? 0 : f.discountPercent();
        if (percent < 5 || percent > 80) {
            throw ApiException.badRequest("Reducerea trebuie să fie între 5% și 80%.");
        }
        Instant now = time.now();
        Instant start = f.startsAt() == null ? now : f.startsAt();
        Instant end = f.endsAt();
        if (end == null || !end.isAfter(start) || !end.isAfter(now)) {
            throw ApiException.badRequest("Oferta trebuie să se încheie după ce începe și în viitor.");
        }
        if (Duration.between(start, end).compareTo(MAX_DEAL) > 0) {
            throw ApiException.badRequest("O ofertă poate dura cel mult 30 de zile.");
        }
        int qty = f.maxQuantity() == null ? 0 : f.maxQuantity();
        if (qty < 1 || qty > 100_000) {
            throw ApiException.badRequest("Cantitatea din ofertă trebuie să fie între 1 și 100000.");
        }
        int perUser = f.perUserLimit() == null ? 2 : f.perUserLimit();
        if (perUser < 1 || perUser > 99) {
            throw ApiException.badRequest("Limita per utilizator trebuie să fie între 1 și 99.");
        }
        // Blocam produsul ca doua oferte create simultan sa nu se suprapuna
        lockReward(r.getId());
        Integer overlapping = jdbc.queryForObject(
                "SELECT count(*) FROM flash_deals WHERE reward_id = ? AND starts_at < ? AND ends_at > ?",
                Integer.class, r.getId(), Timestamp.from(end), Timestamp.from(start));
        if (overlapping != null && overlapping > 0) {
            throw ApiException.conflict("DEAL_OVERLAP", "Produsul are deja o ofertă în acest interval. Încheie-o mai întâi.");
        }
        FlashDeal d = new FlashDeal();
        d.setId(Ids.prefixed("deal-"));
        d.setRewardId(r.getId());
        d.setDiscountPercent(percent);
        d.setStartsAt(start);
        d.setEndsAt(end);
        d.setMaxQuantity(qty);
        d.setSoldQuantity(0);
        d.setPerUserLimit(perUser);
        d.setAutoGenerated(false);
        d.setCreatedAt(now);
        deals.save(d);
        if (!start.isAfter(now) && r.getStockCount() > 0) {
            alerts.dealStarted(r.getId(), r.getName(), percent);
        }
        audit.record("DEAL_CREATE", "FLASH_DEAL", d.getId(), r.getName() + " -" + percent + "% " + start + " → " + end + ", " + qty + " buc.");
        return d;
    }

    @Transactional
    public Map<String, Object> endDeal(String id) {
        FlashDeal d = deals.findByIdForUpdate(id).map(locks::refresh)
                .orElseThrow(() -> ApiException.notFound("Oferta nu a fost găsită."));
        Instant now = time.now();
        if (!d.getEndsAt().isAfter(now)) {
            throw ApiException.conflict("DEAL_ENDED", "Oferta s-a încheiat deja.");
        }
        if (d.getStartsAt().isAfter(now)) {
            deals.delete(d);
            audit.record("DEAL_DELETE", "FLASH_DEAL", id, "Ofertă programată ștearsă înainte de start");
            return Map.of("success", true, "message", "Oferta programată a fost ștearsă.");
        }
        d.setEndsAt(now);
        deals.save(d);
        audit.record("DEAL_END", "FLASH_DEAL", id, "Încheiată manual după " + d.getSoldQuantity() + " buc. vândute");
        return Map.of("success", true, "message", "Oferta a fost încheiată.");
    }

    // ================================================================== coduri promo

    public record PromoForm(String code, String description, Integer discountPercent, Integer discountPoints,
                            Integer maxDiscount, Integer minOrderPoints, Integer maxUses, Instant expiresAt, Boolean active) {}

    @Transactional(readOnly = true)
    public List<Map<String, Object>> promos() {
        return jdbc.queryForList("""
                SELECT p.id, p.code, p.description, p.discount_percent, p.discount_points, p.max_discount, p.min_order_points,
                       p.max_uses, p.current_uses, p.is_active, p.expires_at, p.created_at,
                       (SELECT coalesce(sum(r.discount), 0) FROM promo_redemptions r WHERE r.promo_id = p.id) AS total_discount
                FROM promo_codes p ORDER BY p.is_active DESC, p.created_at DESC""");
    }

    @Transactional
    public PromoCode createPromo(PromoForm f) {
        String code = f.code() == null ? "" : f.code().trim().toUpperCase(Locale.ROOT);
        if (!PROMO.matcher(code).matches()) {
            throw ApiException.badRequest("Codul poate avea 3-32 de caractere: litere, cifre, - și _.");
        }
        if (promos.findByCodeIgnoreCase(code).isPresent()) {
            throw ApiException.conflict("PROMO_EXISTS", "Există deja un cod promoțional „" + code + "”.");
        }
        PromoCode p = new PromoCode(Ids.prefixed("promo-"), code, 0, 0, 0, 1, 0, true);
        p.setCreatedAt(time.now());
        applyPromo(p, f, true);
        promos.save(p);
        audit.record("PROMO_CREATE", "PROMO", p.getId(), describe(p));
        return p;
    }

    @Transactional
    public PromoCode updatePromo(String id, PromoForm f) {
        PromoCode p = promos.findByIdForUpdate(id).map(locks::refresh)
                .orElseThrow(() -> ApiException.notFound("Codul promoțional nu a fost găsit."));
        if (f.code() != null && !f.code().trim().equalsIgnoreCase(p.getCode())) {
            throw ApiException.badRequest("Codul nu poate fi redenumit (a fost deja distribuit). Creează un cod nou.");
        }
        String before = describe(p);
        applyPromo(p, f, false);
        if (p.getMaxUses() < p.getCurrentUses()) {
            throw ApiException.badRequest("Limita de utilizări nu poate fi sub numărul de utilizări existente (" + p.getCurrentUses() + ").");
        }
        promos.save(p);
        audit.record("PROMO_UPDATE", "PROMO", p.getId(), before + " => " + describe(p));
        return p;
    }

    private void applyPromo(PromoCode p, PromoForm f, boolean creating) {
        if (creating || f.description() != null) {
            p.setDescription(InputSanitizer.cleanOptional(f.description(), 140, "Descrierea"));
        }
        if (creating || f.discountPercent() != null || f.discountPoints() != null) {
            int percent = f.discountPercent() == null ? 0 : f.discountPercent();
            int pts = f.discountPoints() == null ? 0 : f.discountPoints();
            if ((percent > 0) == (pts > 0) || percent < 0 || pts < 0) {
                throw ApiException.badRequest("Alege fie o reducere procentuală (1-90%), fie una fixă în AP.");
            }
            if (percent > 90 || pts > MAX_PRICE) {
                throw ApiException.badRequest("Reducere prea mare.");
            }
            p.setDiscountPercent(percent);
            p.setDiscountPoints(pts);
        }
        if (creating || f.maxDiscount() != null) {
            Integer max = f.maxDiscount() == null || f.maxDiscount() == 0 ? null : f.maxDiscount();
            if (max != null && (max < 1 || max > MAX_PRICE)) {
                throw ApiException.badRequest("Plafonul reducerii trebuie să fie pozitiv.");
            }
            p.setMaxDiscount(max);
        }
        if (creating || f.minOrderPoints() != null) {
            int min = f.minOrderPoints() == null ? 0 : f.minOrderPoints();
            if (min < 0 || min > MAX_PRICE) {
                throw ApiException.badRequest("Valoarea minimă a comenzii este invalidă.");
            }
            p.setMinOrderPoints(min);
        }
        if (creating || f.maxUses() != null) {
            int uses = f.maxUses() == null ? 100 : f.maxUses();
            if (uses < 1 || uses > 1_000_000) {
                throw ApiException.badRequest("Numărul maxim de utilizări trebuie să fie între 1 și 1000000.");
            }
            p.setMaxUses(uses);
        }
        if (creating || f.expiresAt() != null) {
            if (f.expiresAt() != null && !f.expiresAt().isAfter(time.now())) {
                throw ApiException.badRequest("Data de expirare trebuie să fie în viitor.");
            }
            p.setExpiresAt(f.expiresAt());
        }
        if (creating || f.active() != null) {
            p.setActive(f.active() == null || f.active());
        }
    }

    private static String describe(PromoCode p) {
        return p.getCode() + " " + (p.getDiscountPercent() > 0 ? "-" + p.getDiscountPercent() + "%" : "-" + p.getDiscountPoints() + " AP")
                + ", " + p.getCurrentUses() + "/" + p.getMaxUses() + (p.isActive() ? ", activ" : ", inactiv");
    }
}
