package gtshop.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SavingGoalAlerts {
    private static final Logger log = LoggerFactory.getLogger(SavingGoalAlerts.class);
    private final JdbcTemplate jdbc;
    private final SavingGoalService goals;

    public SavingGoalAlerts(JdbcTemplate jdbc, SavingGoalService goals) {
        this.jdbc = jdbc;
        this.goals = goals;
    }

    @Scheduled(fixedDelayString = "${rewardhub.goals-alerts-delay-ms:60000}",
            initialDelayString = "${rewardhub.goals-alerts-delay-ms:60000}")
    public void checkPending() {
        // Mai întâi cele neverificate de mult timp, astfel încât obiectivele dificile să nu blocheze coada.
        var users = jdbc.queryForList("""
                SELECT g.user_id FROM saving_goals g JOIN users u ON u.id = g.user_id
                WHERE g.notified_at IS NULL AND NOT u.banned
                ORDER BY g.last_checked_at NULLS FIRST, g.selected_at, g.user_id LIMIT 100
                """, String.class);
        for (String userId : users) {
            try { goals.check(userId); }
            catch (RuntimeException e) {
                // O alertă nereușită este reîncercată în lotul următor, fără a opri celelalte conturi.
                log.warn("Verificare obiectiv amânată: {}", e.getClass().getSimpleName());
            }
        }
    }
}
