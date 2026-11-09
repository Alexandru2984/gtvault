package gtshop.game;

import java.util.List;

/** Nivelurile comunitatii, calculate exclusiv din XP (punctele castigate prin activitate). */
public final class Tiers {

    public record Tier(int level, String code, String title, String icon, int minXp, int discountPercent, List<String> perks) {}

    public record TierProgress(int level, String tier, String title, String icon, int discountPercent, int currentXp,
                               int prevThreshold, int nextThreshold, int xpToNext, int progressPercent,
                               List<String> perks, String nextTier) {}

    public static final List<Tier> ALL = List.of(
            new Tier(1, "BRONZE", "Junior Coder", "🥉", 0, 0,
                    List.of("Acces la toate premiile din catalog", "Quiz tehnic zilnic", "Calendar de streak")),
            new Tier(2, "SILVER", "Tech Explorer", "🥈", 1_000, 3,
                    List.of("Reducere automată 3% la checkout", "Badge argintiu în profil")),
            new Tier(3, "GOLD", "Code Artisan", "🥇", 2_500, 5,
                    List.of("Reducere automată 5% la checkout", "Badge auriu", "Acces la premiile Gold")),
            new Tier(4, "PLATINUM", "Full-Stack Hacker", "💠", 5_000, 7,
                    List.of("Reducere automată 7% la checkout", "Prioritate la premiile limitate")),
            new Tier(5, "DIAMOND", "Tech Architect", "💎", 10_000, 10,
                    List.of("Reducere maximă 10% la checkout", "Badge legendar Diamond", "Invitații la evenimente VIP")));

    private Tiers() {
    }

    public static Tier forXp(int xp) {
        Tier current = ALL.get(0);
        for (Tier t : ALL) {
            if (xp >= t.minXp()) {
                current = t;
            }
        }
        return current;
    }

    public static TierProgress progress(int xp) {
        int safeXp = Math.max(0, xp);
        Tier t = forXp(safeXp);
        boolean max = t.level() == ALL.size();
        Tier next = max ? t : ALL.get(t.level());
        int range = next.minXp() - t.minXp();
        int percent = max ? 100 : (int) Math.min(100, Math.floor((safeXp - t.minXp()) * 100.0 / range));
        return new TierProgress(t.level(), t.code(), t.title(), t.icon(), t.discountPercent(), safeXp, t.minXp(),
                next.minXp(), max ? 0 : next.minXp() - safeXp, percent, t.perks(), max ? null : next.code());
    }
}
