package gtshop.security;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limitator in memorie cu fereastra glisanta (aplicatia ruleaza intr-o singura instanta).
 * Strat suplimentar peste limitarile din nginx.
 */
@Component
public class RateLimiter {
    private static final int MAX_KEYS = 20_000;
    private final Map<String, Bucket> hits = new HashMap<>();
    private final int maxKeys;
    private final LongSupplier ticker;

    public RateLimiter() {
        this(MAX_KEYS, System::nanoTime);
    }

    RateLimiter(int maxKeys, LongSupplier ticker) {
        this.maxKeys = maxKeys;
        this.ticker = ticker;
    }

    public synchronized boolean tryAcquire(String key, int limit, long windowMillis) {
        long now = ticker.getAsLong();
        Bucket bucket = hits.get(key);
        if (bucket == null) {
            if (hits.size() >= maxKeys) {
                return false;
            }
            bucket = new Bucket(TimeUnit.MILLISECONDS.toNanos(windowMillis), new ArrayDeque<>());
            hits.put(key, bucket);
        }
        var timestamps = bucket.timestamps();
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() >= bucket.window()) {
            timestamps.removeFirst();
        }
        if (timestamps.size() >= limit) {
            return false;
        }
        timestamps.addLast(now);
        return true;
    }

    public synchronized void reset() {
        hits.clear();
    }

    @Scheduled(fixedDelay = 30_000)
    synchronized void cleanup() {
        long now = ticker.getAsLong();
        hits.values().removeIf(bucket -> now - bucket.timestamps().getLast() >= bucket.window());
    }

    private record Bucket(long window, ArrayDeque<Long> timestamps) {}
}
