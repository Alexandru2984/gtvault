package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class GamesAndQuestsIntegrationTest extends AbstractIntegrationTest {
    // Ponderi cumulate roata: [0,22)=10, [22,30)=100, [30,50)=25, [50,51)=500, [51,65)=40, [65,85)=0, [85,97)=60, [97,100)=250
    private static final int ROLL_10 = 0;
    private static final int ROLL_NOTHING = 70;
    private static final int ROLL_JACKPOT = 50;

    private JsonNode spin(TestUser u) throws Exception {
        return readJson(mvc.perform(postJson("/api/gamification/spin-wheel", u, null)).andExpect(status().isOk()).andReturn());
    }

    @Test
    void firstSpinOfTheDayIsFreeAndWinsAreNotXp() throws Exception {
        TestUser u = register();
        random.push(ROLL_10);
        JsonNode r = spin(u);
        assertThat(r.path("free").asBoolean()).isTrue();
        assertThat(r.path("prizeIndex").asInt()).isZero();
        assertThat(r.path("wonPoints").asInt()).isEqualTo(10);
        assertThat(r.path("badgesUnlocked").toString()).contains("LUCKY_SPINNER");
        assertThat(points(u)).isEqualTo(1000 + 10 + 25);
        assertThat(xp(u)).isEqualTo(25); // doar insigna; castigul de la roata nu e XP

        random.push(ROLL_NOTHING);
        JsonNode paid = spin(u);
        assertThat(paid.path("free").asBoolean()).isFalse();
        assertThat(paid.path("cost").asInt()).isEqualTo(50);
        assertThat(paid.path("wonPoints").asInt()).isZero();
        assertThat(points(u)).isEqualTo(1035 - 50);

        advanceDays(1);
        random.push(ROLL_NOTHING);
        assertThat(spin(u).path("free").asBoolean()).isTrue();
    }

    @Test
    void jackpotUnlocksLegendaryBadge() throws Exception {
        TestUser u = register();
        random.push(ROLL_JACKPOT);
        JsonNode r = spin(u);
        assertThat(r.path("wonPoints").asInt()).isEqualTo(500);
        assertThat(r.path("badgesUnlocked").toString()).contains("JACKPOT");
        assertThat(points(u)).isEqualTo(1000 + 500 + 25 + 300);
    }

    @Test
    void paidSpinsAreCappedDaily() throws Exception {
        TestUser u = register();
        setPoints(u, 10_000);
        spin(u); // gratuita
        for (int i = 0; i < 10; i++) {
            random.push(ROLL_NOTHING);
            spin(u);
        }
        mvc.perform(postJson("/api/gamification/spin-wheel", u, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DAILY_LIMIT_REACHED"));
    }

    @Test
    void insufficientPointsDoesNotConsumeDailyLimit() throws Exception {
        TestUser u = register();
        spin(u); // gratuita
        setPoints(u, 10);
        mvc.perform(postJson("/api/gamification/spin-wheel", u, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INSUFFICIENT_POINTS"));
        mvc.perform(postJson("/api/gamification/open-crate", u, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INSUFFICIENT_POINTS"));
        JsonNode s = readJson(mvc.perform(getAs("/api/gamification/status", u)).andReturn());
        assertThat(s.path("paidSpinsLeft").asInt()).isEqualTo(10);
        assertThat(s.path("cratesLeft").asInt()).isEqualTo(5);
        assertThat(points(u)).isEqualTo(10);
    }

    @Test
    void mythicCrateDropAndDailyCap() throws Exception {
        TestUser u = register();
        random.push(999);
        JsonNode r = readJson(mvc.perform(postJson("/api/gamification/open-crate", u, null)).andExpect(status().isOk()).andReturn());
        assertThat(r.path("tier").asText()).isEqualTo("MYTHIC");
        assertThat(r.path("wonPoints").asInt()).isEqualTo(2500);
        assertThat(r.path("badgesUnlocked").toString()).contains("CRATE_HUNTER").contains("MYTHIC_LUCK");
        assertThat(points(u)).isEqualTo(1000 - 150 + 2500 + 25 + 300);
        assertThat(xp(u)).isEqualTo(325);

        for (int i = 0; i < 4; i++) {
            random.push(0);
            mvc.perform(postJson("/api/gamification/open-crate", u, null)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.tier").value("COMMON"));
        }
        mvc.perform(postJson("/api/gamification/open-crate", u, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DAILY_LIMIT_REACHED"));
    }

    @Test
    void ledgerIsPagedAndFiltered() throws Exception {
        TestUser u = register();
        random.push(ROLL_10);
        spin(u);
        mvc.perform(postJson("/api/gamification/daily-claim", u, null)).andExpect(status().isOk());
        JsonNode all = readJson(mvc.perform(getAs("/api/user/transactions?size=2", u)).andExpect(status().isOk()).andReturn());
        assertThat(all.path("items")).hasSize(2);
        assertThat(all.path("totalItems").asInt()).isEqualTo(4); // bun venit, castig roata, insigna, streak
        JsonNode games = readJson(mvc.perform(getAs("/api/user/transactions?filter=games", u)).andReturn());
        assertThat(games.path("items")).hasSize(1);
        assertThat(games.at("/items/0/type").asText()).isEqualTo("LUCKY_WHEEL_WIN");
        assertThat(games.at("/items/0/label").asText()).isEqualTo("Câștig roată");
        mvc.perform(getAs("/api/user/transactions?filter=hack", u)).andExpect(status().isBadRequest());
    }

    @Test
    void questsAreGeneratedTrackedAndClaimedOnce() throws Exception {
        TestUser u = register();
        JsonNode q = readJson(mvc.perform(getAs("/api/quests", u)).andExpect(status().isOk()).andReturn());
        assertThat(q.path("daily")).hasSize(4);
        assertThat(q.path("weekly")).hasSize(3);
        assertThat(q.at("/daily/0/key").asText()).isEqualTo("DAILY_LOGIN");
        String loginQuest = q.at("/daily/0/id").asText();

        mvc.perform(postJson("/api/quests/" + loginQuest + "/claim", u, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("QUEST_NOT_COMPLETED"));

        mvc.perform(postJson("/api/gamification/daily-claim", u, null)).andExpect(status().isOk());
        JsonNode after = readJson(mvc.perform(getAs("/api/quests", u)).andReturn());
        assertThat(after.at("/daily/0/completed").asBoolean()).isTrue();
        assertThat(after.path("claimable").asInt()).isGreaterThanOrEqualTo(1);
        // generarea este idempotenta
        assertThat(after.at("/daily/0/id").asText()).isEqualTo(loginQuest);

        int before = points(u);
        mvc.perform(postJson("/api/quests/" + loginQuest + "/claim", u, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardPoints").value(30));
        assertThat(points(u)).isEqualTo(before + 30);
        mvc.perform(postJson("/api/quests/" + loginQuest + "/claim", u, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ALREADY_CLAIMED"));

        TestUser other = register();
        mvc.perform(postJson("/api/quests/" + loginQuest + "/claim", other, null)).andExpect(status().isNotFound());

        advanceDays(1);
        JsonNode tomorrow = readJson(mvc.perform(getAs("/api/quests", u)).andReturn());
        assertThat(tomorrow.at("/daily/0/id").asText()).isNotEqualTo(loginQuest);
        assertThat(tomorrow.at("/daily/0/completed").asBoolean()).isFalse();
    }

    @Test
    void freeSpinCompletesSpinQuestWhenSelected() throws Exception {
        TestUser u = register();
        JsonNode q = readJson(mvc.perform(getAs("/api/quests", u)).andReturn());
        random.push(ROLL_NOTHING);
        spin(u);
        JsonNode after = readJson(mvc.perform(getAs("/api/quests", u)).andReturn());
        for (JsonNode quest : after.path("daily")) {
            if ("SPIN_WHEEL".equals(quest.path("key").asText())) {
                assertThat(quest.path("completed").asBoolean()).isTrue();
            }
        }
        for (JsonNode quest : after.path("weekly")) {
            if ("SPIN_WHEEL".equals(quest.path("key").asText())) {
                assertThat(quest.path("currentProgress").asInt()).isEqualTo(1);
            }
        }
        assertThat(q.path("daily")).hasSize(4);
    }

    @Test
    void badgeCatalogShowsProgress() throws Exception {
        TestUser u = register();
        mvc.perform(postJson("/api/gamification/daily-claim", u, null)).andExpect(status().isOk());
        JsonNode b = readJson(mvc.perform(getAs("/api/gamification/badges", u)).andExpect(status().isOk()).andReturn());
        assertThat(b.path("totalCount").asInt()).isGreaterThanOrEqualTo(20);
        JsonNode streak3 = null;
        for (JsonNode badge : b.path("badges")) {
            if ("STREAK_3".equals(badge.path("code").asText())) {
                streak3 = badge;
            }
        }
        assertThat(streak3).isNotNull();
        assertThat(streak3.path("unlocked").asBoolean()).isFalse();
        assertThat(streak3.path("progress").asInt()).isEqualTo(1);
        assertThat(streak3.path("target").asInt()).isEqualTo(3);
    }
}
