package gtshop.service;

import gtshop.model.UserProfile;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TierServiceImpl implements TierService {

    @Override
    public Map<String, Object> calculateTierAndLevel(UserProfile user) {
        Map<String, Object> res = new HashMap<>();
        if (user == null) {
            res.put("level", 1);
            res.put("tier", "BRONZE");
            res.put("title", "Junior Coder");
            res.put("discountPercent", 0);
            res.put("progressPercent", 0);
            res.put("nextLevelXp", 1000);
            res.put("perks", List.of("Acces standard catalog"));
            return res;
        }

        int xp = Math.max(user.getTotalPointsEarned(), user.getActivityPoints());
        int level;
        String tier;
        String title;
        int discountPercent;
        int prevThreshold;
        int nextThreshold;
        List<String> perks;

        if (xp < 1000) {
            level = 1;
            tier = "BRONZE";
            title = "Junior Coder";
            discountPercent = 0;
            prevThreshold = 0;
            nextThreshold = 1000;
            perks = List.of("Acces la premii fizice și digitale", "Quiz tehnic zilnic", "Daily streak calendar");
        } else if (xp < 2500) {
            level = 2;
            tier = "SILVER";
            title = "Tech Explorer";
            discountPercent = 3;
            prevThreshold = 1000;
            nextThreshold = 2500;
            perks = List.of("Reducere automată 3% la checkout", "Badge argintiu în profil", "Prioritate procesare comenzi");
        } else if (xp < 5000) {
            level = 3;
            tier = "GOLD";
            title = "Code Artisan";
            discountPercent = 5;
            prevThreshold = 2500;
            nextThreshold = 5000;
            perks = List.of("Reducere 5% la checkout", "Acces exclusiv la mentorat 1:1", "Badge auriu strălucitor");
        } else if (xp < 10000) {
            level = 4;
            tier = "PLATINUM";
            title = "Full-Stack Hacker";
            discountPercent = 7;
            prevThreshold = 5000;
            nextThreshold = 10000;
            perks = List.of("Reducere 7% la checkout", "Acces la toate track-urile GT", "Vouchere digitale prioritare");
        } else {
            level = 5;
            tier = "DIAMOND";
            title = "Tech Architect";
            discountPercent = 10;
            prevThreshold = 10000;
            nextThreshold = 10000;
            perks = List.of("Reducere maximă 10% la checkout", "Invitație VIP Meet & Greet", "Badge legendar Diamond");
        }

        int progressPercent;
        if (level == 5) {
            progressPercent = 100;
        } else {
            int range = nextThreshold - prevThreshold;
            int achieved = Math.max(0, xp - prevThreshold);
            progressPercent = Math.min(100, Math.round(((float) achieved / range) * 100));
        }

        res.put("level", level);
        res.put("tier", tier);
        res.put("title", title);
        res.put("discountPercent", discountPercent);
        res.put("currentXp", xp);
        res.put("prevThreshold", prevThreshold);
        res.put("nextThreshold", nextThreshold);
        res.put("progressPercent", progressPercent);
        res.put("perks", perks);
        return res;
    }
}
