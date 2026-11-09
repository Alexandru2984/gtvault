package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class CommunityIntegrationTest extends AbstractIntegrationTest {

    private Map<String, Object> proposal(String title) {
        return Map.of("title", title, "category", "Premii digitale", "description", "O descriere suficient de lungă pentru propunere.");
    }

    private String uniqueTitle() {
        return "Licență IDE " + UUID.randomUUID().toString().substring(0, 6);
    }

    // ------------------------------------------------------------------ propuneri

    @Test
    void proposalRewardOnlyFirstPerDayAndDailyCap() throws Exception {
        TestUser u = register();
        mvc.perform(postJson("/api/proposals", u, proposal(uniqueTitle()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardPoints").value(15))
                .andExpect(jsonPath("$.proposal.hasVoted").value(true))
                .andExpect(jsonPath("$.proposal.votesCount").value(1));
        mvc.perform(postJson("/api/proposals", u, proposal(uniqueTitle()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardPoints").value(0));
        mvc.perform(postJson("/api/proposals", u, proposal(uniqueTitle()))).andExpect(status().isOk());
        mvc.perform(postJson("/api/proposals", u, proposal(uniqueTitle())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DAILY_LIMIT_REACHED"));
        // 15 AP + insigna VISIONARY (25)
        assertThat(points(u)).isEqualTo(1000 + 15 + 25);
    }

    @Test
    void proposalValidation() throws Exception {
        TestUser u = register();
        String title = uniqueTitle();
        mvc.perform(postJson("/api/proposals", u, Map.of("title", "abc", "category", "Premii digitale", "description", "descriere lungă ok")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/proposals", u, Map.of("title", title, "category", "Hack", "description", "descriere lungă ok")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/proposals", u, proposal(title))).andExpect(status().isOk());
        rateLimiter.reset();
        TestUser other = register();
        mvc.perform(postJson("/api/proposals", other, proposal(title.toUpperCase())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DUPLICATE_PROPOSAL"));
    }

    @Test
    void voteRetractRevoteCannotFarmPoints() throws Exception {
        TestUser author = register();
        JsonNode created = readJson(mvc.perform(postJson("/api/proposals", author, proposal(uniqueTitle()))).andReturn());
        String id = created.at("/proposal/id").asText();
        TestUser voter = register();

        mvc.perform(postJson("/api/proposals/" + id + "/vote", voter, null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.hasVoted").value(true)).andExpect(jsonPath("$.rewardPoints").value(5))
                .andExpect(jsonPath("$.votesCount").value(2));
        for (int i = 0; i < 5; i++) {
            mvc.perform(postJson("/api/proposals/" + id + "/vote", voter, null)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.rewardPoints").value(0));
        }
        // 1 vot + 5 comutari -> vot activ? 1 (vot) + 5 comutari = retras
        JsonNode list = readJson(mvc.perform(getAs("/api/proposals", voter)).andReturn());
        for (JsonNode p : list) {
            if (id.equals(p.path("id").asText())) {
                assertThat(p.path("hasVoted").asBoolean()).isFalse();
                assertThat(p.path("votesCount").asInt()).isEqualTo(1);
            }
        }
        assertThat(points(voter)).isEqualTo(1005);
        assertThat(jdbc.queryForObject("select count(*) from proposal_votes where proposal_id = ? and user_id = ?",
                Integer.class, id, voter.id())).isEqualTo(1);
        mvc.perform(postJson("/api/proposals/nu-exista/vote", voter, null)).andExpect(status().isNotFound());
    }

    @Test
    void rewardedVotesAreCappedPerDay() throws Exception {
        TestUser voter = register();
        for (int i = 0; i < 7; i++) {
            TestUser author = register();
            String id = readJson(mvc.perform(postJson("/api/proposals", author, proposal(uniqueTitle()))).andReturn())
                    .at("/proposal/id").asText();
            mvc.perform(postJson("/api/proposals/" + id + "/vote", voter, null)).andExpect(status().isOk());
        }
        assertThat(points(voter)).isEqualTo(1000 + 5 * 5);
    }

    @Test
    void closedProposalsCannotReceiveVotes() throws Exception {
        TestUser author = register();
        String id = readJson(mvc.perform(postJson("/api/proposals", author, proposal(uniqueTitle()))).andReturn())
                .at("/proposal/id").asText();
        jdbc.update("update reward_proposals set status = 'REJECTED' where id = ?", id);
        TestUser voter = register();
        mvc.perform(postJson("/api/proposals/" + id + "/vote", voter, null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("VOTING_CLOSED"));
    }

    // ------------------------------------------------------------------ cadouri

    @Test
    void freshAccountsCannotGift() throws Exception {
        TestUser sender = register();
        TestUser friend = register();
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.referralCode(), "amount", 100)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("GIFT_NOT_ALLOWED"));
        assertThat(points(sender)).isEqualTo(1000);
        assertThat(points(friend)).isEqualTo(1000);
    }

    @Test
    void giftTransfersPointsButNotXp() throws Exception {
        TestUser sender = register();
        makeVeteran(sender, 500);
        TestUser friend = register();
        mvc.perform(getAs("/api/user/gift/recipient?code=" + friend.referralCode().toLowerCase(), sender))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Tester Activ"))
                .andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.referralCode(), "amount", 100,
                        "message", "Spor la învățat!")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amount").value(100));
        assertThat(points(sender)).isEqualTo(1000 - 100 + 25); // + insigna GENEROUS
        assertThat(points(friend)).isEqualTo(1100);
        assertThat(xp(friend)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'GIFT'",
                Integer.class, friend.id())).isEqualTo(1);
    }

    @Test
    void giftValidation() throws Exception {
        TestUser sender = register();
        makeVeteran(sender, 500);
        TestUser friend = register();
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", sender.referralCode(), "amount", 100)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.email(), "amount", 100)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", "GT-ZZZZZZ", "amount", 100)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.referralCode(), "amount", 10)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.referralCode(), "amount", 5000)))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.referralCode(), "amount", "100.5")))
                .andExpect(status().isBadRequest());
        jdbc.update("update users set banned = true where id = ?", friend.id());
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.referralCode(), "amount", 100)))
                .andExpect(status().isNotFound());
        assertThat(points(sender)).isEqualTo(1000);
    }

    @Test
    void dailyGiftCap() throws Exception {
        TestUser sender = register();
        makeVeteran(sender, 500);
        setPoints(sender, 10_000);
        TestUser friend = register();
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.referralCode(), "amount", 1500))).andExpect(status().isOk());
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.referralCode(), "amount", 600)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("DAILY_LIMIT_REACHED"));
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.referralCode(), "amount", 500))).andExpect(status().isOk());
        advanceDays(1);
        mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", friend.referralCode(), "amount", 500))).andExpect(status().isOk());
    }

    @Test
    void concurrentGiftsNeverOverspend() throws Exception {
        TestUser sender = register();
        makeVeteran(sender, 500);
        setPoints(sender, 250);
        TestUser a = register();
        TestUser b = register();
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                String code = i % 2 == 0 ? a.referralCode() : b.referralCode();
                calls.add(() -> mvc.perform(postJson("/api/user/gift", sender, Map.of("recipient", code, "amount", 100)))
                        .andReturn().getResponse().getStatus());
            }
            int ok = 0;
            for (Future<Integer> f : pool.invokeAll(calls)) {
                if (f.get() == 200) {
                    ok++;
                }
            }
            assertThat(ok).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
        assertThat(points(sender)).isEqualTo(250 - 200 + 25);
        assertThat(points(a) + points(b)).isEqualTo(2200);
    }

    @Test
    void concurrentGiftsToSameRecipientAreAllCredited() throws Exception {
        TestUser recipient = register();
        List<TestUser> senders = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            TestUser s = register();
            makeVeteran(s, 500);
            senders.add(s);
        }
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (TestUser s : senders) {
                calls.add(() -> mvc.perform(postJson("/api/user/gift", s, Map.of("recipient", recipient.referralCode(), "amount", 50)))
                        .andReturn().getResponse().getStatus());
            }
            for (Future<Integer> f : pool.invokeAll(calls)) {
                assertThat(f.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(points(recipient)).isEqualTo(1000 + 6 * 50);
        Integer ledger = jdbc.queryForObject("select balance_after from point_transactions where user_id = ? order by created_at desc, balance_after desc limit 1",
                Integer.class, recipient.id());
        assertThat(ledger).isEqualTo(1300);
    }

    // ------------------------------------------------------------------ ghilde, clasament, recomandari

    @Test
    void guildSwitchHasCooldown() throws Exception {
        TestUser u = register();
        mvc.perform(postJson("/api/guilds/select", u, Map.of("guildId", "PYTHON_WIZARDS"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.guild.id").value("PYTHON_WIZARDS"));
        mvc.perform(postJson("/api/guilds/select", u, Map.of("guildId", "DEVOPS_TITANS")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("GUILD_COOLDOWN"));
        mvc.perform(postJson("/api/guilds/select", u, Map.of("guildId", "HACKERS"))).andExpect(status().isBadRequest());
        advanceDays(8);
        mvc.perform(postJson("/api/guilds/select", u, Map.of("guildId", "DEVOPS_TITANS"))).andExpect(status().isOk());
        JsonNode overview = readJson(mvc.perform(getAs("/api/guilds", null)).andExpect(status().isOk()).andReturn());
        assertThat(overview.path("guilds")).hasSize(4);
        assertThat(overview.at("/guilds/0/rank").asInt()).isEqualTo(1);
    }

    @Test
    void weeklyLeaderboardRanksByEarnedXp() throws Exception {
        TestUser top = register();
        TestUser low = register();
        mvc.perform(postJson("/api/gamification/daily-claim", low, null)).andExpect(status().isOk());
        mvc.perform(postJson("/api/gamification/daily-claim", top, null)).andExpect(status().isOk());
        mvc.perform(postJson("/api/gamification/fortune-cookie", top, null)).andExpect(status().isOk());
        // un cadou mare nu urca pe nimeni in clasament
        setPoints(low, 1_000_000);

        JsonNode lb = readJson(mvc.perform(getAs("/api/leaderboard?period=week&limit=50", top)).andExpect(status().isOk()).andReturn());
        int topRank = -1;
        int lowRank = -1;
        for (JsonNode e : lb.path("entries")) {
            if (top.id().equals(e.path("id").asText())) topRank = e.path("rank").asInt();
            if (low.id().equals(e.path("id").asText())) lowRank = e.path("rank").asInt();
        }
        assertThat(lb.at("/me/id").asText()).isEqualTo(top.id());
        assertThat(lb.at("/me/isMe").asBoolean()).isTrue();
        if (topRank > 0 && lowRank > 0) {
            assertThat(topRank).isLessThan(lowRank);
        }
        mvc.perform(getAs("/api/leaderboard?period=year", null)).andExpect(status().isBadRequest());
        mvc.perform(getAs("/api/leaderboard?period=all", null)).andExpect(status().isOk()).andExpect(jsonPath("$.me").doesNotExist());
    }

    @Test
    void streakLeaderboardIgnoresBrokenStreaks() throws Exception {
        TestUser broken = register();
        jdbc.update("update users set streak_days = 999, last_daily_claim_date = '2026-10-01' where id = ?", broken.id());
        TestUser alive = register();
        jdbc.update("update users set streak_days = 998, last_daily_claim_date = '2026-11-08' where id = ?", alive.id());
        JsonNode lb = readJson(mvc.perform(getAs("/api/leaderboard?period=streak&limit=50", alive)).andExpect(status().isOk()).andReturn());
        List<String> ids = new ArrayList<>();
        lb.path("entries").forEach(e -> ids.add(e.path("id").asText()));
        assertThat(ids).contains(alive.id()).doesNotContain(broken.id());
        assertThat(lb.at("/me/value").asInt()).isEqualTo(998);
        jdbc.update("update users set streak_days = 0 where id in (?, ?)", broken.id(), alive.id());
    }

    @Test
    void referralInfoAndRecruiterBadge() throws Exception {
        TestUser host = register();
        register(uniqueEmail(), "Ioana Pop", host.referralCode());
        JsonNode r = readJson(mvc.perform(getAs("/api/user/referrals", host)).andExpect(status().isOk()).andReturn());
        assertThat(r.path("invitedCount").asInt()).isEqualTo(1);
        assertThat(r.path("earnedPoints").asInt()).isEqualTo(300);
        assertThat(r.path("referralLink").asText()).endsWith("/?ref=" + host.referralCode());
        assertThat(r.at("/friends/0/name").asText()).isEqualTo("Ioana");
        // 1000 + 300 recomandare + 75 insigna RECRUITER
        assertThat(points(host)).isEqualTo(1375);
    }

    @Test
    void publicConfigExposesRealOdds() throws Exception {
        JsonNode cfg = readJson(mvc.perform(getAs("/api/gamification/config", null)).andExpect(status().isOk()).andReturn());
        double sum = 0;
        for (JsonNode s : cfg.at("/wheel/segments")) {
            sum += s.path("probability").asDouble();
        }
        assertThat(sum).isEqualTo(100.0);
        assertThat(cfg.at("/wheel/expectedValue").asDouble()).isLessThan(cfg.at("/wheel/cost").asDouble());
        assertThat(cfg.at("/crate/expectedValue").asDouble()).isLessThan(cfg.at("/crate/cost").asDouble());
    }

    @Test
    void checkoutUsesServerPriceAndUnlocksFirstOrderBadge() throws Exception {
        TestUser u = register();
        JsonNode res = readJson(mvc.perform(postJson("/api/checkout", u, Map.of("items",
                List.of(Map.of("rewardId", "rew-sticker", "name", "Fals", "price", 1, "quantity", 1))))).andExpect(status().isOk()).andReturn());
        assertThat(res.path("success").asBoolean()).isTrue();
        assertThat(res.at("/purchasedItems/0/name").asText()).isEqualTo("Sticker GT");
        assertThat(points(u)).isEqualTo(1000 - 180 + 25);
        assertThat(jdbc.queryForObject("select count(*) from user_badges where user_id = ? and badge_code = 'FIRST_ORDER'",
                Integer.class, u.id())).isEqualTo(1);
    }
}
