package gtshop.controller;

import gtshop.model.PointTransaction;
import gtshop.model.UserProfile;
import gtshop.repository.UserRepository;
import gtshop.service.TierService;
import gtshop.service.TransactionService;
import gtshop.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Tag(name = "Transactions & Tier", description = "Point ledger, leveling, and referrals")
public class TransactionController {

    private final UserService userService;
    private final TransactionService transactionService;
    private final TierService tierService;
    private final UserRepository userRepository;

    public TransactionController(UserService userService,
                                 TransactionService transactionService,
                                 TierService tierService,
                                 UserRepository userRepository) {
        this.userService = userService;
        this.transactionService = transactionService;
        this.tierService = tierService;
        this.userRepository = userRepository;
    }

    @GetMapping("/transactions")
    @Operation(summary = "Get current user point transactions ledger")
    public List<PointTransaction> getTransactions() {
        UserProfile user = userService.getCurrentUser();
        return transactionService.getRecentTransactions(user);
    }

    @GetMapping("/tier")
    @Operation(summary = "Get user gamification level, tier, and perks")
    public Map<String, Object> getUserTier() {
        UserProfile user = userService.getCurrentUser();
        return tierService.calculateTierAndLevel(user);
    }

    @GetMapping("/referrals")
    @Operation(summary = "Get user referral info and invited friends")
    public Map<String, Object> getReferralInfo() {
        UserProfile user = userService.getCurrentUser();
        Map<String, Object> res = new HashMap<>();

        if (user == null) {
            res.put("referralCode", "");
            res.put("invitedCount", 0);
            res.put("earnedPoints", 0);
            return res;
        }

        String code = user.getReferralCode();
        if (code == null || code.isBlank()) {
            code = "GT-" + user.getId().replace("user-", "").toUpperCase();
            user.setReferralCode(code);
            userRepository.save(user);
        }

        int count = userRepository.countByReferredBy(code);
        res.put("referralCode", code);
        res.put("referralLink", "https://shop.micutu.com/?ref=" + code);
        res.put("invitedCount", count);
        res.put("earnedPoints", count * 300);
        return res;
    }
}
