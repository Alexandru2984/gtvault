package gtshop.controller;

import gtshop.dto.AuthRequest;
import gtshop.dto.AuthResponse;
import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.security.JwtTokenProvider;
import gtshop.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "User registration and login endpoints")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final TransactionService transactionService;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider tokenProvider,
                          TransactionService transactionService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.transactionService = transactionService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        if (this.userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body(AuthResponse.error("Email already exists"));
        }
        UserProfile user = new UserProfile();
        user.setId("user-" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(request.getEmail());
        user.setPassword(this.passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName() != null ? request.getName() : "New User");
        user.setAvatar("https://example.com/avatar.png");

        String userRefCode = "GT-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        user.setReferralCode(userRefCode);

        int startingPoints = 1000;
        UserProfile referrer = null;
        if (request.getReferralCode() != null && !request.getReferralCode().trim().isEmpty()) {
            referrer = this.userRepository.findByReferralCode(request.getReferralCode().trim()).orElse(null);
            if (referrer != null) {
                user.setReferredBy(referrer.getReferralCode());
                startingPoints += 200; // Extra 200 AP for using referral
            }
        }

        user.setActivityPoints(startingPoints);
        user.setTotalPointsEarned(startingPoints);
        this.userRepository.save(user);

        // Record welcome transaction
        this.transactionService.recordTransaction(user, "WELCOME_BONUS", startingPoints, user.getActivityPoints(),
            referrer != null ? "Bonus de bun venit (+200 AP recomandare de la " + referrer.getName() + ")" : "Bonus de bun venit RewardHub");

        // Reward referrer if applicable
        if (referrer != null) {
            int newReferrerBalance = referrer.getActivityPoints() + 300;
            referrer.setActivityPoints(newReferrerBalance);
            this.userRepository.save(referrer);
            this.transactionService.recordTransaction(referrer, "REFERRAL_BONUS", 300, newReferrerBalance,
                "Bonus recomandare prieten: " + user.getName());
        }

        String token = this.tokenProvider.generateToken(user.getId());
        return ResponseEntity.ok(AuthResponse.success(token, user.getId(), user.getName(), user.getActivityPoints()));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        try {
            UserProfile user = this.userRepository.findByEmail(request.getEmail()).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body(AuthResponse.error("Invalid email or password"));
            }
            Authentication authentication = this.authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(user.getId(), request.getPassword()));
            String token = this.tokenProvider.generateToken(authentication);
            return ResponseEntity.ok(AuthResponse.success(token, user.getId(), user.getName(), user.getActivityPoints()));
        } catch (AuthenticationException e) {
            return ResponseEntity.badRequest().body(AuthResponse.error("Invalid email or password"));
        }
    }
}
