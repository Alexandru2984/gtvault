package gtshop.controller;

import gtshop.dto.AuthRequest;
import gtshop.dto.AuthResponse;
import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    public AuthController(AuthService authService, CurrentUser currentUser) {
        this.authService = authService;
        this.currentUser = currentUser;
    }

    public record PasswordChangeRequest(
            @NotBlank(message = "Parola curentă este obligatorie.") @Size(max = 128) String currentPassword,
            @NotBlank(message = "Parola nouă este obligatorie.") @Size(max = 128) String newPassword) {}

    @PostMapping("/register")
    @Operation(summary = "Creează un cont nou (+1000 AP bonus de bun venit)")
    @RateLimit(name = "register-burst", limit = 3, windowSeconds = 600, scope = RateLimit.Scope.IP)
    @RateLimit(name = "register-daily", limit = 10, windowSeconds = 86_400, scope = RateLimit.Scope.IP)
    public AuthResponse register(@Valid @RequestBody AuthRequest request) {
        return authService.register(request.getEmail(), request.getPassword(), request.getName(), request.getReferralCode());
    }

    @PostMapping("/login")
    @Operation(summary = "Autentificare cu email și parolă")
    @RateLimit(name = "login", limit = 10, windowSeconds = 300, scope = RateLimit.Scope.IP)
    public AuthResponse login(@Valid @RequestBody AuthRequest request) {
        return authService.login(request.getEmail(), request.getPassword());
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Deconectează toate sesiunile (invalidează token-urile emise anterior)")
    @RateLimit(name = "logout-all", limit = 5, windowSeconds = 3600)
    public AuthResponse logoutAll() {
        return authService.logoutEverywhere(currentUser.requireId());
    }

    @PostMapping("/password")
    @Operation(summary = "Schimbă parola contului")
    @RateLimit(name = "password-change", limit = 5, windowSeconds = 900)
    public AuthResponse changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        return authService.changePassword(currentUser.requireId(), request.currentPassword(), request.newPassword());
    }
}
