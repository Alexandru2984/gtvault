package gtshop.service;

import gtshop.model.UserProfile;
import gtshop.model.UserQuest;
import gtshop.repository.UserQuestRepository;
import gtshop.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class QuestServiceImpl implements QuestService {

    private final UserQuestRepository questRepository;
    private final UserRepository userRepository;
    private final TransactionService transactionService;

    public QuestServiceImpl(UserQuestRepository questRepository,
                            UserRepository userRepository,
                            TransactionService transactionService) {
        this.questRepository = questRepository;
        this.userRepository = userRepository;
        this.transactionService = transactionService;
    }

    private String getTodayString() {
        return LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    @Override
    public List<UserQuest> getDailyQuests(UserProfile user) {
        if (user == null) {
            return List.of();
        }

        String today = getTodayString();
        List<UserQuest> existing = questRepository.findByUserAndQuestDate(user, today);
        if (!existing.isEmpty()) {
            return existing;
        }

        // Initialize daily quest pool for today
        List<UserQuest> initialized = new ArrayList<>();

        boolean alreadyClaimedDaily = today.equals(user.getLastDailyClaimDate());
        boolean alreadyAnsweredQuiz = today.equals(user.getLastQuizDate());

        initialized.add(new UserQuest(
            "quest-" + UUID.randomUUID().toString().substring(0, 8),
            user,
            "DAILY_LOGIN",
            "DAILY",
            "⚡ Conectare Zilnică",
            "Revendică bonusul de streak zilnic în RewardHub",
            30,
            alreadyClaimedDaily ? 1 : 0,
            1,
            alreadyClaimedDaily,
            false,
            today,
            Instant.now()
        ));

        initialized.add(new UserQuest(
            "quest-" + UUID.randomUUID().toString().substring(0, 8),
            user,
            "SOLVE_QUIZ",
            "DAILY",
            "🧠 Minte de Inginer",
            "Participă la provocarea tehnică a zilei",
            40,
            alreadyAnsweredQuiz ? 1 : 0,
            1,
            alreadyAnsweredQuiz,
            false,
            today,
            Instant.now()
        ));

        initialized.add(new UserQuest(
            "quest-" + UUID.randomUUID().toString().substring(0, 8),
            user,
            "SPIN_WHEEL",
            "DAILY",
            "🎡 Rotește și Câștigă",
            "Învârte Roata Norocului măcar o dată astăzi",
            35,
            0,
            1,
            false,
            false,
            today,
            Instant.now()
        ));

        initialized.add(new UserQuest(
            "quest-" + UUID.randomUUID().toString().substring(0, 8),
            user,
            "FAVORITE_ITEM",
            "DAILY",
            "💖 Explorator de Premii",
            "Salvează cel puțin o recompensă nouă la Favorite",
            25,
            0,
            1,
            false,
            false,
            today,
            Instant.now()
        ));

        initialized.add(new UserQuest(
            "quest-" + UUID.randomUUID().toString().substring(0, 8),
            user,
            "OPEN_CRATE",
            "DAILY",
            "🎁 Vânător de Cufere",
            "Deschide un Cufăr Misterios (Mystery Crate) pentru premii rare",
            50,
            0,
            1,
            false,
            false,
            today,
            Instant.now()
        ));

        return questRepository.saveAll(initialized);
    }

    @Override
    public void trackAction(UserProfile user, String actionKey, int amount) {
        if (user == null || actionKey == null) {
            return;
        }

        String today = getTodayString();
        // Ensure quests are loaded/created
        List<UserQuest> quests = questRepository.findByUserAndQuestDate(user, today);
        if (quests.isEmpty()) {
            quests = getDailyQuests(user);
        }

        for (UserQuest q : quests) {
            if (actionKey.equalsIgnoreCase(q.getQuestKey()) && !q.isCompleted()) {
                int newProg = Math.min(q.getTargetProgress(), q.getCurrentProgress() + amount);
                q.setCurrentProgress(newProg);
                if (newProg >= q.getTargetProgress()) {
                    q.setCompleted(true);
                }
                questRepository.save(q);
            }
        }
    }

    @Override
    public UserQuest claimQuest(UserProfile user, String questId) {
        if (user == null) {
            throw new IllegalArgumentException("Utilizatorul nu este autentificat.");
        }

        UserQuest quest = questRepository.findByIdAndUser(questId, user)
            .orElseThrow(() -> new IllegalArgumentException("Misiunea cerută nu a fost găsită."));

        if (!quest.isCompleted()) {
            throw new IllegalStateException("Misiunea nu a fost încă finalizată!");
        }

        if (quest.isClaimed()) {
            throw new IllegalStateException("Recompensa a fost deja revendicată!");
        }

        quest.setClaimed(true);
        questRepository.save(quest);

        int reward = quest.getRewardPoints();
        user.setActivityPoints(user.getActivityPoints() + reward);
        userRepository.save(user);

        transactionService.recordTransaction(
            user,
            "QUEST_REWARD",
            reward,
            user.getActivityPoints(),
            "Misiune îndeplinită: " + quest.getTitle()
        );

        return quest;
    }
}
