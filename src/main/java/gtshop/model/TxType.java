package gtshop.model;

import java.util.Arrays;
import java.util.List;

/**
 * Tipurile de miscari in jurnalul de puncte. {@code countsAsXp} indica daca un credit
 * contribuie la XP-ul pe viata (nivel/tier/clasament). Castigurile la jocuri de noroc,
 * cadourile si rambursarile NU conteaza ca XP (altfel nivelul ar putea fi "cumparat").
 */
public enum TxType {
    WELCOME_BONUS(false, "🎉", "Bonus de bun venit"),
    REFERRAL_BONUS(true, "🤝", "Recomandare"),
    DAILY_STREAK(true, "🔥", "Streak zilnic"),
    QUIZ_REWARD(true, "🧠", "Quiz tehnic"),
    FORTUNE_COOKIE(true, "🥠", "Răvașul zilei"),
    QUEST_REWARD(true, "🎯", "Misiune"),
    WORDLE_REWARD(true, "🔤", "Tech Wordle"),
    COMMUNITY_CONTRIBUTION(true, "💡", "Propunere"),
    VOTE_REWARD(true, "🗳️", "Vot"),
    REVIEW_REWARD(true, "⭐", "Recenzie"),
    BADGE_REWARD(true, "🏅", "Insignă"),
    LUCKY_WHEEL_FEE(false, "🎡", "Rotire roată"),
    LUCKY_WHEEL_WIN(false, "🎡", "Câștig roată"),
    LOOT_CRATE_FEE(false, "🎁", "Deschidere cufăr"),
    LOOT_CRATE_WIN(false, "🎁", "Câștig cufăr"),
    GIFT_SENT(false, "💸", "Cadou trimis"),
    GIFT_RECEIVED(false, "🎁", "Cadou primit"),
    ORDER_PAYMENT(false, "🛍️", "Comandă"),
    ORDER_REFUND(false, "↩️", "Rambursare"),
    STREAK_FREEZE_PURCHASE(false, "🛡️", "Streak freeze"),
    ADMIN_ADJUSTMENT(false, "🛠️", "Ajustare administrator");

    private final boolean countsAsXp;
    private final String icon;
    private final String label;

    TxType(boolean countsAsXp, String icon, String label) {
        this.countsAsXp = countsAsXp;
        this.icon = icon;
        this.label = label;
    }

    public boolean countsAsXp() {
        return countsAsXp;
    }

    public String icon() {
        return icon;
    }

    public String label() {
        return label;
    }

    /** Numele tipurilor care conteaza ca XP (pentru interogarile de clasament). */
    public static final List<String> XP_TYPES = Arrays.stream(values()).filter(TxType::countsAsXp).map(Enum::name).toList();

    public static TxType parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
