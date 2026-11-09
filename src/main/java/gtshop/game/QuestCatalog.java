package gtshop.game;

import java.util.List;

/**
 * Misiunile disponibile. Progresul este urmarit exclusiv pe server, la evenimentul real
 * (nu exista endpoint prin care clientul sa-si raporteze singur progresul).
 */
public final class QuestCatalog {

    public enum Action { DAILY_LOGIN, SOLVE_QUIZ, CORRECT_QUIZ, SPIN_WHEEL, OPEN_CRATE, FORTUNE, VOTE_PROPOSAL, SEND_GIFT, PLACE_ORDER, WRITE_REVIEW, PLAY_WORDLE }

    public record QuestDef(Action action, String title, String description, int reward, int target) {}

    /** Misiunea zilnica fixa: revendicarea bonusului de streak. */
    public static final QuestDef DAILY_ANCHOR =
            new QuestDef(Action.DAILY_LOGIN, "⚡ Conectare zilnică", "Revendică bonusul de streak de azi.", 30, 1);

    /** Din acest bazin se aleg zilnic inca 3 misiuni (diferite pentru fiecare utilizator). */
    public static final List<QuestDef> DAILY_POOL = List.of(
            new QuestDef(Action.SOLVE_QUIZ, "🧠 Minte de inginer", "Răspunde la quiz-ul tehnic al zilei.", 40, 1),
            new QuestDef(Action.SPIN_WHEEL, "🎡 Rotește și câștigă", "Învârte Roata Norocului (rotirea gratuită contează).", 25, 1),
            new QuestDef(Action.FORTUNE, "🥠 Înțelepciunea zilei", "Deschide răvașul tehnic al zilei.", 20, 1),
            new QuestDef(Action.OPEN_CRATE, "🎁 Vânător de cufere", "Deschide un Cufăr Misterios.", 40, 1),
            new QuestDef(Action.VOTE_PROPOSAL, "🗳️ Vocea comunității", "Votează o propunere de recompensă.", 25, 1),
            new QuestDef(Action.SEND_GIFT, "💝 Gest frumos", "Trimite un cadou în AP unui prieten.", 30, 1),
            new QuestDef(Action.CORRECT_QUIZ, "🎯 Răspuns perfect", "Răspunde corect la quiz-ul zilei.", 50, 1),
            new QuestDef(Action.PLAY_WORDLE, "🔤 Cuvântul zilei", "Termină o rundă de Tech Wordle.", 30, 1));

    /** Din acest bazin se aleg saptamanal 3 misiuni. */
    public static final List<QuestDef> WEEKLY_POOL = List.of(
            new QuestDef(Action.DAILY_LOGIN, "🔥 Săptămână activă", "Revendică bonusul zilnic în 5 zile diferite.", 150, 5),
            new QuestDef(Action.CORRECT_QUIZ, "🎓 Expert tehnic", "Răspunde corect la 4 quiz-uri.", 150, 4),
            new QuestDef(Action.OPEN_CRATE, "🗝️ Colecționar de cufere", "Deschide 3 cufere.", 120, 3),
            new QuestDef(Action.VOTE_PROPOSAL, "📣 Implicat în comunitate", "Votează 5 propuneri.", 100, 5),
            new QuestDef(Action.PLACE_ORDER, "🛍️ Răsfăță-te", "Plasează o comandă în magazin.", 200, 1),
            new QuestDef(Action.SPIN_WHEEL, "🎰 Învârtitor de profesie", "Învârte roata de 5 ori.", 100, 5),
            new QuestDef(Action.PLAY_WORDLE, "📚 Vocabular tehnic", "Termină Tech Wordle în 4 zile diferite.", 140, 4));

    public static final int DAILY_EXTRA = 3;
    public static final int WEEKLY_COUNT = 3;

    private QuestCatalog() {
    }
}
