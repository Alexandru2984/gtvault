package gtshop.service;

import gtshop.model.UserProfile;
import java.util.Map;

public interface TierService {
    Map<String, Object> calculateTierAndLevel(UserProfile user);
}
