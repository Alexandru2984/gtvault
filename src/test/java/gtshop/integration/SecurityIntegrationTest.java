package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gtshop.support.AbstractIntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    @ParameterizedTest(name = "anonim POST {0} -> 401")
    @ValueSource(strings = {
            "/api/user/gift", "/api/gamification/spin-wheel", "/api/gamification/daily-claim",
            "/api/gamification/open-crate", "/api/checkout", "/api/cart/add", "/api/user/profile",
            "/api/gamification/fortune-cookie", "/api/proposals", "/api/guilds/select",
            "/api/games/wordle/guess", "/api/rewards/rew-mouse/reviews", "/api/reviews/rev-x/helpful",
            "/api/reviews/rev-x/report", "/api/notifications/read-all", "/api/user/account/delete", "/api/auth/logout"})
    void anonymousMutationsAreRejected(String url) throws Exception {
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @ParameterizedTest(name = "anonim GET {0} -> 401")
    @ValueSource(strings = {"/api/user/me", "/api/user/profile", "/api/user/transactions", "/api/cart",
            "/api/gamification/status", "/api/gamification/badges", "/api/quests", "/api/user/referrals",
            "/api/wishlist", "/api/wishlist/ids", "/api/notifications", "/api/notifications/unread-count",
            "/api/user/export", "/api/user/reviews", "/api/user/reviews/pending", "/api/auth/sessions"})
    void anonymousPrivateReadsAreRejected(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "public GET {0} -> 200")
    @ValueSource(strings = {"/api/rewards", "/api/gamification/leaderboard", "/api/guilds", "/api/community/activity",
            "/api/community/stats", "/api/games/wordle", "/api/rewards/rew-mouse/reviews", "/api/gamification/config"})
    void publicReadsWork(String url) throws Exception {
        mvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Nu mai exista utilizatorul implicit user-123 pentru vizitatori anonimi")
    void noDefaultUserFallback() throws Exception {
        mvc.perform(get("/api/user/profile").param("userId", "user-123")).andExpect(status().isUnauthorized());
        Integer count = jdbc.queryForObject("select count(*) from users where id = 'user-123'", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void tamperedTokenIsTreatedAsAnonymous() throws Exception {
        TestUser user = register();
        String tampered = user.token().substring(0, user.token().length() - 3) + "abc";
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + tampered)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        TestUser user = register();
        String forged = Jwts.builder().issuer("rewardhub").subject(user.id()).claim("tv", 0)
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("another-secret-another-secret-another-secret".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutIssuerIsRejected() throws Exception {
        TestUser user = register();
        String noIssuer = Jwts.builder().subject(user.id())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("test-secret-key-for-integration-tests-only-0123456789abcdef".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + noIssuer)).andExpect(status().isUnauthorized());
    }

    @Test
    void bannedUserTokenStopsWorking() throws Exception {
        TestUser user = register();
        mvc.perform(getAs("/api/user/me", user)).andExpect(status().isOk());
        jdbc.update("update users set banned = true where id = ?", user.id());
        mvc.perform(getAs("/api/user/me", user)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEverywhereInvalidatesOldTokens() throws Exception {
        TestUser user = register();
        var res = readJson(mvc.perform(postJson("/api/auth/logout-all", user, null)).andExpect(status().isOk()).andReturn());
        String newToken = res.path("token").asText();
        mvc.perform(getAs("/api/user/me", user)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + newToken)).andExpect(status().isOk());
    }

    @Test
    void errorsDoNotLeakInternals() throws Exception {
        TestUser user = register();
        mvc.perform(authed(post("/api/user/profile").contentType(MediaType.APPLICATION_JSON).content("{not json"), user))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cererea nu are un format valid."));
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/api/rewards"))
                .andExpect(status().isOk())
                .andExpect(r -> assertThat(r.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff"))
                .andExpect(r -> assertThat(r.getResponse().getHeader("X-Frame-Options")).isEqualTo("DENY"))
                .andExpect(r -> assertThat(r.getResponse().getHeader("Content-Security-Policy")).contains("default-src 'none'"));
    }

    @Test
    void corsOnlyAllowsConfiguredOrigins() throws Exception {
        mvc.perform(get("/api/rewards").header("Origin", "https://evil.example"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/rewards").header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk())
                .andExpect(r -> assertThat(r.getResponse().getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173"));
    }

    @Test
    void healthEndpointIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void unknownEndpointReturnsJson404OrAuth() throws Exception {
        TestUser user = register();
        mvc.perform(getAs("/api/does-not-exist", user)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    void rateLimitUsesRealIpOnlyFromLoopback() throws Exception {
        Map<String, String> body = Map.of("email", "nobody@rewardhub.test", "password", "x");
        for (int i = 0; i < 10; i++) {
            mvc.perform(post("/api/auth/login").header("X-Real-IP", "203.0.113.7")
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").header("X-Real-IP", "203.0.113.7")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isTooManyRequests());
        // alt IP real (alt vizitator in spatele aceluiasi nginx) nu este afectat
        mvc.perform(post("/api/auth/login").header("X-Real-IP", "203.0.113.8")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}
