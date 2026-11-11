package gtshop.controller;

import gtshop.dto.AuthRequest;
import gtshop.dto.AuthResponse;
import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.AuthService;
import gtshop.service.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Înregistrare, autentificare și gestionarea sesiunilor")
public class AuthController {
    private final AuthService authService;
    private final CurrentUser currentUser;
    private final SessionService sessions;

    public AuthController(AuthService authService, CurrentUser currentUser, SessionService sessions) {
        this.authService = authService;
        this.currentUser = currentUser;
        this.sessions = sessions;
    }

    public record PasswordChangeRequest(
            @NotBlank(message = "Parola curentă este obligatorie.") @Size(max = 128) String currentPassword,
            @NotBlank(message = "Parola nouă este obligatorie.") @Size(max = 128) String newPassword) {}

    @PostMapping("/register")
    @Operation(summary = "Creează un cont nou (+1000 AP bonus de bun venit)")
    @RateLimit(name = "register-burst", limit = 3, windowSeconds = 600, scope = RateLimit.Scope.IP)
    @RateLimit(name = "register-daily", limit = 10, windowSeconds = 86_400, scope = RateLimit.Scope.IP)
    public AuthResponse register(@Valid @RequestBody AuthRequest request, HttpServletRequest http) {
        return authService.register(request.getEmail(), request.getPassword(), request.getName(), request.getReferralCode(), http.getHeader("User-Agent"));
    }

    @PostMapping("/login")
    @Operation(summary = "Autentificare cu email și parolă")
    @RateLimit(name = "login", limit = 10, windowSeconds = 300, scope = RateLimit.Scope.IP)
    public AuthResponse login(@Valid @RequestBody AuthRequest request, HttpServletRequest http) {
        return authService.login(request.getEmail(), request.getPassword(), http.getHeader("User-Agent"));
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Deconectează toate sesiunile (invalidează token-urile emise anterior)")
    @RateLimit(name = "logout-all", limit = 5, windowSeconds = 3600)
    public AuthResponse logoutAll(HttpServletRequest http) {
        return authService.logoutEverywhere(currentUser.requireId(), http.getHeader("User-Agent"));
    }

    @PostMapping("/password")
    @Operation(summary = "Schimbă parola contului")
    @RateLimit(name = "password-change", limit = 5, windowSeconds = 900)
    public AuthResponse changePassword(@Valid @RequestBody PasswordChangeRequest request, HttpServletRequest http) {
        return authService.changePassword(currentUser.requireId(), request.currentPassword(), request.newPassword(), http.getHeader("User-Agent"));
    }

    @GetMapping("/sessions")
    @Operation(summary = "Sesiunile mele active, cu dispozitivul și ultima utilizare")
    public Map<String, Object> sessions() {
        var user = currentUser.require();
        return Map.of("items", sessions.list(user.id(), user.sessionId()), "max", SessionService.MAX_SESSIONS);
    }

    @DeleteMapping("/sessions/{id}")
    @RateLimit(name = "session-revoke", limit = 60, windowSeconds = 3600)
    @Operation(summary = "Revocă o singură sesiune proprie")
    public Map<String, Object> revoke(@PathVariable String id) {
        var user = currentUser.require();
        sessions.revoke(user.id(), id);
        return Map.of("success", true);
    }

    @PostMapping("/logout")
    @Operation(summary = "Revocă sesiunea curentă pe server")
    public Map<String, Object> logout() {
        var user = currentUser.require();
        sessions.revoke(user.id(), user.sessionId());
        return Map.of("success", true);
    }
}
