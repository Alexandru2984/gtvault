package gtshop.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limiteaza numarul de cereri pentru un endpoint, per IP sau per utilizator autentificat.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {
    /** Numele "galetii" (endpoint-uri cu acelasi nume impart limita). */
    String name();
    int limit();
    int windowSeconds();
    Scope scope() default Scope.USER_OR_IP;

    enum Scope { IP, USER_OR_IP }
}
