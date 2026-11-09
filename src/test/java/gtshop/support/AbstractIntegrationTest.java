package gtshop.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gtshop.security.RateLimiter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Baza pentru testele de integrare: aplicatia completa + PostgreSQL 18 real (aceeasi versiune ca productia),
 * schema creata exclusiv prin migrarile Flyway si validata de Hibernate (ddl-auto=validate).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AbstractIntegrationTest.ClockConfig.class)
public abstract class AbstractIntegrationTest {

    /** Luni, 9 noiembrie 2026, 12:00 ora Romaniei. */
    public static final Instant START = Instant.parse("2026-11-09T10:00:00Z");

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:18-alpine")).withReuse(false);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    public static class ClockConfig {
        @Bean
        @Primary
        public MutableClock testClock() {
            return new MutableClock(START);
        }

        @Bean
        @Primary
        public ScriptedRandom scriptedRandom() {
            return new ScriptedRandom();
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected MutableClock clock;
    @Autowired
    protected RateLimiter rateLimiter;
    @Autowired
    protected ScriptedRandom random;

    @BeforeEach
    void resetSharedState() {
        rateLimiter.reset();
        clock.set(START);
        random.clear();
    }

    // ------------------------------------------------------------------ helpers

    public record TestUser(String id, String email, String token, String referralCode) {}

    protected String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 10) + "@rewardhub.test";
    }

    protected TestUser register() throws Exception {
        return register(uniqueEmail(), "Tester Activ", null);
    }

    protected TestUser register(String email, String name, String referral) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", "Parola-Sigura-123");
        body.put("name", name);
        if (referral != null) body.put("referralCode", referral);
        JsonNode res = readJson(mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn());
        if (!res.path("success").asBoolean()) {
            throw new IllegalStateException("Register failed: " + res);
        }
        rateLimiter.reset();
        return new TestUser(res.at("/user/id").asText(), email, res.path("token").asText(), res.at("/user/referralCode").asText());
    }

    /** Pune soldul direct in baza de date (doar pentru pregatirea testelor). */
    protected void setPoints(TestUser user, int points) {
        jdbc.update("update users set activity_points = ? where id = ?", points, user.id());
    }

    /** Cont „vechi” si activ: trece de regulile anti-abuz pentru cadouri. */
    protected void makeVeteran(TestUser user, int xp) {
        jdbc.update("update users set created_at = ?, total_points_earned = ? where id = ?",
                java.sql.Timestamp.from(clock.instant().minus(java.time.Duration.ofDays(60))), xp, user.id());
    }

    protected int xp(TestUser user) {
        Integer p = jdbc.queryForObject("select total_points_earned from users where id = ?", Integer.class, user.id());
        return p == null ? 0 : p;
    }

    protected void advanceDays(int days) {
        clock.advance(java.time.Duration.ofDays(days));
    }

    protected int points(TestUser user) {
        Integer p = jdbc.queryForObject("select activity_points from users where id = ?", Integer.class, user.id());
        return p == null ? 0 : p;
    }

    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, TestUser user) {
        return builder.header("Authorization", "Bearer " + user.token());
    }

    protected MockHttpServletRequestBuilder postJson(String url, TestUser user, Object body) throws Exception {
        MockHttpServletRequestBuilder b = post(url).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "{}" : json.writeValueAsString(body));
        return user == null ? b : authed(b, user);
    }

    protected MockHttpServletRequestBuilder getAs(String url, TestUser user) {
        MockHttpServletRequestBuilder b = get(url);
        return user == null ? b : authed(b, user);
    }

    protected JsonNode readJson(MvcResult result) throws Exception {
        String content = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return content.isEmpty() ? json.createObjectNode() : json.readTree(content);
    }
}
