package gtshop.security;

import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import java.util.Collections;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        UserProfile user = this.userRepository.findByEmail(usernameOrEmail)
                .or(() -> this.userRepository.findById(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + usernameOrEmail));
        return new User(user.getId(), user.getPassword() != null ? user.getPassword() : "", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
