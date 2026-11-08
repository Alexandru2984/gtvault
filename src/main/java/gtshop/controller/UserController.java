package gtshop.controller;

import gtshop.dto.UserSummary;
import gtshop.model.HistorySummary;
import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.util.AvatarCatalog;
import gtshop.util.InputSanitizer;
import gtshop.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User", description = "Profilul utilizatorului autentificat")
public class UserController {
    private final gtshop.service.UserService userService;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public UserController(gtshop.service.UserService userService, UserRepository userRepository, CurrentUser currentUser) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    public record ProfileUpdate(@Size(max = 80) String name, @Size(max = 120) String avatar) {}

    @GetMapping({"/me", "/profile"})
    @Operation(summary = "Profilul privat al utilizatorului autentificat")
    public UserSummary me() {
        return UserSummary.of(userService.getCurrentUser());
    }

    @PostMapping({"/me", "/profile"})
    @Operation(summary = "Actualizează numele și/sau avatarul")
    @RateLimit(name = "profile-update", limit = 20, windowSeconds = 3600)
    @Transactional
    public Map<String, Object> updateProfile(@Valid @RequestBody ProfileUpdate request) {
        UserProfile user = userRepository.findByIdForUpdate(currentUser.requireId())
                .orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        if (request.name() != null) {
            user.setName(InputSanitizer.cleanName(request.name()));
        }
        if (request.avatar() != null) {
            if (!AvatarCatalog.isAllowed(request.avatar())) {
                throw ApiException.badRequest("Avatarul ales nu este disponibil.");
            }
            user.setAvatar(request.avatar());
        }
        userRepository.save(user);
        return Map.of("success", true, "message", "Profil actualizat cu succes.", "user", UserSummary.of(user));
    }

    @PostMapping("/avatar")
    @Operation(summary = "Schimbă avatarul (doar din galeria predefinită)")
    @RateLimit(name = "profile-update", limit = 20, windowSeconds = 3600)
    @Transactional
    public Map<String, Object> updateAvatar(@RequestBody Map<String, String> request) {
        return updateProfile(new ProfileUpdate(null, request.get("avatar")));
    }

    @GetMapping("/avatars")
    @Operation(summary = "Lista avatarelor disponibile")
    public Map<String, Object> avatars() {
        return Map.of("avatars", AvatarCatalog.paths(), "default", AvatarCatalog.DEFAULT);
    }

    @GetMapping("/history/summary")
    @Operation(summary = "Rezumatul istoricului de comenzi")
    public HistorySummary getHistorySummary() {
        return userService.getHistorySummary();
    }
}
