package gtshop.service;

import gtshop.config.AppProperties;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import org.springframework.stereotype.Component;

/**
 * Sursa unica de adevar pentru "acum" si "azi" in fusul orar al comunitatii.
 */
@Component
public class TimeService {
    private final Clock clock;
    private final ZoneId zone;

    public TimeService(Clock clock, AppProperties props) {
        this.clock = clock;
        this.zone = ZoneId.of(props.getTimezone());
    }

    public Instant now() {
        return clock.instant();
    }

    public ZoneId zone() {
        return zone;
    }

    public LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), zone);
    }

    public String todayIso() {
        return today().toString();
    }

    public String yesterdayIso() {
        return today().minusDays(1).toString();
    }

    public Instant startOfToday() {
        return today().atStartOfDay(zone).toInstant();
    }

    public Instant startOfTomorrow() {
        return today().plusDays(1).atStartOfDay(zone).toInstant();
    }

    public Instant startOfWeek() {
        return today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant();
    }

    public Instant startOfMonth() {
        return today().withDayOfMonth(1).atStartOfDay(zone).toInstant();
    }

    public long secondsUntilTomorrow() {
        return Math.max(0, startOfTomorrow().getEpochSecond() - now().getEpochSecond());
    }
}
