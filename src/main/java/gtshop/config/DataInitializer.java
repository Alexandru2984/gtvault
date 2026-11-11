package gtshop.config;

import gtshop.model.Reward;
import gtshop.repository.RewardRepository;
import gtshop.repository.UserRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Populeaza catalogul la prima pornire si completeaza codurile de recomandare lipsa.
 * Nu se mai creeaza niciun utilizator implicit (anterior "user-123" era folosit pentru orice vizitator anonim).
 */
@Component
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final RewardRepository rewardRepository;
    private final UserRepository userRepository;
    public DataInitializer(RewardRepository rewardRepository, UserRepository userRepository) {
        this.rewardRepository = rewardRepository;
        this.userRepository = userRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        if (rewardRepository.count() == 0L) {
            List<Reward> rewards = List.of(
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
            for (Reward r : rewards) {
                if ("Premii fizice".equals(r.getCategory())) {
                    r.setFulfillment(Reward.FULFILLMENT_PHYSICAL);
                }
                if ("rew-badge".equals(r.getId())) {
                    r.setFulfillment(Reward.FULFILLMENT_BADGE);
                    r.setMaxPerOrder(1);
                }
                // Cutia cu premii aleatorii nu este implementata; cuferele reale sunt in Activitati
                if ("rew-lucky".equals(r.getId())) {
                    r.setActive(false);
                }
            }
            rewardRepository.saveAll(rewards);
            log.info("{} rewards initialized", rewards.size());
        }

        // Conturile vechi fara cod de recomandare primesc unul (necesar pentru cadouri si invitatii)
        for (gtshop.model.UserProfile u : userRepository.findByReferralCodeIsNull()) {
            String code;
            do {
                code = "GT-" + gtshop.util.Ids.code(6);
            } while (userRepository.existsByReferralCode(code));
            u.setReferralCode(code);
            userRepository.saveAndFlush(u);
        }
    }
}
