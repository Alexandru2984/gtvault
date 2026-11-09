package gtshop.controller;

import gtshop.dto.UserSummary;
import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.security.CurrentUser;
import gtshop.security.RateLimit;
import gtshop.service.PrivacyService;
import gtshop.service.TimeService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
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
    private final PrivacyService privacy;
    private final TimeService time;

    public UserController(gtshop.service.UserService userService, UserRepository userRepository, CurrentUser currentUser,
                          PrivacyService privacy, TimeService time) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.privacy = privacy;
        this.time = time;
    }

    public record ProfileUpdate(@Size(max = 80) String name, @Size(max = 120) String avatar, Boolean publicProfile,
                                @Size(max = 400) String bio) {
        public ProfileUpdate(String name, String avatar) {
            this(name, avatar, null, null);
        }
    }

    public record DeleteAccountRequest(String password, String confirmation) {}

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
        if (request.publicProfile() != null) {
            user.setPublicProfile(request.publicProfile());
        }
        if (request.bio() != null) {
            String bio = InputSanitizer.cleanOptional(request.bio().replaceAll("\\s+", " "), 160, "Descrierea");
            user.setBio(bio);
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

    @GetMapping("/export")
    @Operation(summary = "Descarcă toate datele mele (GDPR, format JSON)")
    @RateLimit(name = "data-export", limit = 5, windowSeconds = 3600)
    public ResponseEntity<Map<String, Object>> export() {
        String filename = "rewardhub-datele-mele-" + time.todayIso() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(privacy.export(currentUser.requireId()));
    }

    @PostMapping("/account/delete")
    @Operation(summary = "Șterge definitiv contul și toate datele (cere parola și confirmarea „STERGE”)")
    @RateLimit(name = "account-delete", limit = 5, windowSeconds = 3600)
    public Map<String, Object> deleteAccount(@RequestBody DeleteAccountRequest request) {
        return privacy.deleteAccount(currentUser.requireId(), request.password(), request.confirmation());
    }

    @GetMapping("/avatars")
    @Operation(summary = "Lista avatarelor disponibile")
    public Map<String, Object> avatars() {
        return Map.of("avatars", AvatarCatalog.paths(), "default", AvatarCatalog.DEFAULT);
    }

}
