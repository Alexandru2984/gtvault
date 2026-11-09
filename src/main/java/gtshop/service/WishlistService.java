package gtshop.service;

import gtshop.game.Economy;
import gtshop.model.Reward;
import gtshop.repository.RewardRepository;
import gtshop.web.ApiException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lista de dorinte, pastrata pe server (sincronizata intre dispozitive). */
@Service
public class WishlistService {
    private final JdbcTemplate jdbc;
    private final RewardRepository rewards;
    private final CatalogService catalog;
    private final WalletService wallet;
    private final TimeService time;

    public WishlistService(JdbcTemplate jdbc, RewardRepository rewards, CatalogService catalog, WalletService wallet,
                           TimeService time) {
        this.jdbc = jdbc;
        this.rewards = rewards;
        this.catalog = catalog;
        this.wallet = wallet;
        this.time = time;
    }

    public record Item(CatalogService.RewardView reward, Instant addedAt) {}

    @Transactional(readOnly = true)
    public Map<String, Object> list(String userId) {
        Map<String, Instant> added = new LinkedHashMap<>();
        jdbc.query("SELECT reward_id, added_at FROM wishlist_items WHERE user_id = ? ORDER BY added_at DESC, reward_id",
                rs -> {
                    added.put(rs.getString(1), rs.getTimestamp(2).toInstant());
                }, userId);
        CatalogService.Snapshot snap = catalog.snapshot();
        Map<String, Reward> byId = new LinkedHashMap<>();
        rewards.findAllById(added.keySet()).forEach(r -> byId.put(r.getId(), r));
        List<Item> items = new ArrayList<>();
        int onSale = 0;
        for (Map.Entry<String, Instant> e : added.entrySet()) {
            Reward r = byId.get(e.getKey());
            if (r == null || !r.isActive()) {
                continue;
            }
            CatalogService.RewardView v = snap.view(r);
            if (v.deal() != null) {
                onSale++;
            }
            items.add(new Item(v, e.getValue()));
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("items", items);
        res.put("count", items.size());
        res.put("onSale", onSale);
        res.put("max", Economy.WISHLIST_MAX);
        return res;
    }

    @Transactional(readOnly = true)
    public List<String> ids(String userId) {
        return jdbc.queryForList("SELECT reward_id FROM wishlist_items WHERE user_id = ? ORDER BY added_at DESC",
                String.class, userId);
    }

    @Transactional
    public Map<String, Object> add(String userId, String rewardId) {
        Reward r = rewards.findById(rewardId).filter(Reward::isActive)
                .orElseThrow(() -> ApiException.notFound("Produsul nu a fost găsit."));
        // Blocam randul utilizatorului pentru ca verificarea limitei si inserarea sa fie atomice
        wallet.lock(userId);
        int count = count(userId);
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM wishlist_items WHERE user_id = ? AND reward_id = ?)", Boolean.class, userId, rewardId));
        if (!exists) {
            if (count >= Economy.WISHLIST_MAX) {
                throw new ApiException(HttpStatus.CONFLICT, "WISHLIST_FULL",
                        "Wishlist-ul poate avea cel mult " + Economy.WISHLIST_MAX + " produse.");
            }
            jdbc.update("INSERT INTO wishlist_items (user_id, reward_id, added_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                    userId, r.getId(), Timestamp.from(time.now()));
            count++;
        }
        return Map.of("success", true, "wishlisted", true, "count", count,
                "message", "„" + r.getName() + "” a fost adăugat în wishlist. Te anunțăm când intră la reducere.");
    }

    @Transactional
    public Map<String, Object> remove(String userId, String rewardId) {
        jdbc.update("DELETE FROM wishlist_items WHERE user_id = ? AND reward_id = ?", userId, rewardId);
        return Map.of("success", true, "wishlisted", false, "count", count(userId), "message", "Produs scos din wishlist.");
    }

    private int count(String userId) {
        Integer c = jdbc.queryForObject("SELECT count(*) FROM wishlist_items WHERE user_id = ?", Integer.class, userId);
        return c == null ? 0 : c;
    }
}
