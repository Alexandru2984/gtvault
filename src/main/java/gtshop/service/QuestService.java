package gtshop.service;

import gtshop.game.Badge;
import gtshop.game.ContentBank;
import gtshop.game.QuestCatalog;
import gtshop.game.QuestCatalog.Action;
import gtshop.game.QuestCatalog.QuestDef;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.model.UserQuest;
import gtshop.repository.UserQuestRepository;
import gtshop.util.Ids;
import gtshop.web.ApiException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Misiuni zilnice si saptamanale, generate per utilizator si urmarite exclusiv pe server. */
@Service
public class QuestService {
    static final String DAILY = "DAILY";
    static final String WEEKLY = "WEEKLY";

    private final UserQuestRepository questRepository;
    private final WalletService wallet;
    private final BadgeService badges;
    private final NotificationService notifications;
    private final TimeService time;

    public QuestService(UserQuestRepository questRepository, WalletService wallet, BadgeService badges,
                        NotificationService notifications, TimeService time) {
        this.questRepository = questRepository;
        this.wallet = wallet;
        this.badges = badges;
        this.notifications = notifications;
        this.time = time;
    }

    public record QuestView(String id, String key, String type, String title, String description, int rewardPoints,
                            int currentProgress, int targetProgress, boolean completed, boolean claimed, Instant expiresAt) {}

    @Transactional
    public Map<String, Object> list(String userId) {
        UserProfile user = wallet.lock(userId);
        List<UserQuest> daily = ensure(user, DAILY);
        List<UserQuest> weekly = ensure(user, WEEKLY);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("daily", daily.stream().map(this::view).toList());
        res.put("weekly", weekly.stream().map(this::view).toList());
        res.put("dailyResetsAt", time.startOfTomorrow());
        res.put("weeklyResetsAt", time.startOfNextWeek());
        res.put("claimable", daily.stream().filter(q -> q.isCompleted() && !q.isClaimed()).count()
                + weekly.stream().filter(q -> q.isCompleted() && !q.isClaimed()).count());
        return res;
    }

    /** Avanseaza misiunile active pentru actiunea data. Utilizatorul trebuie sa fie blocat de apelant. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void track(UserProfile lockedUser, Action action, int amount) {
        if (amount <= 0) {
            return;
        }
        List<UserQuest> all = new ArrayList<>(ensure(lockedUser, DAILY));
        all.addAll(ensure(lockedUser, WEEKLY));
        for (UserQuest q : all) {
            if (!q.isCompleted() && action.name().equals(q.getQuestKey())) {
                q.setCurrentProgress(Math.min(q.getTargetProgress(), q.getCurrentProgress() + amount));
                if (q.getCurrentProgress() >= q.getTargetProgress()) {
                    q.setCompleted(true);
                    if (WEEKLY.equals(q.getQuestType())) {
                        notifications.notify(lockedUser.getId(), "QUEST", "Misiune săptămânală finalizată! 🎯",
                                q.getTitle() + " – revendică " + q.getRewardPoints() + " AP.", "🎯", "#/activitati");
                    }
                }
                questRepository.save(q);
            }
        }
    }

    @Transactional
    public Map<String, Object> claim(String userId, String questId) {
        UserProfile user = wallet.lock(userId);
        UserQuest quest = questRepository.findByIdAndUserId(questId, userId)
                .orElseThrow(() -> ApiException.notFound("Misiunea nu a fost găsită."));
        if (quest.isClaimed()) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_CLAIMED", "Recompensa a fost deja revendicată.");
        }
        if (!quest.isCompleted()) {
            throw new ApiException(HttpStatus.CONFLICT, "QUEST_NOT_COMPLETED", "Misiunea nu este încă finalizată.");
        }
        quest.setClaimed(true);
        questRepository.save(quest);
        wallet.credit(user, quest.getRewardPoints(), TxType.QUEST_REWARD, "Misiune îndeplinită: " + quest.getTitle());
        List<Badge> unlocked = badges.evaluate(user, Badge.QUEST_RUNNER, Badge.QUEST_LEGEND);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", "Ai revendicat " + quest.getRewardPoints() + " AP pentru „" + quest.getTitle() + "”!");
        res.put("rewardPoints", quest.getRewardPoints());
        res.put("newBalance", user.getActivityPoints());
        res.put("quest", view(quest));
        res.put("badgesUnlocked", unlocked.stream().map(Badge::name).toList());
        return res;
    }

    private List<UserQuest> ensure(UserProfile user, String type) {
        String period = DAILY.equals(type) ? time.todayIso() : time.weekStartIso();
        List<UserQuest> existing = questRepository.findForPeriod(user.getId(), type, period);
        if (!existing.isEmpty()) {
            return existing;
        }
        List<QuestDef> defs = new ArrayList<>();
        Random seeded = new Random(ContentBank.stableHash(user.getId() + "|" + type + "|" + period));
        if (DAILY.equals(type)) {
            defs.add(QuestCatalog.DAILY_ANCHOR);
            List<QuestDef> pool = new ArrayList<>(QuestCatalog.DAILY_POOL);
            Collections.shuffle(pool, seeded);
            defs.addAll(pool.subList(0, QuestCatalog.DAILY_EXTRA));
        } else {
            List<QuestDef> pool = new ArrayList<>(QuestCatalog.WEEKLY_POOL);
            Collections.shuffle(pool, seeded);
            defs.addAll(pool.subList(0, QuestCatalog.WEEKLY_COUNT));
        }
        Instant now = time.now();
        List<UserQuest> created = new ArrayList<>();
        for (int i = 0; i < defs.size(); i++) {
            QuestDef d = defs.get(i);
            created.add(new UserQuest(Ids.prefixed("quest-"), user, d.action().name(), type, d.title(), d.description(),
                    d.reward(), 0, d.target(), false, false, period, now.plusMillis(i)));
        }
        return questRepository.saveAll(created);
    }

    private QuestView view(UserQuest q) {
        Instant expires = DAILY.equals(q.getQuestType()) ? time.startOfTomorrow() : time.startOfNextWeek();
        return new QuestView(q.getId(), q.getQuestKey(), q.getQuestType(), q.getTitle(), q.getDescription(),
                q.getRewardPoints(), q.getCurrentProgress(), q.getTargetProgress(), q.isCompleted(), q.isClaimed(), expires);
    }
}
