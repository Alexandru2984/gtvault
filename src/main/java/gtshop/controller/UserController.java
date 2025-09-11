package gtshop.controller;

import gtshop.model.UserProfile;
import gtshop.service.UserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final UserService userService;
    public UserController(UserService userService) { this.userService = userService; }

    @GetMapping("/profile")
    public UserProfile getProfile() { return userService.getUser(); }

    @GetMapping("/history")
    public java.util.Map<String,Object> getHistory() {
        return java.util.Map.of("purchases", userService.getHistory());
    }

    // >>> Asta e ruta nouă <<<
    @GetMapping("/history/summary")
    public Object getHistorySummary() {
        return userService.getHistorySummary();
    }

    // Endpoint pentru setarea punctelor - rezolvă problema principală!
    @PostMapping("/profile")
    @CrossOrigin(origins = "*")
    public java.util.Map<String, Object> updateProfile(@RequestBody java.util.Map<String, Object> request) {
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        
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
            
            boolean updated = userService.updateUserPoints(userId, activityPoints);
            
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
}
