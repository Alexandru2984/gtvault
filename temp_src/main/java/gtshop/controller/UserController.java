package gtshop.controller;

import gtshop.model.UserProfile;
import gtshop.service.UserService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public UserProfile getProfile() {
        return userService.getUser();
    }

    @PostMapping("/profile")
    public Map<String, Object> updateProfile(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String userId = (String) request.get("userId");
            Object pointsObj = request.get("activityPoints");
            
            if (userId == null || pointsObj == null) {
                response.put("success", false);
                response.put("message", "userId și activityPoints sunt obligatorii");
                return response;
            }
            
            // Handle both Integer and String input for points
            int activityPoints;
            if (pointsObj instanceof Integer) {
                activityPoints = (Integer) pointsObj;
            } else if (pointsObj instanceof String) {
                activityPoints = Integer.parseInt((String) pointsObj);
            } else {
                response.put("success", false);
                response.put("message", "activityPoints trebuie să fie un număr");
                return response;
            }
            
            // For simplicity, update the user directly (in a real app, you'd call a service)
            UserProfile user = userService.getUser();
            if (user != null && user.getId().equals(userId)) {
                user.setActivityPoints(activityPoints);
                response.put("success", true);
                response.put("message", "Punctele au fost actualizate cu succes");
                response.put("newBalance", activityPoints);
            } else {
                response.put("success", false);
                response.put("message", "Utilizator nu a fost găsit");
            }
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Eroare internă: " + e.getMessage());
        }
        
        return response;
    }

    @GetMapping("/history")
    public Map<String, Object> getHistory() {
        return Map.of("purchases", userService.getHistory());
    }

    @GetMapping("/history/summary")
    public Object getHistorySummary() {
        return userService.getHistorySummary();
    }
}
