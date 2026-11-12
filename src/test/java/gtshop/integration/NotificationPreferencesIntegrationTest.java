package gtshop.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import gtshop.service.NotificationService;
import gtshop.service.WishlistAlerts;
import gtshop.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class NotificationPreferencesIntegrationTest extends AbstractIntegrationTest {
    @Autowired NotificationService notifications;
    @Autowired WishlistAlerts wishlistAlerts;
    @Autowired PlatformTransactionManager transactions;

    private Map<String, Boolean> preferences(boolean sales, boolean stock, boolean progress, boolean community, boolean announcements) {
        return Map.of("wishlistSales", sales, "wishlistStock", stock, "progress", progress,
                "community", community, "announcements", announcements);
    }

    private void save(TestUser user, Map<String, Boolean> preferences) throws Exception {
        mvc.perform(authed(put("/api/notifications/preferences").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(preferences)), user)).andExpect(status().isOk());
    }

    private int count(TestUser user, String type) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE user_id = ? AND type = ?",
                Integer.class, user.id(), type);
    }

    @Test
    void preferencesDefaultToEnabledAndUpdatesArePrivateAndPersistent() throws Exception {
        TestUser user = register();
        TestUser other = register();
        mvc.perform(getAs("/api/notifications/preferences", user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.wishlistSales").value(true))
                .andExpect(jsonPath("$.preferences.announcements").value(true));
        save(user, preferences(false, true, false, true, false));
        JsonNode saved = readJson(mvc.perform(getAs("/api/notifications/preferences", user)).andExpect(status().isOk()).andReturn());
        assertThat(saved.path("preferences")).isEqualTo(json.valueToTree(preferences(false, true, false, true, false)));
        mvc.perform(getAs("/api/notifications/preferences", other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.progress").value(true))
                .andExpect(jsonPath("$.preferences.announcements").value(true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_preferences WHERE user_id = ?", Integer.class, user.id())).isEqualTo(1);
    }

    @Test
    void anonymousAndIncompleteUpdatesCannotChangePreferences() throws Exception {
        TestUser user = register();
        mvc.perform(getAs("/api/notifications/preferences", null)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/notifications/preferences").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(preferences(false, false, false, false, false)))).andExpect(status().isUnauthorized());
        mvc.perform(authed(put("/api/notifications/preferences").contentType(MediaType.APPLICATION_JSON)
                .content("{\"progress\":false}"), user)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_preferences WHERE user_id = ?", Integer.class, user.id())).isZero();
    }

    @Test
    void optionalAlertsAreSuppressedWhileOrdersAccountAndModerationRemainActive() throws Exception {
        TestUser user = register();
        save(user, preferences(false, false, false, false, false));
        for (String type : List.of("BADGE", "QUEST", "STREAK", "GOAL", "REVIEW", "PROPOSAL", "ANNOUNCEMENT")) {
            notifications.notify(user.id(), type, "Progres test", "Detalii", "🔔", "#/profil");
            assertThat(count(user, type)).as(type).isZero();
        }
        for (String type : List.of("ORDER", "ACCOUNT", "GIFT", "REFERRAL", "MODERATION")) {
            notifications.notify(user.id(), type, "Mesaj important", "Detalii", "🔔", "#/profil");
            assertThat(count(user, type)).as(type).isEqualTo(1);
        }
        save(user, preferences(true, true, true, true, true));
        notifications.notify(user.id(), "BADGE", "Progres test", "Detalii", "🔔", "#/profil");
        assertThat(count(user, "BADGE")).isEqualTo(1);
    }

    @Test
    void bulkWishlistAlertsRespectSaleAndStockChoicesSeparatelyAndKeepLegacyDefaults() throws Exception {
        TestUser sales = register();
        TestUser stock = register();
        TestUser legacy = register();
        for (TestUser user : List.of(sales, stock, legacy)) {
            mvc.perform(authed(put("/api/wishlist/rew-mouse"), user)).andExpect(status().isOk());
        }
        save(sales, preferences(true, false, true, true, true));
        save(stock, preferences(false, true, true, true, true));
        new TransactionTemplate(transactions).executeWithoutResult(tx -> wishlistAlerts.dealStarted("rew-mouse", "Mouse GT", 20));
        assertThat(count(sales, "WISHLIST")).isEqualTo(1);
        assertThat(count(stock, "WISHLIST")).isZero();
        assertThat(count(legacy, "WISHLIST")).isEqualTo(1);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> wishlistAlerts.backInStock("rew-mouse", "Mouse GT"));
        assertThat(count(sales, "WISHLIST")).isEqualTo(1);
        assertThat(count(stock, "WISHLIST")).isEqualTo(1);
        assertThat(count(legacy, "WISHLIST")).isEqualTo(2);
    }

    @Test
    void preferenceUpdatesKeepExistingNotificationsAndDataExportIncludesChoices() throws Exception {
        TestUser user = register();
        notifications.notify(user.id(), "BADGE", "Progres test", "Detalii", "🔔", "#/profil");
        save(user, preferences(false, false, false, false, false));
        assertThat(count(user, "BADGE")).isEqualTo(1);
        mvc.perform(getAs("/api/user/export", user)).andExpect(status().isOk())
                .andExpect(jsonPath("$.notification_preferences[0].progress").value(false));
        mvc.perform(postJson("/api/user/account/delete", user,
                Map.of("password", "Parola-Sigura-123", "confirmation", "STERGE"))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification_preferences WHERE user_id = ?", Integer.class, user.id())).isZero();
    }
}
