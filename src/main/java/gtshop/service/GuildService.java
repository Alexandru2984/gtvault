package gtshop.service;

import gtshop.model.UserProfile;

import java.util.List;
import java.util.Map;

public interface GuildService {
    List<Map<String, Object>> getGuildsOverview();
    Map<String, Object> getUserGuild(UserProfile user);
    Map<String, Object> selectGuild(UserProfile user, String guildId);
}
