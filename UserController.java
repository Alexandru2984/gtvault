package gtshop.controller;

import gtshop.service.UserService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/user"})
public class UserController {
   private final UserService userService;

   public UserController(UserService userService) {
      this.userService = userService;
   }

   @PostMapping({"/set-points"})
   public Map<String, Object> setPoints(@RequestBody SetPointsRequest request) {
      try {
         // Validare
         if (request.getPoints() < 0 || request.getPoints() > 999999) {
            Map<String, Object> errorResp = new HashMap<>();
            errorResp.put("success", false);
            errorResp.put("message", "Numărul de puncte trebuie să fie între 0 și 999,999");
            return errorResp;
         }

         // Setare puncte prin service
         this.userService.setUserPoints(request.getPoints());
         
         Map<String, Object> resp = new HashMap<>();
         resp.put("success", true);
         resp.put("message", "Punctele au fost setate cu succes!");
         resp.put("newPoints", request.getPoints());
         return resp;
         
      } catch (Exception e) {
         Map<String, Object> errorResp = new HashMap<>();
         errorResp.put("success", false);
         errorResp.put("message", "Eroare la setarea punctelor: " + e.getMessage());
         return errorResp;
      }
   }

   // Clasa pentru request
   public static class SetPointsRequest {
      private int points;

      public int getPoints() {
         return points;
      }

      public void setPoints(int points) {
         this.points = points;
      }
   }
}
