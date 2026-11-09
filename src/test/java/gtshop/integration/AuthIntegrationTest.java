package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.support.AbstractIntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AuthIntegrationTest extends AbstractIntegrationTest {

    private org.springframework.test.web.servlet.ResultActions doRegister(Map<String, ?> body) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    @Test
    void registerGivesWelcomeBonusAndNoSecretsInResponse() throws Exception {
        String email = uniqueEmail();
        String body = doRegister(Map.of("email", email, "password", "Parola-Sigura-123", "name", "Ana Popescu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.user.activityPoints").value(1000))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.referralCode").value(org.hamcrest.Matchers.matchesPattern("GT-[A-Z2-9]{6}")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password").doesNotContain("$2a$");
        Integer tx = jdbc.queryForObject("select count(*) from point_transactions t join users u on u.id = t.user_id where u.email = ?",
                Integer.class, email);
        assertThat(tx).isEqualTo(1);
    }

    @Test
    void duplicateEmailIsCaseInsensitive() throws Exception {
        String email = uniqueEmail();
        register(email, "Primul Cont", null);
        doRegister(Map.of("email", email.toUpperCase(), "password", "Parola-Sigura-123", "name", "Al Doilea"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"));
    }

    @Test
    void rejectsHtmlInNameAndWeakPasswords() throws Exception {
        doRegister(Map.of("email", uniqueEmail(), "password", "Parola-Sigura-123", "name", "<img src=x onerror=alert(1)>"))
                .andExpect(status().isBadRequest());
        rateLimiter.reset();
        doRegister(Map.of("email", uniqueEmail(), "password", "1234567", "name", "Ion"))
                .andExpect(status().isBadRequest());
        rateLimiter.reset();
        doRegister(Map.of("email", uniqueEmail(), "password", "aaaaaaaaaa", "name", "Ion"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginIsCaseInsensitiveAndUniformOnFailure() throws Exception {
        TestUser user = register();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.email().toUpperCase(), "password", "Parola-Sigura-123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").isNotEmpty());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.email(), "password", "gresita-gresita"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "nu-exista@rewardhub.test", "password", "gresita-gresita"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    @Test
    void referralRewardsBothSidesAndNotifiesReferrer() throws Exception {
        TestUser referrer = register();
        TestUser invited = register(uniqueEmail(), "Prieten Nou", referrer.referralCode().toLowerCase());
        assertThat(points(invited)).isEqualTo(1200);
        // 1000 + 300 bonus recomandare + 75 insigna RECRUITER
        assertThat(points(referrer)).isEqualTo(1375);
        Integer notifs = jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = 'REFERRAL'",
                Integer.class, referrer.id());
        assertThat(notifs).isEqualTo(1);
    }

    @Test
    void invalidReferralCodeIsRejected() throws Exception {
        doRegister(Map.of("email", uniqueEmail(), "password", "Parola-Sigura-123", "name", "Ion", "referralCode", "GT-NOPE00"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_REFERRAL"));
    }

    @Test
    void configuredAdminEmailGetsAdminRole() throws Exception {
        TestUser admin = register("admin@rewardhub.test", "Admin GT", null);
        JsonNode me = readJson(mvc.perform(getAs("/api/user/me", admin)).andReturn());
        assertThat(me.path("role").asText()).isEqualTo("ADMIN");
    }

    @Test
    void profileUpdateValidatesNameAndAvatar() throws Exception {
        TestUser user = register();
        mvc.perform(postJson("/api/user/profile", user, Map.of("avatar", "javascript:alert(1)")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/user/profile", user, Map.of("avatar", "x\" onerror=\"alert(1)")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/user/profile", user, Map.of("name", "<script>alert(1)</script>")))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/user/profile", user, Map.of("name", "Mihai Ionescu", "avatar", "images/avatars/robot.svg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.name").value("Mihai Ionescu"))
                .andExpect(jsonPath("$.user.avatar").value("images/avatars/robot.svg"));
    }

    @Test
    void changePasswordRotatesTokens() throws Exception {
        TestUser user = register();
        mvc.perform(postJson("/api/auth/password", user, Map.of("currentPassword", "gresita", "newPassword", "Parola-Noua-456")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("WRONG_PASSWORD"));
        JsonNode res = readJson(mvc.perform(postJson("/api/auth/password", user,
                Map.of("currentPassword", "Parola-Sigura-123", "newPassword", "Parola-Noua-456"))).andExpect(status().isOk()).andReturn());
        mvc.perform(getAs("/api/user/me", user)).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/user/me")
                .header("Authorization", "Bearer " + res.path("token").asText())).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.email(), "password", "Parola-Noua-456"))))
                .andExpect(status().isOk());
    }
}
