package gtshop.security;

import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Autentifica cererea pe baza header-ului {@code Authorization: Bearer <jwt>}.
 * Token-ul este acceptat doar daca utilizatorul exista, nu este suspendat si versiunea token-ului
 * corespunde (schimbarea parolei / "deconectare peste tot" invalideaza token-urile vechi).
 * Un token invalid NU blocheaza cererea - aceasta continua ca anonima, iar regulile din
 * {@link SecurityConfig} decid accesul.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            tokenProvider.parse(header.substring(7).trim()).ifPresent(claims -> {
                UserProfile user = userRepository.findById(claims.userId()).orElse(null);
                if (user != null && !user.isBanned() && user.getTokenVersion() == claims.tokenVersion()) {
                    AuthUser principal = new AuthUser(user.getId(), user.getRole());
                    var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
                    var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            });
        }
        chain.doFilter(request, response);
    }
}
