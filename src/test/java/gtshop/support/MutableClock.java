package gtshop.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Ceas controlabil din teste (avans in timp pentru streak, limite zilnice etc.). */
public class MutableClock extends Clock {
    private volatile Instant instant;

    public MutableClock(Instant start) {
        this.instant = start;
    }

    public void set(Instant instant) {
        this.instant = instant;
    }

    public void advance(Duration duration) {
        this.instant = this.instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
