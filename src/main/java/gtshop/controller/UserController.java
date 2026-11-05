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
        UserProfile user;
        if (userId != null && !userId.isEmpty() && (user = this.userService.getUserById(userId)) != null) {
            return user;
        }
        return this.userService.getUser();
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
    @Operation(summary = "Update user points")
    public Map<String, Object> updateProfile(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            int activityPoints;
            String userId = (String) request.get("userId");
            Object pointsObj = request.get("activityPoints");
            if (userId == null || pointsObj == null) {
                response.put("success", false);
                response.put("message", "userId și activityPoints sunt obligatorii");
                return response;
            }
            if (pointsObj instanceof Integer) {
                activityPoints = (Integer) pointsObj;
            } else if (pointsObj instanceof Number) {
                activityPoints = ((Number) pointsObj).intValue();
            } else if (pointsObj instanceof String) {
                activityPoints = Integer.parseInt((String) pointsObj);
            } else {
                response.put("success", false);
                response.put("message", "activityPoints trebuie să fie un număr");
                return response;
            }
            boolean updated = this.userService.updateUserPoints(userId, activityPoints);
            if (updated) {
                response.put("success", true);
                response.put("message", "Punctele au fost actualizate cu succes");
                response.put("newBalance", activityPoints);
            } else {
                response.put("success", false);
                response.put("message", "Nu s-au putut actualiza punctele");
            }
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
            UserProfile user = this.userService.getUser();
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
