package gtshop;

import gtshop.model.UserProfile;
import gtshop.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class GtShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(GtShopApplication.class, args);
    }

    // Inițializare user cu puncte default
    @Bean
    public CommandLineRunner initData(UserService userService) {
        return args -> {
            UserProfile defaultUser = new UserProfile(
                "user-123", 
                "Popescu Ion", 
                "https://example.com/avatar.png", 
                2056
            );
            userService.createUser(defaultUser);
            System.out.println("✅ User inițializat cu 2056 puncte");
        };
    }
}
