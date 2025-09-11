package gtshop.controller;

import gtshop.model.SetPointsRequest;
import gtshop.service.UserService;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserControllerExtended {
    
    private final UserService userService;

    public UserControllerExtended(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public Object getProfile() {
        return userService.getUser();
    }

    @GetMapping("/history")
    public Map<String, Object> getHistory() {
        Map<String, Object> response = new HashMap<>();
        response.put("purchases", userService.getHistory());
        return response;
    }

    @GetMapping("/history-summary")
    public Object getHistorySummary() {
        return userService.getHistorySummary();
    }

    @PostMapping("/set-points")
    public Map<String, Object> setPoints(@RequestBody SetPointsRequest request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Validare
            if (request.getPoints() < 0) {
                response.put("success", false);
                response.put("message", "Punctele nu pot fi negative");
                return response;
            }
            
            if (request.getPoints() > 999999) {
                response.put("success", false);
                response.put("message", "Numărul de puncte nu poate fi mai mare de 999,999");
                return response;
            }

            // Setare puncte prin service
            userService.setUserPoints(request.getPoints());
            
            // Răspuns de succes
            response.put("success", true);
            response.put("message", "Punctele au fost setate cu succes!");
            response.put("newPoints", request.getPoints());
            
            return response;
            
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("message", "Eroare de validare: " + e.getMessage());
            return response;
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Eroare la setarea punctelor: " + e.getMessage());
            return response;
        }
    }

    @GetMapping("/points")
    public Map<String, Object> getPoints() {
        Map<String, Object> response = new HashMap<>();
        try {
            int points = userService.getUserPoints();
            response.put("success", true);
            response.put("points", points);
            return response;
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Eroare la obținerea punctelor: " + e.getMessage());
            return response;
        }
    }
}
