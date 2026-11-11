package gtshop.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {
    @Test
    void capacityAndCleanupRespectEachBucketsWindow() {
        AtomicLong clock = new AtomicLong();
        RateLimiter limiter = new RateLimiter(2, clock::get);
        assertThat(limiter.tryAcquire("daily", 1, 86_400_000)).isTrue();
        assertThat(limiter.tryAcquire("short", 1, 60_000)).isTrue();
        assertThat(limiter.tryAcquire("other", 1, 60_000)).isFalse();

        clock.addAndGet(TimeUnit.MINUTES.toNanos(1));
        limiter.cleanup();

        assertThat(limiter.tryAcquire("other", 1, 60_000)).isTrue();
        assertThat(limiter.tryAcquire("daily", 1, 86_400_000)).isFalse();
        assertThat(limiter.tryAcquire("short", 1, 60_000)).isFalse();
    }
}
