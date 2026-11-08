package gtshop.service;

import gtshop.config.AppProperties;
import gtshop.dto.AuthResponse;
import gtshop.dto.UserSummary;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.security.JwtTokenProvider;
import gtshop.util.AvatarCatalog;
import gtshop.util.Ids;
import gtshop.util.InputSanitizer;
import gtshop.web.ApiException;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public static final int WELCOME_BONUS = 1000;
    public static final int REFERRAL_NEW_USER_BONUS = 200;
    public static final int REFERRAL_REFERRER_BONUS = 300;
    /** Peste aceasta limita, recomandarile nu mai aduc puncte celui care invita (anti-sybil). */
    public static final int MAX_REWARDED_REFERRALS = 20;
    public static final int MIN_PASSWORD = 8;
    public static final int MAX_PASSWORD = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final WalletService wallet;
    private final AppProperties props;
    private final NotificationService notifications;
    /** Hash folosit cand email-ul nu exista, ca timpul de raspuns sa nu dezvaluie conturile. */
    private final String dummyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider,
                       WalletService wallet, AppProperties props, NotificationService notifications) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.wallet = wallet;
        this.props = props;
        this.notifications = notifications;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-" + Ids.code(8));
    }

    public static void validateNewPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("Parola trebuie să aibă cel puțin " + MIN_PASSWORD + " caractere.");
        }
        if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PASSWORD) {
            throw ApiException.badRequest("Parola poate avea cel mult " + MAX_PASSWORD + " de caractere.");
        }
        if (password.isBlank() || password.chars().distinct().count() < 4) {
            throw ApiException.badRequest("Parola este prea simplă. Folosește o combinație mai variată de caractere.");
        }
    }

    @Transactional
    public AuthResponse register(String rawEmail, String password, String rawName, String rawReferral) {
        String email = InputSanitizer.normalizeEmail(rawEmail);
        String name = InputSanitizer.cleanName(rawName);
        validateNewPassword(password);

        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_TAKEN", "Există deja un cont cu acest email. Încearcă să te autentifici.");
        }

        UserProfile referrer = null;
        if (rawReferral != null && !rawReferral.isBlank()) {
            String code = rawReferral.trim().toUpperCase(Locale.ROOT);
            referrer = userRepository.findByReferralCode(code).orElse(null);
            if (referrer == null) {
                throw ApiException.badRequest("INVALID_REFERRAL", "Codul de recomandare nu există. Verifică-l sau lasă câmpul gol.");
            }
        }

        UserProfile user = new UserProfile();
        user.setId(Ids.prefixed("user-"));
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setName(name);
        user.setAvatar(AvatarCatalog.DEFAULT);
        user.setReferralCode(uniqueReferralCode());
        user.setRole(props.isAdminEmail(email) ? UserProfile.ROLE_ADMIN : UserProfile.ROLE_USER);
        user.setActivityPoints(0);
        if (referrer != null) {
            user.setReferredBy(referrer.getReferralCode());
        }
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("EMAIL_TAKEN", "Există deja un cont cu acest email.");
        }

        UserProfile locked = wallet.lock(user.getId());
        wallet.credit(locked, WELCOME_BONUS, TxType.WELCOME_BONUS, "Bonus de bun venit RewardHub");
        if (referrer != null) {
            wallet.credit(locked, REFERRAL_NEW_USER_BONUS, TxType.REFERRAL_BONUS,
                    "Bonus recomandare (cod " + referrer.getReferralCode() + ")");
            rewardReferrer(referrer.getId(), name);
        }

        var issued = tokenProvider.generateToken(locked.getId(), locked.getTokenVersion());
        return AuthResponse.ok("Cont creat cu succes!", issued.token(), issued.expiresAt(), UserSummary.of(locked));
    }

    private void rewardReferrer(String referrerId, String newUserName) {
        UserProfile referrer = wallet.lock(referrerId);
        int alreadyInvited = userRepository.countByReferredBy(referrer.getReferralCode());
        // countByReferredBy include deja noul cont (salvat cu flush)
        if (alreadyInvited <= MAX_REWARDED_REFERRALS) {
            wallet.credit(referrer, REFERRAL_REFERRER_BONUS, TxType.REFERRAL_BONUS,
                    "Bonus recomandare prieten: " + newUserName);
            notifications.notify(referrer.getId(), "REFERRAL", "Prietenul tău s-a alăturat! 🤝",
                    newUserName + " s-a înscris cu codul tău. Ai primit +" + REFERRAL_REFERRER_BONUS + " AP.", "🤝", "#/profil");
        } else {
            notifications.notify(referrer.getId(), "REFERRAL", "Un nou membru prin codul tău",
                    newUserName + " s-a înscris cu codul tău (limita de recompense pentru recomandări a fost atinsă).", "🤝", "#/profil");
        }
    }

    private String uniqueReferralCode() {
        for (int i = 0; i < 10; i++) {
            String code = "GT-" + Ids.code(6);
            if (!userRepository.existsByReferralCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Nu s-a putut genera un cod de recomandare unic");
    }

    @Transactional
    public AuthResponse login(String rawEmail, String password) {
        String email;
        try {
            email = InputSanitizer.normalizeEmail(rawEmail);
        } catch (ApiException e) {
            passwordEncoder.matches(password == null ? "" : password, dummyHash);
            throw invalidCredentials();
        }
        UserProfile user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getPassword() == null || user.getPassword().isBlank()) {
            passwordEncoder.matches(password == null ? "" : password, dummyHash);
            throw invalidCredentials();
        }
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPassword())) {
            throw invalidCredentials();
        }
        if (user.isBanned()) {
            throw ApiException.forbidden("Contul este suspendat. Contactează echipa Generația Tech.");
        }
        if (props.isAdminEmail(email) && !user.isAdmin()) {
            user.setRole(UserProfile.ROLE_ADMIN);
            userRepository.save(user);
        }
        var issued = tokenProvider.generateToken(user.getId(), user.getTokenVersion());
        return AuthResponse.ok("Autentificare reușită!", issued.token(), issued.expiresAt(), UserSummary.of(user));
    }

    /** Invalideaza toate token-urile emise anterior pentru acest cont. */
    @Transactional
    public AuthResponse logoutEverywhere(String userId) {
        UserProfile user = wallet.lock(userId);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        var issued = tokenProvider.generateToken(user.getId(), user.getTokenVersion());
        return AuthResponse.ok("Toate celelalte sesiuni au fost deconectate.", issued.token(), issued.expiresAt(), UserSummary.of(user));
    }

    @Transactional
    public AuthResponse changePassword(String userId, String currentPassword, String newPassword) {
        UserProfile user = wallet.lock(userId);
        if (user.getPassword() == null || !passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WRONG_PASSWORD", "Parola curentă nu este corectă.");
        }
        validateNewPassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw ApiException.badRequest("Parola nouă trebuie să fie diferită de cea veche.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        var issued = tokenProvider.generateToken(user.getId(), user.getTokenVersion());
        return AuthResponse.ok("Parola a fost schimbată. Celelalte sesiuni au fost deconectate.",
                issued.token(), issued.expiresAt(), UserSummary.of(user));
    }

    public boolean passwordMatches(UserProfile user, String password) {
        return user.getPassword() != null && passwordEncoder.matches(password == null ? "" : password, user.getPassword());
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email sau parolă incorectă.");
    }
}
