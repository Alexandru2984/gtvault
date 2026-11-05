package gtshop.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI().info(new Info().title("GT Shop API").version("2.0.0").description("RewardHub API - Sistema de recompense Genera\u021bia Tech").contact(new Contact().name("Genera\u021bia Tech").url("https://generatiatech.ro")).license(new License().name("MIT License").url("https://opensource.org/licenses/MIT"))).addSecurityItem(new SecurityRequirement().addList("bearerAuth")).components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme().name("bearerAuth").type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT").description("Enter JWT token")));
    }
}

