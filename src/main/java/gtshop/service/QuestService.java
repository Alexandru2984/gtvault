package gtshop.service;

import gtshop.model.UserProfile;
import gtshop.model.UserQuest;

import java.util.List;

public interface QuestService {
    List<UserQuest> getDailyQuests(UserProfile user);
    void trackAction(UserProfile user, String actionKey, int amount);
    UserQuest claimQuest(UserProfile user, String questId);
}
