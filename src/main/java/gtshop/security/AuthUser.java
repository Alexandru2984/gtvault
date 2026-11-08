package gtshop.security;

import java.security.Principal;

/**
 * Principalul autentificat (extras din JWT si verificat in baza de date la fiecare cerere).
 */
public record AuthUser(String id, String role) implements Principal {
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    @Override
    public String getName() {
        return id;
    }
}
