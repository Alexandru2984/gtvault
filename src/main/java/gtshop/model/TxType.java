package gtshop.model;

/**
 * Tipurile de miscari in jurnalul de puncte. {@code countsAsXp} indica daca un credit
 * contribuie la XP-ul pe viata (nivel/tier/clasament). Castigurile la jocuri de noroc,
 * cadourile si rambursarile NU conteaza ca XP (altfel nivelul ar putea fi "cumparat").
 */
public enum TxType {
    WELCOME_BONUS(false, "🎉"),
    REFERRAL_BONUS(true, "🤝"),
    DAILY_STREAK(true, "🔥"),
    QUIZ_REWARD(true, "🧠"),
    FORTUNE_COOKIE(true, "🥠"),
    QUEST_REWARD(true, "🎯"),
    WORDLE_REWARD(true, "🔤"),
    COMMUNITY_CONTRIBUTION(true, "💡"),
    VOTE_REWARD(true, "🗳️"),
    REVIEW_REWARD(true, "⭐"),
    BADGE_REWARD(true, "🏅"),
    LUCKY_WHEEL_FEE(false, "🎡"),
    LUCKY_WHEEL_WIN(false, "🎡"),
    LOOT_CRATE_FEE(false, "🎁"),
    LOOT_CRATE_WIN(false, "🎁"),
    GIFT_SENT(false, "💸"),
    GIFT_RECEIVED(false, "🎁"),
    ORDER_PAYMENT(false, "🛍️"),
    ORDER_REFUND(false, "↩️"),
    STREAK_FREEZE_PURCHASE(false, "🛡️"),
    ADMIN_ADJUSTMENT(false, "🛠️");

    private final boolean countsAsXp;
    private final String icon;

    TxType(boolean countsAsXp, String icon) {
        this.countsAsXp = countsAsXp;
        this.icon = icon;
    }

    public boolean countsAsXp() {
        return countsAsXp;
    }

    public String icon() {
        return icon;
    }
}
