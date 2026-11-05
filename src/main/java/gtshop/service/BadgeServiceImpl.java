package gtshop.service;

import gtshop.model.UserBadge;
import gtshop.model.UserProfile;
import gtshop.repository.UserBadgeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class BadgeServiceImpl implements BadgeService {
    private final UserBadgeRepository badgeRepository;

    public BadgeServiceImpl(UserBadgeRepository badgeRepository) {
        this.badgeRepository = badgeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserBadge> getUserBadges(String userId) {
        return this.badgeRepository.findByUserIdOrderByUnlockedAtDesc(userId);
    }

    @Override
    public boolean unlockBadge(UserProfile user, String badgeCode, String title, String description, String icon) {
        if (user == null || badgeCode == null) return false;
        if (this.badgeRepository.existsByUserIdAndBadgeCode(user.getId(), badgeCode)) {
            return false; // already unlocked
        }
        UserBadge badge = new UserBadge("badge-" + UUID.randomUUID().toString().substring(0, 8), user, badgeCode, title, description, icon);
        this.badgeRepository.save(badge);
        return true;
    }

    @Override
    public void checkOrderBadges(UserProfile user, int totalSpent) {
        unlockBadge(user, "FIRST_ORDER", "Primul Pas 🛍️", "Ai finalizat cu succes prima ta comandă din RewardHub!", "🛍️");
        if (totalSpent >= 1000) {
            unlockBadge(user, "BIG_SPENDER", "Tech VIP 💎", "Ai investit peste 1000 AP într-o singură comandă!", "💎");
        }
    }

    @Override
    public void checkStreakBadges(UserProfile user, int streakDays) {
        if (streakDays >= 3) {
            unlockBadge(user, "STREAK_3", "Hot Streak 🔥", "Ai atins un streak de minim 3 zile active consecutive!", "🔥");
        }
        if (streakDays >= 7) {
            unlockBadge(user, "STREAK_7", "Generația Legend 👑", "Erou al comunității: 7 zile consecutive de prezență activă!", "👑");
        }
    }

    @Override
    public void checkQuizBadge(UserProfile user) {
        unlockBadge(user, "TECH_BRAIN", "Tech Brain 🧠", "Ai răspuns corect la provocarea tehnică zilnică!", "🧠");
    }

    @Override
    public void checkWheelBadge(UserProfile user) {
        unlockBadge(user, "LUCKY_SPINNER", "Lucky Spinner 🎡", "Ți-ai testat șansele la Roata Norocului!", "🎡");
    }
}
