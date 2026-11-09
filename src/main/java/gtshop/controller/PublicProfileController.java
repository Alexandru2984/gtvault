package gtshop.controller;

import gtshop.security.CurrentUser;
import gtshop.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Profiles", description = "Profilurile publice ale membrilor")
public class PublicProfileController {
    private final ProfileService profiles;
    private final CurrentUser currentUser;

    public PublicProfileController(ProfileService profiles, CurrentUser currentUser) {
        this.profiles = profiles;
        this.currentUser = currentUser;
    }

    @GetMapping("/{id}")
    @Operation(summary = "Profilul public al unui membru (fără date private)")
    public Map<String, Object> profile(@PathVariable String id) {
        return profiles.publicProfile(id, currentUser.optionalId());
    }
}
