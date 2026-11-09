package gtshop.service;

import gtshop.web.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contoare zilnice per utilizator (ziua in fusul orar al comunitatii), folosite pentru limitele
 * anti-abuz: rotiri platite, cufere, voturi recompensate, cadouri etc.
 *
 * <p>Apelantii tin deja randul utilizatorului blocat, deci incrementarea este serializata.</p>
 */
@Service
public class DailyCounterService {
    private static final String UPSERT = """
            INSERT INTO user_daily_counters (user_id, counter_date, counter_key, counter_value)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (user_id, counter_date, counter_key)
            DO UPDATE SET counter_value = user_daily_counters.counter_value + EXCLUDED.counter_value
            RETURNING counter_value""";

    private final JdbcTemplate jdbc;
    private final TimeService time;

    public DailyCounterService(JdbcTemplate jdbc, TimeService time) {
        this.jdbc = jdbc;
        this.time = time;
    }

    @Transactional(readOnly = true)
    public int get(String userId, String key) {
        List<Integer> v = jdbc.queryForList(
                "SELECT counter_value FROM user_daily_counters WHERE user_id = ? AND counter_date = ? AND counter_key = ?",
                Integer.class, userId, time.todayIso(), key);
        return v.isEmpty() ? 0 : v.get(0);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public int add(String userId, String key, int amount) {
        Integer v = jdbc.queryForObject(UPSERT, Integer.class, userId, time.todayIso(), key, amount);
        return v == null ? amount : v;
    }

    /** Verifica limita inainte de a incrementa; arunca 409 DAILY_LIMIT_REACHED daca s-ar depasi. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int consume(String userId, String key, int amount, int dailyLimit, String message) {
        int current = get(userId, key);
        if (current + amount > dailyLimit) {
            throw new ApiException(HttpStatus.CONFLICT, "DAILY_LIMIT_REACHED", message);
        }
        return add(userId, key, amount);
    }

    @Scheduled(cron = "0 15 3 * * *", zone = "Europe/Bucharest")
    @Transactional
    public void purgeOld() {
        jdbc.update("DELETE FROM user_daily_counters WHERE counter_date < ?", time.today().minusDays(45).toString());
    }
}
