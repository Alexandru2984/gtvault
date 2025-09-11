package gtshop.service;

import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
   
   // Pentru simplitate, vom stoca punctele într-o variabilă statică
   // În producție, aceasta ar trebui să fie într-o bază de date
   private static int currentUserPoints = 1000; // Puncte default
   
   @Override
   public void setUserPoints(int points) {
      currentUserPoints = points;
      System.out.println("Punctele utilizatorului au fost setate la: " + points);
   }
   
   @Override
   public int getUserPoints() {
      return currentUserPoints;
   }
}
