package gtshop.service;

import gtshop.model.UserProfile;
import gtshop.security.JwtTokenProvider;
import gtshop.web.ApiException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Sesiuni revocabile. Ceasul real este același folosit de JWT, independent de calendarul jocurilor. */
@Service
public class SessionService {
    public static final int MAX_SESSIONS = 10;
    private final JdbcTemplate jdbc;
    private final JwtTokenProvider tokens;
    private final WalletService wallet;

    public SessionService(JdbcTemplate jdbc, JwtTokenProvider tokens, WalletService wallet) {
        this.jdbc = jdbc;
        this.tokens = tokens;
        this.wallet = wallet;
    }

    public record SessionView(String id, String device, Instant createdAt, Instant lastSeenAt,
                              Instant expiresAt, boolean current) {}

    /** Apelantul deține blocarea utilizatorului, inclusiv la autentificări simultane. */
    @Transactional(propagation = Propagation.MANDATORY)
    public JwtTokenProvider.IssuedToken issue(UserProfile user, String userAgent) {
        jdbc.update("DELETE FROM user_sessions WHERE user_id = ? AND (expires_at <= ? OR token_version <> ?)",
                user.getId(), Timestamp.from(Instant.now()), user.getTokenVersion());
        // Păstrăm cele mai noi nouă sesiuni înainte de a crea sesiunea nouă.
        jdbc.update("""
                DELETE FROM user_sessions WHERE user_id = ? AND id IN
                (SELECT id FROM user_sessions WHERE user_id = ? ORDER BY created_at DESC, id DESC OFFSET ?)
                """, user.getId(), user.getId(), MAX_SESSIONS - 1);
        var issued = tokens.generateToken(user.getId(), user.getTokenVersion());
        jdbc.update("""
                INSERT INTO user_sessions (id, user_id, token_version, device, created_at, last_seen_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, issued.id(), user.getId(), user.getTokenVersion(), device(userAgent),
                Timestamp.from(issued.issuedAt()), Timestamp.from(issued.issuedAt()), Timestamp.from(issued.expiresAt()));
        return issued;
    }

    @Transactional
    public boolean accepts(JwtTokenProvider.TokenClaims claims) {
        Instant now = Instant.now();
        boolean valid = Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_sessions
                WHERE id = ? AND user_id = ? AND token_version = ? AND expires_at > ?)
                """, Boolean.class, claims.sessionId(), claims.userId(), claims.tokenVersion(), Timestamp.from(now)));
        if (valid) {
            // Actualizare condiționată la cel mult cinci minute, fără rescriere la fiecare citire API.
            jdbc.update("UPDATE user_sessions SET last_seen_at = ? WHERE id = ? AND last_seen_at < ?",
                    Timestamp.from(now), claims.sessionId(), Timestamp.from(now.minus(Duration.ofMinutes(5))));
        }
        return valid;
    }

    @Transactional(readOnly = true)
    public List<SessionView> list(String userId, String currentId) {
        return jdbc.query("""
                SELECT s.* FROM user_sessions s JOIN users u ON u.id = s.user_id
                WHERE s.user_id = ? AND s.token_version = u.token_version AND s.expires_at > ?
                ORDER BY s.created_at DESC, s.id DESC
                """, (rs, row) -> new SessionView(rs.getString("id"), rs.getString("device"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("last_seen_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(), rs.getString("id").equals(currentId)),
                userId, Timestamp.from(Instant.now()));
    }

    @Transactional
    public void revoke(String userId, String sessionId) {
        wallet.lock(userId);
        if (jdbc.update("DELETE FROM user_sessions WHERE id = ? AND user_id = ?", sessionId, userId) == 0) {
            throw ApiException.notFound("Sesiunea nu a fost găsită sau a fost deja închisă.");
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAll(String userId) {
        jdbc.update("DELETE FROM user_sessions WHERE user_id = ?", userId);
    }

    @Scheduled(cron = "0 45 3 * * *")
    @Transactional
    public void purgeExpired() {
        jdbc.update("DELETE FROM user_sessions WHERE expires_at <= ?", Timestamp.from(Instant.now()));
    }

    private static String device(String raw) {
        if (raw == null || raw.isBlank()) return "Dispozitiv necunoscut";
        String ua = raw.substring(0, Math.min(raw.length(), 512));
        String browser = ua.contains("Edg/") ? "Edge" : ua.contains("OPR/") ? "Opera"
                : ua.contains("Firefox/") || ua.contains("FxiOS/") ? "Firefox"
                : ua.contains("Chrome/") || ua.contains("CriOS/") ? "Chrome"
                : ua.contains("Safari/") ? "Safari" : "Browser necunoscut";
        String os = ua.contains("Android") ? "Android" : ua.contains("iPhone") || ua.contains("iPad") ? "iOS"
                : ua.contains("Windows") ? "Windows" : ua.contains("Macintosh") ? "macOS"
                : ua.contains("Linux") ? "Linux" : "sistem necunoscut";
        return browser + " · " + os;
    }
}
