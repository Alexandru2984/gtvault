package gtshop.dto;

import java.time.Instant;

public record AuthResponse(boolean success, String message, String token, Instant expiresAt, UserSummary user) {
    public static AuthResponse ok(String message, String token, Instant expiresAt, UserSummary user) {
        return new AuthResponse(true, message, token, expiresAt, user);
    }
}
