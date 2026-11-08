package gtshop.dto;

import gtshop.model.UserProfile;
import gtshop.util.AvatarCatalog;
import java.time.Instant;

/** Profilul privat al utilizatorului autentificat (nu contine hash-ul parolei). */
public record UserSummary(
        String id,
        String name,
        String email,
        String avatar,
        int activityPoints,
        int totalPointsEarned,
        int streakDays,
        int longestStreak,
        int streakFreezes,
        String guildId,
        String referralCode,
        String role,
        Instant createdAt) {

    public static UserSummary of(UserProfile u) {
        return new UserSummary(u.getId(), u.getName(), u.getEmail(), AvatarCatalog.safe(u.getAvatar()),
                u.getActivityPoints(), u.getTotalPointsEarned(), u.getStreakDays(), u.getLongestStreak(),
                u.getStreakFreezes(), u.getGuildId(), u.getReferralCode(), u.getRole(), u.getCreatedAt());
    }
}
