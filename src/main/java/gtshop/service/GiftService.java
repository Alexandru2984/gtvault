package gtshop.service;

import gtshop.game.Badge;
import gtshop.game.Economy;
import gtshop.game.QuestCatalog.Action;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.util.AvatarCatalog;
import gtshop.util.InputSanitizer;
import gtshop.web.ApiException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transfer de AP intre membri. Destinatarul se identifica doar prin codul GT (sau id), nu prin email,
 * ca endpoint-ul sa nu poata fi folosit pentru a verifica ce adrese au cont. Limitele (vechime cont,
 * XP minim, plafon zilnic) blocheaza conturile create doar pentru a muta bonusul de bun venit.
 */
@Service
public class GiftService {
    static final String GIFT_COUNT = "GIFT_COUNT";
    static final String GIFT_AMOUNT = "GIFT_AMOUNT";

    private final UserRepository userRepository;
    private final WalletService wallet;
    private final DailyCounterService counters;
    private final BadgeService badges;
    private final QuestService quests;
    private final NotificationService notifications;
    private final TimeService time;

    public GiftService(UserRepository userRepository, WalletService wallet, DailyCounterService counters,
                       BadgeService badges, QuestService quests, NotificationService notifications, TimeService time) {
        this.userRepository = userRepository;
        this.wallet = wallet;
        this.counters = counters;
        this.badges = badges;
        this.quests = quests;
        this.notifications = notifications;
        this.time = time;
    }

    private Optional<UserProfile> resolve(String ref) {
        if (ref == null || ref.isBlank() || ref.length() > 64) {
            return Optional.empty();
        }
        String r = ref.trim();
        Optional<UserProfile> found;
        if (r.toUpperCase(Locale.ROOT).startsWith("GT-")) {
            found = userRepository.findByReferralCode(r.toUpperCase(Locale.ROOT));
        } else if (r.startsWith("user-")) {
            found = userRepository.findById(r);
        } else {
            throw ApiException.badRequest("Folosește codul GT al prietenului (de forma GT-XXXXXX).");
        }
        return found.filter(u -> !u.isBanned());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> preview(String senderId, String ref) {
        UserProfile r = resolve(ref).orElseThrow(() -> ApiException.notFound("Nu am găsit niciun membru cu acest cod."));
        if (r.getId().equals(senderId)) {
            throw ApiException.badRequest("Nu îți poți trimite un cadou ție însuți.");
        }
        return Map.of("id", r.getId(), "name", r.getName(), "avatar", AvatarCatalog.safe(r.getAvatar()),
                "guildId", r.getGuildId() == null ? "JAVA_KNIGHTS" : r.getGuildId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> limits(String senderId) {
        UserProfile s = userRepository.findById(senderId).orElseThrow(() -> ApiException.unauthorized("Contul nu mai există."));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eligible", eligibilityProblem(s) == null);
        m.put("reason", eligibilityProblem(s));
        m.put("sentToday", counters.get(senderId, GIFT_AMOUNT));
        m.put("giftsToday", counters.get(senderId, GIFT_COUNT));
        m.put("dailyAmount", Economy.GIFT_DAILY_AMOUNT);
        m.put("dailyCount", Economy.GIFT_DAILY_COUNT);
        m.put("min", Economy.GIFT_MIN);
        m.put("max", Economy.GIFT_MAX);
        return m;
    }

    private String eligibilityProblem(UserProfile s) {
        if (s.getCreatedAt() != null && s.getCreatedAt().isAfter(time.now().minus(Duration.ofDays(Economy.GIFT_MIN_ACCOUNT_AGE_DAYS)))) {
            return "Poți trimite cadouri după " + Economy.GIFT_MIN_ACCOUNT_AGE_DAYS + " zile de la crearea contului.";
        }
        if (s.getTotalPointsEarned() < Economy.GIFT_MIN_XP) {
            return "Ai nevoie de cel puțin " + Economy.GIFT_MIN_XP + " XP câștigat din activitate ca să trimiți cadouri.";
        }
        return null;
    }

    @Transactional
    public Map<String, Object> send(String senderId, String recipientRef, Integer amount, String rawMessage) {
        if (amount == null || amount < Economy.GIFT_MIN || amount > Economy.GIFT_MAX) {
            throw ApiException.badRequest("Suma trebuie să fie între " + Economy.GIFT_MIN + " și " + Economy.GIFT_MAX + " AP.");
        }
        String message = InputSanitizer.cleanOptional(rawMessage, 120, "Mesajul");
        UserProfile target = resolve(recipientRef)
                .orElseThrow(() -> ApiException.notFound("Nu am găsit niciun membru cu acest cod."));
        if (target.getId().equals(senderId)) {
            throw ApiException.badRequest("Nu îți poți trimite un cadou ție însuți.");
        }

        // Blocare in ordine fixa (dupa id) -> fara deadlock cand doi membri isi trimit cadouri simultan
        UserProfile sender;
        UserProfile recipient;
        if (senderId.compareTo(target.getId()) < 0) {
            sender = wallet.lock(senderId);
            recipient = wallet.lock(target.getId());
        } else {
            recipient = wallet.lock(target.getId());
            sender = wallet.lock(senderId);
        }
        String problem = eligibilityProblem(sender);
        if (problem != null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GIFT_NOT_ALLOWED", problem);
        }
        counters.consume(senderId, GIFT_COUNT, 1, Economy.GIFT_DAILY_COUNT,
                "Poți trimite cel mult " + Economy.GIFT_DAILY_COUNT + " cadouri pe zi.");
        int sentToday = counters.consume(senderId, GIFT_AMOUNT, amount, Economy.GIFT_DAILY_AMOUNT,
                "Plafonul zilnic pentru cadouri este de " + Economy.GIFT_DAILY_AMOUNT + " AP (azi ai trimis "
                        + counters.get(senderId, GIFT_AMOUNT) + " AP).");

        String note = message == null ? "" : " – „" + message + "”";
        wallet.debit(sender, amount, TxType.GIFT_SENT, "Cadou pentru " + recipient.getName() + note);
        wallet.credit(recipient, amount, TxType.GIFT_RECEIVED, "Cadou de la " + sender.getName() + note);
        notifications.notify(recipient.getId(), "GIFT", "Ai primit " + amount + " AP de la " + sender.getName() + " 🎁",
                message == null ? "Un gest frumos din partea comunității!" : message, "🎁", "#/istoric");
        quests.track(sender, Action.SEND_GIFT, 1);
        List<Badge> unlocked = badges.evaluate(sender, Badge.GENEROUS, Badge.PHILANTHROPIST);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("message", "Ai trimis " + amount + " AP către " + recipient.getName() + "!");
        r.put("amount", amount);
        r.put("recipientName", recipient.getName());
        r.put("newBalance", sender.getActivityPoints());
        r.put("sentToday", sentToday);
        r.put("badgesUnlocked", unlocked.stream().map(Badge::name).toList());
        return r;
    }
}
