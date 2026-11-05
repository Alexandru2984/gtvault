package gtshop.config;

import gtshop.model.Reward;
import gtshop.model.UserProfile;
import gtshop.repository.RewardRepository;
import gtshop.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer {
    private final RewardRepository rewardRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(RewardRepository rewardRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.rewardRepository = rewardRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostConstruct
    public void init() {
        if (this.userRepository.findById("user-123").isEmpty()) {
            UserProfile defaultUser = new UserProfile("user-123", "Popescu Ion", "https://example.com/avatar.png", 2056);
            defaultUser.setEmail("demo@gtshop.ro");
            String initPassword = System.getenv("DEFAULT_USER_INIT_PASSWORD");
            if (initPassword == null || initPassword.isBlank()) {
                initPassword = java.util.UUID.randomUUID().toString();
            }
            defaultUser.setPassword(this.passwordEncoder.encode(initPassword));
            this.userRepository.save(defaultUser);
            System.out.println("✅ Default user initialized with 2056 points");
        }
        if (this.rewardRepository.count() == 0L) {
            List<Reward> rewards = Arrays.asList(
                new Reward("rew-tricou", "Tricou GT", "Tricou personalizat Generația Tech din bumbac", "Tricou personalizat Generația Tech din bumbac. Mărimi: S, M, L, XL.", 550, "", "Premii fizice", true, 100, false),
                new Reward("rew-sticker", "Sticker GT", "Sticker personalizat Generația Tech", "Sticker Generația Tech. Personalizează-ți obiectele.", 180, "", "Premii fizice", true, 300, false),
                new Reward("rew-mouse", "Mouse Logitech G305", "Mouse pentru birou sau gaming", "Mouse Logitech G305, senzor HERO, autonomie ridicată.", 350, "", "Premii fizice", true, 20, false),
                new Reward("rew-agenda", "Agendă GT", "Agendă personalizată Generația Tech", "Organizează-ți ideile cu agenda GT, perfectă pentru notițe.", 300, "", "Premii fizice", true, 50, false),
                new Reward("rew-templatecv", "Template CV", "Primești acces la un CV template", "CV template editabil, design curat, secțiuni predefinite.", 900, "", "Premii digitale", true, 9999, false),
                new Reward("rew-ghidgpt", "Ghid ChatGPT", "Ghid pentru începători", "Ghid digital cu sfaturi și exemple practice.", 3000, "", "Premii digitale", true, 9999, true),
                new Reward("rew-udemy", "Subscripție Udemy 30 zile", "Acces 30 zile pe Udemy", "Acces la mii de cursuri online timp de 30 de zile.", 2500, "", "Premii digitale", true, 9999, false),
                new Reward("rew-badge", "Badge Hero", "Badge afișat pe profil", "Badge Hero afișat pe profilul tău în platforma Generația Tech.", 1500, "", "Premii digitale", true, 9999, false),
                new Reward("rew-meet", "Meet & Greet", "Meet & Greet cu echipa Generația Tech", "Eveniment exclusiv pentru a cunoaște mentori și colegi din comunitatea GT.", 1200, "", "Premii exclusive", true, 1000, false),
                new Reward("rew-mentorat", "Mentorat 1:1", "Mentorat 1:1 cu mentorul tău", "Sesiune 60 minute cu mentorul tău din track.", 1560, "", "Premii exclusive", true, 1000, false),
                new Reward("rew-lucky", "Lucky Crate", "Cu acest lucky crate îți poți testa norocul", "Șanse: 50% Lose, 15% CV Template, 15% Ghid ChatGPT, 10% Agendă GT, 5% Mouse G305, 5% Subscripție Udemy.", 2550, "", "Premii exclusive", true, 1000, true),
                new Reward("rew-alltracks", "Acces la toate track-urile", "Acces la toate track-urile pentru 30 zile", "Acces 30 zile la toate track-urile (fără teme/questuri).", 3500, "", "Premii exclusive", true, 1000, false)
            );
            this.rewardRepository.saveAll(rewards);
            System.out.println("✅ " + rewards.size() + " rewards initialized");
        }
    }
}
