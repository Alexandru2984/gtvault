package gtshop.game;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Logica pura pentru streak: zile consecutive in fusul orar al comunitatii, cu „freeze”-uri care
 * acopera zilele ratate (fiecare zi ratata consuma un freeze).
 */
public final class StreakMath {

    public record Outcome(int newStreak, int freezesUsed, boolean reset) {}

    private StreakMath() {
    }

    public static LocalDate parse(String iso) {
        if (iso == null || iso.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(iso.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Zile ratate intre ultima revendicare si azi (0 = a revendicat ieri). Negativ daca a revendicat azi. */
    public static long missedDays(LocalDate last, LocalDate today) {
        return ChronoUnit.DAYS.between(last, today) - 1;
    }

    /** Rezultatul revendicarii de azi. Apelantul verifica separat ca azi nu s-a revendicat deja. */
    public static Outcome claim(LocalDate last, int currentStreak, int freezes, LocalDate today) {
        if (last == null || last.isAfter(today) || currentStreak <= 0) {
            return new Outcome(1, 0, last != null && currentStreak > 0);
        }
        long missed = missedDays(last, today);
        if (missed <= 0) {
            return new Outcome(currentStreak + 1, 0, false);
        }
        if (missed <= freezes) {
            return new Outcome(currentStreak + 1, (int) missed, false);
        }
        return new Outcome(1, 0, true);
    }

    /** Streak-ul „viu” afisat azi: 0 daca s-a pierdut deja (nici freeze-urile nu il mai pot salva). */
    public static int effective(LocalDate last, int currentStreak, int freezes, LocalDate today) {
        if (last == null || currentStreak <= 0 || last.isAfter(today)) {
            return last != null && last.isAfter(today) ? currentStreak : 0;
        }
        long missed = missedDays(last, today);
        return missed <= freezes ? currentStreak : 0;
    }
}
