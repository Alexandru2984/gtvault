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

    @org.springframework.web.bind.annotation.PostMapping("/gift")
    @Operation(summary = "Send AP as a gift to a community friend")
    public org.springframework.http.ResponseEntity<Map<String, Object>> giftPoints(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> body) {
        UserProfile sender = userService.getCurrentUser();
        Map<String, Object> res = new HashMap<>();

        if (sender == null) {
            res.put("success", false);
            res.put("message", "Utilizator neautentificat.");
            return org.springframework.http.ResponseEntity.status(401).body(res);
        }

        String recipientId = body.containsKey("recipient") ? body.get("recipient").toString().trim() : "";
        int amount = body.containsKey("amount") ? ((Number) body.get("amount")).intValue() : 0;
        String message = body.containsKey("message") ? body.get("message").toString().trim() : "";

        if (recipientId.isEmpty()) {
            res.put("success", false);
            res.put("message", "Te rugăm să specifici email-ul, ID-ul sau codul de recomandare al destinatarului.");
            return org.springframework.http.ResponseEntity.badRequest().body(res);
        }

        if (amount < 25) {
            res.put("success", false);
            res.put("message", "Suma minimă pe care o poți trimite cadou este de 25 AP.");
            return org.springframework.http.ResponseEntity.badRequest().body(res);
        }

        if (sender.getActivityPoints() < amount) {
            res.put("success", false);
            res.put("message", "Fonduri insuficiente! Ai doar " + sender.getActivityPoints() + " AP.");
            return org.springframework.http.ResponseEntity.badRequest().body(res);
        }

        UserProfile recipient = userRepository.findByEmail(recipientId)
            .or(() -> userRepository.findByReferralCode(recipientId.toUpperCase()))
            .or(() -> userRepository.findById(recipientId))
            .orElse(null);

        if (recipient == null) {
            res.put("success", false);
            res.put("message", "Destinatarul nu a fost găsit în comunitate (verifică email-ul sau codul GT).");
            return org.springframework.http.ResponseEntity.badRequest().body(res);
        }

        if (recipient.getId().equals(sender.getId())) {
            res.put("success", false);
            res.put("message", "Nu îți poți trimite puncte cadou ție însuți!");
            return org.springframework.http.ResponseEntity.badRequest().body(res);
        }

        // Atomically transfer points
        sender.setActivityPoints(sender.getActivityPoints() - amount);
        recipient.setActivityPoints(recipient.getActivityPoints() + amount);

        userRepository.save(sender);
        userRepository.save(recipient);

        String note = message.isEmpty() ? "Felicitări!" : message;
        transactionService.recordTransaction(
            sender,
            "GIFT_SENT",
            -amount,
            sender.getActivityPoints(),
            "Cadou trimis către " + recipient.getName() + " (" + note + ")"
        );

        transactionService.recordTransaction(
            recipient,
            "GIFT_RECEIVED",
            amount,
            recipient.getActivityPoints(),
            "Cadou primit de la " + sender.getName() + " (" + note + ")"
        );

        res.put("success", true);
        res.put("message", "Ai trimis cu succes " + amount + " AP către " + recipient.getName() + "!");
        res.put("amount", amount);
        res.put("recipientName", recipient.getName());
        res.put("newBalance", sender.getActivityPoints());
        return org.springframework.http.ResponseEntity.ok(res);
    }
}
