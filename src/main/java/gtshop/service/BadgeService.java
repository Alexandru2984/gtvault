package gtshop.service;

import gtshop.model.UserBadge;
import gtshop.model.UserProfile;
import java.util.List;

public interface BadgeService {
    List<UserBadge> getUserBadges(String userId);
    boolean unlockBadge(UserProfile user, String badgeCode, String title, String description, String icon);
    void checkOrderBadges(UserProfile user, int totalSpent);
    void checkStreakBadges(UserProfile user, int streakDays);
    void checkQuizBadge(UserProfile user);
    void checkWheelBadge(UserProfile user);
}
