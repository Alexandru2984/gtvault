package gtshop.security;

import gtshop.web.ApiException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acces la utilizatorul autentificat curent. NU exista fallback la un utilizator implicit.
 */
@Component
public class CurrentUser {

    public Optional<AuthUser> optional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public Optional<String> optionalId() {
        return optional().map(AuthUser::id);
    }

    public AuthUser require() {
        return optional().orElseThrow(() -> ApiException.unauthorized("Trebuie să fii autentificat."));
    }

    public String requireId() {
        return require().id();
    }
}
