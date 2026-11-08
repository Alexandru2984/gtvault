package gtshop.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limitator in memorie cu fereastra glisanta (aplicatia ruleaza intr-o singura instanta).
 * Strat suplimentar peste limitarile din nginx.
 */
@Component
public class RateLimiter {
    private static final int MAX_KEYS = 100_000;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key, int limit, long windowMillis) {
        long now = System.currentTimeMillis();
        if (hits.size() > MAX_KEYS) {
            evictExpired(now, windowMillis);
        }
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() >= windowMillis) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    public void reset() {
        hits.clear();
    }

    @Scheduled(fixedDelay = 300_000)
    void cleanup() {
        evictExpired(System.currentTimeMillis(), 24L * 3600 * 1000);
    }

    private void evictExpired(long now, long maxWindow) {
        hits.entrySet().removeIf(e -> {
            Deque<Long> d = e.getValue();
            synchronized (d) {
                return d.isEmpty() || now - d.peekLast() >= maxWindow;
            }
        });
    }
}
