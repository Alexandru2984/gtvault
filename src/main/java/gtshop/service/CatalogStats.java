package gtshop.service;

import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Agregate afisate in catalog: nota medie, numarul de recenzii si cate persoane au produsul in wishlist. */
@Component
public class CatalogStats {
    public record Rating(double average, long count) {}

    private final JdbcTemplate jdbc;

    public CatalogStats(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<String, Rating> ratings() {
        Map<String, Rating> map = new HashMap<>();
        jdbc.query("SELECT reward_id, avg(rating), count(*) FROM product_reviews WHERE NOT hidden GROUP BY reward_id",
                rs -> {
                    map.put(rs.getString(1), new Rating(round1(rs.getDouble(2)), rs.getLong(3)));
                });
        return map;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> wishlistCounts() {
        Map<String, Long> map = new HashMap<>();
        jdbc.query("SELECT reward_id, count(*) FROM wishlist_items GROUP BY reward_id",
                rs -> {
                    map.put(rs.getString(1), rs.getLong(2));
                });
        return map;
    }

    static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
