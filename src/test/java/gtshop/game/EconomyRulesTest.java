package gtshop.game;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EconomyRulesTest {

    @Test
    void gamblingHasNegativeExpectedValue() {
        assertThat(Economy.wheelExpectedValue()).isLessThan(Economy.SPIN_COST).isGreaterThan(Economy.SPIN_COST * 0.7);
        assertThat(Economy.crateExpectedValue()).isLessThan(Economy.CRATE_COST).isGreaterThan(Economy.CRATE_COST * 0.9);
        assertThat(Economy.WHEEL_TOTAL_WEIGHT).isEqualTo(100);
        assertThat(Economy.CRATE_TOTAL_WEIGHT).isEqualTo(1000);
    }

    @Test
    void streakRewardsCycleWeekly() {
        assertThat(Economy.streakReward(1)).isEqualTo(50);
        assertThat(Economy.streakReward(7)).isEqualTo(500);
        assertThat(Economy.streakReward(8)).isEqualTo(50);
        assertThat(Economy.streakReward(14)).isEqualTo(500);
    }

    @Test
    void streakMathHandlesGapsAndFreezes() {
        LocalDate today = LocalDate.of(2026, 11, 9);
        assertThat(StreakMath.claim(null, 0, 0, today)).isEqualTo(new StreakMath.Outcome(1, 0, false));
        assertThat(StreakMath.claim(today.minusDays(1), 4, 0, today)).isEqualTo(new StreakMath.Outcome(5, 0, false));
        assertThat(StreakMath.claim(today.minusDays(2), 4, 0, today)).isEqualTo(new StreakMath.Outcome(1, 0, true));
        assertThat(StreakMath.claim(today.minusDays(2), 4, 1, today)).isEqualTo(new StreakMath.Outcome(5, 1, false));
        assertThat(StreakMath.claim(today.minusDays(3), 4, 1, today)).isEqualTo(new StreakMath.Outcome(1, 0, true));
        assertThat(StreakMath.claim(today.minusDays(3), 4, 2, today)).isEqualTo(new StreakMath.Outcome(5, 2, false));
        assertThat(StreakMath.effective(today.minusDays(1), 4, 0, today)).isEqualTo(4);
        assertThat(StreakMath.effective(today.minusDays(2), 4, 0, today)).isZero();
        assertThat(StreakMath.effective(today.minusDays(2), 4, 1, today)).isEqualTo(4);
    }

    @Test
    void tiersProgress() {
        assertThat(Tiers.progress(0).tier()).isEqualTo("BRONZE");
        assertThat(Tiers.progress(999).tier()).isEqualTo("BRONZE");
        assertThat(Tiers.progress(1000).tier()).isEqualTo("SILVER");
        assertThat(Tiers.progress(1750).progressPercent()).isEqualTo(50);
        assertThat(Tiers.progress(10_000).tier()).isEqualTo("DIAMOND");
        assertThat(Tiers.progress(50_000).progressPercent()).isEqualTo(100);
        assertThat(Tiers.progress(50_000).nextTier()).isNull();
    }

    @Test
    void quizShufflesAnswerPositionAndRotatesQuestions() throws Exception {
        ContentBank bank = new ContentBank(new ObjectMapper());
        assertThat(bank.quizSize()).isGreaterThanOrEqualTo(50);
        Map<Integer, Integer> positions = new HashMap<>();
        Set<String> seenForOneUser = new HashSet<>();
        LocalDate day = LocalDate.of(2026, 11, 9);
        for (int d = 0; d < bank.quizSize(); d++) {
            ContentBank.DailyQuestion q = bank.questionFor("user-abc", day.plusDays(d));
            seenForOneUser.add(q.source().id());
            assertThat(q.options().get(q.correctIndex())).isEqualTo(q.source().options().get(q.source().answer()));
        }
        for (int u = 0; u < 400; u++) {
            positions.merge(bank.questionFor("user-" + u, day).correctIndex(), 1, Integer::sum);
        }
        // un utilizator vede toata banca inainte de repetari
        assertThat(seenForOneUser).hasSize(bank.quizSize());
        // raspunsul corect apare pe toate pozitiile, nu mereu pe prima
        assertThat(positions.keySet()).containsExactlyInAnyOrder(0, 1, 2, 3);
        assertThat(positions.values()).allMatch(c -> c > 50);
    }
}
