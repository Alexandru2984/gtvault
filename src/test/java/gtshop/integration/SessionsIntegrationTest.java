package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.security.JwtTokenProvider;
import gtshop.support.AbstractIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class SessionsIntegrationTest extends AbstractIntegrationTest {
    @Autowired JwtTokenProvider tokens;

    private JsonNode login(TestUser user) throws Exception {
        return readJson(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 15) Chrome/140.0 Safari/537.36")
                .content(json.writeValueAsString(Map.of("email", user.email(), "password", "Parola-Sigura-123"))))
                .andExpect(status().isOk()).andReturn());
    }

    private String sessionId(String token) {
        return tokens.parse(token).orElseThrow().sessionId();
    }

    @Test
    void listsOnlyOwnSessionsWithCurrentMarkerAndMinimalDeviceInformation() throws Exception {
        TestUser user = register();
        TestUser other = register();
        login(user);
        JsonNode data = readJson(mvc.perform(getAs("/api/auth/sessions", user))
                .andExpect(status().isOk()).andExpect(jsonPath("$.max").value(10)).andReturn());
        assertThat(data.path("items").size()).isEqualTo(2);
        assertThat(data.at("/items/0/device").asText()).isEqualTo("Chrome · Android");
        assertThat(data.at("/items/0/current").asBoolean()).isFalse();
        assertThat(data.at("/items/1/current").asBoolean()).isTrue();
        assertThat(data.toString()).doesNotContain(user.token(), other.token(), "Mozilla", "userAgent", "password");
    }

    @Test
    void revocationIsRestrictedToOwnerAndStopsOnlyTheSelectedToken() throws Exception {
        TestUser user = register();
        TestUser other = register();
        String secondToken = login(user).path("token").asText();
        String secondId = sessionId(secondToken);
        mvc.perform(authed(delete("/api/auth/sessions/" + secondId), other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + secondToken)).andExpect(status().isOk());
        mvc.perform(authed(delete("/api/auth/sessions/" + secondId), user)).andExpect(status().isOk());
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + secondToken)).andExpect(status().isUnauthorized());
        mvc.perform(getAs("/api/user/me", user)).andExpect(status().isOk());
        mvc.perform(getAs("/api/user/me", other)).andExpect(status().isOk());
    }

    @Test
    void logoutRevokesCurrentSessionAndLeavesOtherDevicesSignedIn() throws Exception {
        TestUser user = register();
        String secondToken = login(user).path("token").asText();
        mvc.perform(postJson("/api/auth/logout", user, null)).andExpect(status().isOk());
        mvc.perform(getAs("/api/user/me", user)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + secondToken)).andExpect(status().isOk());
    }

    @Test
    void signedTokensWithoutRegisteredSessionAndExpiredSessionsAreRejected() throws Exception {
        TestUser user = register();
        String unregistered = tokens.generateToken(user.id(), 0).token();
        mvc.perform(get("/api/user/me").header("Authorization", "Bearer " + unregistered)).andExpect(status().isUnauthorized());
        jdbc.update("UPDATE user_sessions SET created_at = ?, expires_at = ? WHERE user_id = ?",
                Timestamp.from(Instant.now().minusSeconds(3600)), Timestamp.from(Instant.now().minusSeconds(1)), user.id());
        mvc.perform(getAs("/api/user/me", user)).andExpect(status().isUnauthorized());
    }

    @Test
    void lastSeenIsUpdatedAtMostOnceEveryFiveMinutes() throws Exception {
        TestUser user = register();
        jdbc.update("UPDATE user_sessions SET last_seen_at = ? WHERE user_id = ?",
                Timestamp.from(Instant.now().minusSeconds(360)), user.id());
        mvc.perform(getAs("/api/user/me", user)).andExpect(status().isOk());
        Timestamp first = jdbc.queryForObject("SELECT last_seen_at FROM user_sessions WHERE user_id = ?", Timestamp.class, user.id());
        assertThat(first.toInstant()).isAfter(Instant.now().minusSeconds(5));
        mvc.perform(getAs("/api/user/me", user)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT last_seen_at FROM user_sessions WHERE user_id = ?", Timestamp.class, user.id())).isEqualTo(first);
    }

    @Test
    void simultaneousLoginsKeepTheSessionLimitAndEvictTheOldestToken() throws Exception {
        TestUser user = register();
        // Stabilim explicit cea mai veche sesiune fără a aștepta ceasul real.
        jdbc.update("UPDATE user_sessions SET created_at = ? WHERE user_id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)), user.id());
        var requests = new ArrayList<Callable<String>>();
        for (int i = 0; i < 10; i++) requests.add(() -> login(user).path("token").asText());
        try (var executor = Executors.newFixedThreadPool(4)) {
            for (var response : executor.invokeAll(requests)) assertThat(response.get()).isNotBlank();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions WHERE user_id = ?", Integer.class, user.id())).isEqualTo(10);
        mvc.perform(getAs("/api/user/me", user)).andExpect(status().isUnauthorized());
    }

    @Test
    void exportIncludesSessionMetadataAndDeletingAccountRemovesSessions() throws Exception {
        TestUser user = register();
        mvc.perform(getAs("/api/user/export", user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].device").exists())
                .andExpect(jsonPath("$.sessions[0].token_version").doesNotExist());
        mvc.perform(postJson("/api/user/account/delete", user,
                Map.of("password", "Parola-Sigura-123", "confirmation", "STERGE"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_sessions WHERE user_id = ?", Integer.class, user.id())).isZero();
    }
}
