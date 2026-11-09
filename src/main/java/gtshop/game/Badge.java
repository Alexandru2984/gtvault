package gtshop.game;

import gtshop.model.TxType;

/**
 * Catalogul de insigne. Codurile existente in productie (FIRST_ORDER, BIG_SPENDER, STREAK_3, STREAK_7,
 * TECH_BRAIN, LUCKY_SPINNER) sunt pastrate.
 *
 * <p>{@code metric} indica de unde se calculeaza progresul; insignele de tip EVENT se deblocheaza
 * doar la un eveniment anume (de exemplu jackpot la roata).</p>
 */
public enum Badge {
    FIRST_ORDER("Primul pas", "Ai finalizat prima comandă din RewardHub.", "🛍️", Rarity.COMMON, Metric.TX_COUNT, TxType.ORDER_PAYMENT, 1),
    BIG_SPENDER("Tech VIP", "Ai plasat o comandă de cel puțin 1000 AP.", "💎", Rarity.RARE, Metric.EVENT, null, 1),
    LOYAL_CUSTOMER("Client fidel", "Ai plasat 5 comenzi.", "📦", Rarity.EPIC, Metric.TX_COUNT, TxType.ORDER_PAYMENT, 5),
    STREAK_3("Hot Streak", "Streak de 3 zile consecutive.", "🔥", Rarity.COMMON, Metric.LONGEST_STREAK, null, 3),
    STREAK_7("Săptămâna perfectă", "Streak de 7 zile consecutive.", "👑", Rarity.RARE, Metric.LONGEST_STREAK, null, 7),
    STREAK_30("De neoprit", "Streak de 30 de zile consecutive.", "🚀", Rarity.EPIC, Metric.LONGEST_STREAK, null, 30),
    STREAK_100("Legenda comunității", "Streak de 100 de zile consecutive.", "🏆", Rarity.LEGENDARY, Metric.LONGEST_STREAK, null, 100),
    TECH_BRAIN("Tech Brain", "Primul răspuns corect la quiz-ul zilnic.", "🧠", Rarity.COMMON, Metric.TX_COUNT, TxType.QUIZ_REWARD, 1),
    QUIZ_MASTER("Quiz Master", "10 răspunsuri corecte la quiz.", "🎓", Rarity.EPIC, Metric.TX_COUNT, TxType.QUIZ_REWARD, 10),
    LUCKY_SPINNER("Lucky Spinner", "Ai învârtit Roata Norocului.", "🎡", Rarity.COMMON, Metric.EVENT, null, 1),
    JACKPOT("Jackpot!", "Ai nimerit marele premiu la Roata Norocului.", "🎰", Rarity.LEGENDARY, Metric.EVENT, null, 1),
    CRATE_HUNTER("Vânător de cufere", "Ai deschis primul Cufăr Misterios.", "🎁", Rarity.COMMON, Metric.TX_COUNT, TxType.LOOT_CRATE_FEE, 1),
    CRATE_HOARDER("Colecționar", "Ai deschis 25 de cufere.", "🗝️", Rarity.EPIC, Metric.TX_COUNT, TxType.LOOT_CRATE_FEE, 25),
    MYTHIC_LUCK("Noroc mitic", "Ai găsit un drop MYTHIC într-un cufăr.", "🌌", Rarity.LEGENDARY, Metric.EVENT, null, 1),
    GENEROUS("Suflet generos", "Ai trimis primul cadou unui prieten.", "💝", Rarity.COMMON, Metric.TX_COUNT, TxType.GIFT_SENT, 1),
    PHILANTHROPIST("Filantrop", "Ai trimis 10 cadouri.", "🤲", Rarity.EPIC, Metric.TX_COUNT, TxType.GIFT_SENT, 10),
    RECRUITER("Recrutor", "Un prieten s-a alăturat cu codul tău.", "🤝", Rarity.RARE, Metric.REFERRALS, null, 1),
    INFLUENCER("Influencer", "5 prieteni s-au alăturat cu codul tău.", "📣", Rarity.LEGENDARY, Metric.REFERRALS, null, 5),
    VISIONARY("Vizionar", "Ai propus o recompensă nouă comunității.", "💡", Rarity.COMMON, Metric.PROPOSALS, null, 1),
    VOICE_OF_COMMUNITY("Vocea comunității", "Ai votat 10 propuneri.", "🗳️", Rarity.RARE, Metric.TX_COUNT, TxType.VOTE_REWARD, 10),
    QUEST_RUNNER("Maratonist", "Ai finalizat 10 misiuni.", "🎯", Rarity.RARE, Metric.TX_COUNT, TxType.QUEST_REWARD, 10),
    QUEST_LEGEND("Erou al misiunilor", "Ai finalizat 50 de misiuni.", "🛡️", Rarity.LEGENDARY, Metric.TX_COUNT, TxType.QUEST_REWARD, 50),
    CRITIC("Critic de încredere", "Ai scris prima recenzie verificată.", "⭐", Rarity.COMMON, Metric.TX_COUNT, TxType.REVIEW_REWARD, 1),
    WORDSMITH("Wordsmith", "Ai ghicit 5 cuvinte în Tech Wordle.", "🔤", Rarity.RARE, Metric.TX_COUNT, TxType.WORDLE_REWARD, 5),
    /** Se obtine doar cumparand „Badge Hero” din magazin (fara recompensa in AP). */
    HERO("Hero", "Insigna exclusivă Hero, afișată pe profil.", "🦸", Rarity.LEGENDARY, Metric.EVENT, null, 1);

    public enum Rarity {
        COMMON(25), RARE(75), EPIC(150), LEGENDARY(300);

        private final int reward;

        Rarity(int reward) {
            this.reward = reward;
        }

        public int reward() {
            return reward;
        }
    }

    public enum Metric { TX_COUNT, LONGEST_STREAK, REFERRALS, PROPOSALS, EVENT }

    private final String title;
    private final String description;
    private final String icon;
    private final Rarity rarity;
    private final Metric metric;
    private final TxType txType;
    private final int target;

    Badge(String title, String description, String icon, Rarity rarity, Metric metric, TxType txType, int target) {
        this.title = title;
        this.description = description;
        this.icon = icon;
        this.rarity = rarity;
        this.metric = metric;
        this.txType = txType;
        this.target = target;
    }

    public String title() { return title; }
    public String description() { return description; }
    public String icon() { return icon; }
    public Rarity rarity() { return rarity; }
    public Metric metric() { return metric; }
    public TxType txType() { return txType; }
    public int target() { return target; }

    public static Badge byCode(String code) {
        try {
            return code == null ? null : Badge.valueOf(code);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
