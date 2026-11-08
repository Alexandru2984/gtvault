package gtshop;

import gtshop.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class GtShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(GtShopApplication.class, args);
    }
}
