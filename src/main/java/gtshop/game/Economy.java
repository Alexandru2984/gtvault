package gtshop.game;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Toate regulile economiei de puncte intr-un singur loc. Sunt publicate integral prin
 * GET /api/gamification/config (transparenta: utilizatorii vad probabilitatile reale).
 *
 * <p>Jocurile de noroc au valoare asteptata sub cost, deci nu pot fi folosite ca „imprimanta de AP”:
 * roata ~40.5 AP per rotire la un cost de 50, cufarul ~144 AP la un cost de 150.</p>
 */
public final class Economy {

    // ---------------------------------------------------------------- streak zilnic
    /** Recompensa pentru ziua 1..7 din ciclul saptamanal de streak. */
    public static final List<Integer> STREAK_REWARDS = List.of(50, 75, 100, 150, 200, 250, 500);
    /** Bonusuri suplimentare la praguri de streak. */
    public static final Map<Integer, Integer> STREAK_MILESTONES = Map.of(30, 1_000, 100, 5_000, 365, 20_000);
    public static final int FREEZE_COST = 300;
    public static final int MAX_FREEZES = 2;

    // ---------------------------------------------------------------- activitati zilnice
    public static final int QUIZ_REWARD = 100;
    public static final int FORTUNE_REWARD = 20;

    // ---------------------------------------------------------------- roata norocului
    public record WheelSegment(int points, int weight, String label, String color) {}

    public static final int SPIN_COST = 50;
    public static final int MAX_PAID_SPINS_PER_DAY = 10;
    /** Ordinea segmentelor pe roata (indexul este cel trimis clientului pentru animatie). */
    public static final List<WheelSegment> WHEEL = List.of(
            new WheelSegment(10, 22, "10 AP", "#6366f1"),
            new WheelSegment(100, 8, "100 AP", "#f59e0b"),
            new WheelSegment(25, 20, "25 AP", "#06b6d4"),
            new WheelSegment(500, 1, "JACKPOT 500", "#ef4444"),
            new WheelSegment(40, 14, "40 AP", "#10b981"),
            new WheelSegment(0, 20, "Mai încearcă", "#64748b"),
            new WheelSegment(60, 12, "60 AP", "#8b5cf6"),
            new WheelSegment(250, 3, "250 AP", "#ec4899"));
    public static final int WHEEL_TOTAL_WEIGHT = WHEEL.stream().mapToInt(WheelSegment::weight).sum();

    // ---------------------------------------------------------------- cufere
    public record CrateTier(String code, String label, int points, int weightPermille, String color) {}

    public static final int CRATE_COST = 150;
    public static final int MAX_CRATES_PER_DAY = 5;
    public static final List<CrateTier> CRATE = List.of(
            new CrateTier("COMMON", "Pachet Coder Începător", 60, 450, "#94a3b8"),
            new CrateTier("UNCOMMON", "Pachet Echilibrat", 120, 300, "#22c55e"),
            new CrateTier("RARE", "Pachet Tehnologic Rar", 200, 170, "#3b82f6"),
            new CrateTier("EPIC", "Super Drop Epic", 400, 60, "#a855f7"),
            new CrateTier("LEGENDARY", "Mega Jackpot Legendar", 1_000, 18, "#f59e0b"),
            new CrateTier("MYTHIC", "Drop Mitic", 2_500, 2, "#ef4444"));
    public static final int CRATE_TOTAL_WEIGHT = CRATE.stream().mapToInt(CrateTier::weightPermille).sum();

    // ---------------------------------------------------------------- comunitate
    public static final int PROPOSAL_REWARD = 15;
    public static final int MAX_PROPOSALS_PER_DAY = 3;
    public static final int VOTE_REWARD = 5;
    public static final int MAX_REWARDED_VOTES_PER_DAY = 5;

    // ---------------------------------------------------------------- cadouri
    public static final int GIFT_MIN = 25;
    public static final int GIFT_MAX = 2_000;
    public static final int GIFT_DAILY_AMOUNT = 2_000;
    public static final int GIFT_DAILY_COUNT = 5;
    public static final int GIFT_MIN_ACCOUNT_AGE_DAYS = 7;
    /** XP minim (castigat prin activitate, nu bonusul de bun venit) pentru a putea trimite cadouri. */
    public static final int GIFT_MIN_XP = 300;

    // ---------------------------------------------------------------- ghilde
    public static final int GUILD_SWITCH_COOLDOWN_DAYS = 7;

    private Economy() {
    }

    public static int streakReward(int streak) {
        return STREAK_REWARDS.get(Math.floorMod(Math.max(1, streak) - 1, STREAK_REWARDS.size()));
    }

    public static double wheelExpectedValue() {
        return WHEEL.stream().mapToDouble(s -> (double) s.points() * s.weight()).sum() / WHEEL_TOTAL_WEIGHT;
    }

    public static double crateExpectedValue() {
        return CRATE.stream().mapToDouble(t -> (double) t.points() * t.weightPermille()).sum() / CRATE_TOTAL_WEIGHT;
    }

    public static Map<String, Object> publicConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("streak", Map.of("rewards", STREAK_REWARDS, "milestones", STREAK_MILESTONES,
                "freezeCost", FREEZE_COST, "maxFreezes", MAX_FREEZES));
        cfg.put("quiz", Map.of("reward", QUIZ_REWARD));
        cfg.put("fortune", Map.of("reward", FORTUNE_REWARD));
        cfg.put("wheel", Map.of("cost", SPIN_COST, "freeSpinsPerDay", 1, "maxPaidSpinsPerDay", MAX_PAID_SPINS_PER_DAY,
                "segments", WHEEL.stream().map(s -> Map.of("label", s.label(), "points", s.points(), "color", s.color(),
                        "probability", round(s.weight() * 100.0 / WHEEL_TOTAL_WEIGHT))).toList(),
                "expectedValue", round(wheelExpectedValue())));
        cfg.put("crate", Map.of("cost", CRATE_COST, "maxPerDay", MAX_CRATES_PER_DAY,
                "tiers", CRATE.stream().map(t -> Map.of("code", t.code(), "label", t.label(), "points", t.points(),
                        "color", t.color(), "probability", round(t.weightPermille() / 10.0))).toList(),
                "expectedValue", round(crateExpectedValue())));
        cfg.put("community", Map.of("proposalReward", PROPOSAL_REWARD, "maxProposalsPerDay", MAX_PROPOSALS_PER_DAY,
                "voteReward", VOTE_REWARD, "maxRewardedVotesPerDay", MAX_REWARDED_VOTES_PER_DAY));
        cfg.put("gifts", Map.of("min", GIFT_MIN, "max", GIFT_MAX, "dailyAmount", GIFT_DAILY_AMOUNT,
                "dailyCount", GIFT_DAILY_COUNT, "minAccountAgeDays", GIFT_MIN_ACCOUNT_AGE_DAYS, "minXp", GIFT_MIN_XP));
        cfg.put("guilds", Map.of("switchCooldownDays", GUILD_SWITCH_COOLDOWN_DAYS));
        cfg.put("tiers", Tiers.ALL);
        return cfg;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
