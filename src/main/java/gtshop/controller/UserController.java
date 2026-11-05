package gtshop.controller;

import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User", description = "User profile and history endpoints")
public class UserController {
    private final UserService userService;
    private final UserRepository userRepository;

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @GetMapping("/profile")
    @Operation(summary = "Get user profile")
    public UserProfile getProfile(@RequestParam(value = "userId", required = false) String userId) {
        if (userId != null && !userId.isEmpty()) {
            UserProfile user = this.userService.getUserById(userId);
            if (user != null) {
                return user;
            }
        }
        return this.userService.getCurrentUser();
    }

    @GetMapping("/history")
    @Operation(summary = "Get purchase history")
    public Map<String, Object> getHistory() {
        return Map.of("purchases", this.userService.getHistory());
    }

    @GetMapping("/history/summary")
    @Operation(summary = "Get purchase history summary")
    public Object getHistorySummary() {
        return this.userService.getHistorySummary();
    }

    @PostMapping("/profile")
    @Operation(summary = "Update user profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            UserProfile user = this.userService.getCurrentUser();
            if (request.containsKey("name") && request.get("name") != null) {
                String name = request.get("name").toString().trim();
                if (!name.isEmpty()) {
                    user.setName(name);
                    this.userRepository.save(user);
                }
            }
            response.put("success", true);
            response.put("message", "Profil actualizat cu succes");
            response.put("newBalance", user.getActivityPoints());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Eroare internă: " + e.getMessage());
        }
        return response;
    }

    @PostMapping("/avatar")
    @Operation(summary = "Update user avatar")
    public Map<String, Object> updateAvatar(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String avatarUrl = request.get("avatar");
            if (avatarUrl == null || avatarUrl.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Avatar URL este obligatoriu");
                return response;
            }
            UserProfile user = this.userService.getCurrentUser();
            user.setAvatar(avatarUrl.trim());
            this.userRepository.save(user);
            response.put("success", true);
            response.put("message", "Avatar actualizat cu succes");
            response.put("avatar", avatarUrl);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Eroare: " + e.getMessage());
        }
        return response;
    }
}
