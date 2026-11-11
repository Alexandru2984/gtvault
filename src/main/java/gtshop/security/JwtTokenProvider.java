package gtshop.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Emite si valideaza token-uri JWT semnate HMAC.
 * Secretul este obligatoriu (minim 32 octeti) - nu mai exista completare cu zerouri.
 */
@Component
public class JwtTokenProvider {
    static final String CLAIM_TOKEN_VERSION = "tv";
    private static final int MIN_SECRET_BYTES = 32;

    private final String jwtSecret;
    private final long jwtExpirationMs;
    private final String issuer;
    private SecretKey signingKey;

    public JwtTokenProvider(@Value("${jwt.secret:}") String jwtSecret,
                            @Value("${jwt.expiration:604800000}") long jwtExpirationMs,
                            @Value("${jwt.issuer:rewardhub}") String issuer) {
        this.jwtSecret = jwtSecret;
        this.jwtExpirationMs = jwtExpirationMs;
        this.issuer = issuer;
    }

    @PostConstruct
    void init() {
        byte[] keyBytes = jwtSecret == null ? new byte[0] : jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET lipseste sau este prea scurt (minim " + MIN_SECRET_BYTES + " caractere). Aplicatia nu porneste fara un secret sigur.");
        }
        if (jwtExpirationMs < 60_000) {
            throw new IllegalStateException("jwt.expiration trebuie sa fie de cel putin 60 de secunde.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public record IssuedToken(String token, Instant expiresAt, String id, Instant issuedAt) {}

    public record TokenClaims(String userId, int tokenVersion, String sessionId) {}

    public IssuedToken generateToken(String userId, int tokenVersion) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtExpirationMs);
        String id = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(id)
                .issuer(issuer)
                .subject(userId)
                .claim(CLAIM_TOKEN_VERSION, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
        return new IssuedToken(token, expiry, id, now);
    }

    public Optional<TokenClaims> parse(String token) {
        if (token == null || token.isBlank() || token.length() > 4096) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(issuer)
                    .clockSkewSeconds(30)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String subject = claims.getSubject();
            String id = claims.getId();
            if (subject == null || subject.isBlank() || id == null || !id.matches("[a-f0-9-]{36}")) {
                return Optional.empty();
            }
            Number tv = claims.get(CLAIM_TOKEN_VERSION, Number.class);
            return Optional.of(new TokenClaims(subject, tv == null ? 0 : tv.intValue(), id));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
