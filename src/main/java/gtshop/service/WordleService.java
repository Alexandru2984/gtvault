package gtshop.service;

import gtshop.config.AppProperties;
import gtshop.game.Badge;
import gtshop.game.ContentBank;
import gtshop.game.Economy;
import gtshop.game.QuestCatalog.Action;
import gtshop.game.Wordle;
import gtshop.model.TxType;
import gtshop.model.UserProfile;
import gtshop.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tech Wordle: un cuvant tehnic de 5 litere pe zi, acelasi pentru toata comunitatea, 6 incercari.
 * Tot jocul se evalueaza pe server; raspunsul nu este trimis clientului decat dupa terminarea rundei.
 */
@Service
public class WordleService {
    static final String PLAYING = "PLAYING";
    static final String WON = "WON";
    static final String LOST = "LOST";
    /** Prima zi a jocului (puzzle #1). */
    static final LocalDate LAUNCH = LocalDate.of(2026, 11, 9);

    private static final String UPSERT = """
            INSERT INTO wordle_games (user_id, game_date, guesses, status, reward, updated_at, puzzle_date)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, game_date) DO UPDATE
            SET guesses = EXCLUDED.guesses, status = EXCLUDED.status, reward = EXCLUDED.reward,
                updated_at = EXCLUDED.updated_at, puzzle_date = EXCLUDED.puzzle_date""";

    private final JdbcTemplate jdbc;
    private final WalletService wallet;
    private final BadgeService badges;
    private final QuestService quests;
    private final ContentBank content;
    private final TimeService time;
    private final byte[] secret;

    public WordleService(JdbcTemplate jdbc, WalletService wallet, BadgeService badges, QuestService quests,
                         ContentBank content, TimeService time, AppProperties props,
                         @Value("${jwt.secret:}") String jwtSecret) {
        this.jdbc = jdbc;
        this.wallet = wallet;
        this.badges = badges;
        this.quests = quests;
        this.content = content;
        this.time = time;
        String s = props.getGamesSecret().isBlank() ? jwtSecret : props.getGamesSecret();
        if (s == null || s.length() < 16) {
            throw new IllegalStateException("GAMES_SECRET (sau JWT_SECRET) lipseste: cuvantul zilei nu poate fi ales sigur.");
        }
        this.secret = ("tech-wordle:" + s).getBytes(StandardCharsets.UTF_8);
    }

    private record Game(List<String> guesses, String status, int reward, String puzzleDate) {
        static final Game EMPTY = new Game(List.of(), PLAYING, 0, null);
    }

    /** Cuvantul unei zile (folosit si de teste; nu este expus prin API inainte de finalul rundei). */
    public ContentBank.WordleWord wordFor(LocalDate day) {
        Optional<ContentBank.WordleWord> stored = storedPuzzle(day);
        if (stored.isPresent()) return stored.get();
        // O victorie existentă este dovada cuvântului jucat înaintea migrării.
        var legacy = jdbc.queryForList("""
                SELECT guesses FROM wordle_games WHERE game_date = ? AND status = 'WON'
                ORDER BY updated_at, user_id LIMIT 1
                """, String.class, day.toString());
        if (!legacy.isEmpty()) {
            String[] guesses = legacy.getFirst().split(",");
            String answer = Wordle.normalize(guesses[guesses.length - 1]);
            if (answer != null) return describe(answer);
        }
        return content.words().get(Wordle.dailyIndex(day, content.words().size(), secret));
    }

    private Optional<ContentBank.WordleWord> storedPuzzle(LocalDate day) {
        return jdbc.query("SELECT answer, topic, hint FROM wordle_puzzles WHERE game_date = ?",
                (rs, row) -> new ContentBank.WordleWord(rs.getString(1), rs.getString(2), rs.getString(3)), day.toString())
                .stream().findFirst();
    }

    private ContentBank.WordleWord describe(String answer) {
        return content.words().stream().filter(w -> w.word().equals(answer)).findFirst()
                .orElse(new ContentBank.WordleWord(answer, "Rundă arhivată", "Cuvânt folosit într-o rundă anterioară."));
    }

    private ContentBank.WordleWord puzzleForPlay(LocalDate day) {
        Optional<ContentBank.WordleWord> stored = storedPuzzle(day);
        if (stored.isPresent()) return stored.get();
        ContentBank.WordleWord candidate = wordFor(day);
        jdbc.update("""
                INSERT INTO wordle_puzzles (game_date, answer, topic, hint, created_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (game_date) DO NOTHING
                """, day.toString(), candidate.word(), candidate.topic(), candidate.hint(), Timestamp.from(time.now()));
        // În cazul pornirilor simultane, toate conturile folosesc rândul care a câștigat inserarea.
        return storedPuzzle(day).orElseThrow();
    }

    private ContentBank.WordleWord answerFor(Game game, LocalDate day) {
        if (game.puzzleDate() != null) return storedPuzzle(day).orElseThrow();
        if (WON.equals(game.status()) && !game.guesses().isEmpty()) {
            return describe(game.guesses().getLast());
        }
        // Soluția unei înfrângeri vechi nu se poate deduce în siguranță din încercările salvate.
        return PLAYING.equals(game.status()) ? wordFor(day) : null;
    }

    static int puzzleNumber(LocalDate day) {
        return (int) Math.max(1, day.toEpochDay() - LAUNCH.toEpochDay() + 1);
    }

    // ------------------------------------------------------------------ API

    @Transactional(readOnly = true)
    public Map<String, Object> state(Optional<String> userId) {
        LocalDate today = time.today();
        Map<String, Object> r = base(today);
        if (userId.isEmpty()) {
            r.put("requiresLogin", true);
            r.put("status", PLAYING);
            r.put("guesses", List.of());
            r.put("guessesLeft", Economy.WORDLE_MAX_GUESSES);
            return r;
        }
        r.put("requiresLogin", false);
        Game game = load(userId.get(), today.toString()).orElse(Game.EMPTY);
        fill(r, game, answerFor(game, today));
        r.put("stats", stats(userId.get(), today));
        return r;
    }

    @Transactional
    public Map<String, Object> guess(String userId, String raw) {
        UserProfile u = wallet.lock(userId);
        LocalDate today = time.today();
        String date = today.toString();
        Game game = load(userId, date).orElse(Game.EMPTY);
        if (!PLAYING.equals(game.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "GAME_OVER",
                    "Ai terminat runda de azi. Un cuvânt nou apare la miezul nopții.");
        }
        String word = Wordle.normalize(raw);
        if (word == null) {
            throw ApiException.badRequest("INVALID_WORD", "Introdu un cuvânt de exact " + Economy.WORDLE_LENGTH
                    + " litere (A–Z, fără diacritice).");
        }
        if (game.guesses().contains(word)) {
            throw ApiException.badRequest("DUPLICATE_GUESS", "Ai încercat deja cuvântul " + word + ".");
        }
        ContentBank.WordleWord answer = puzzleForPlay(today);
        List<String> guesses = new ArrayList<>(game.guesses());
        guesses.add(word);
        boolean won = word.equals(answer.word());
        boolean lost = !won && guesses.size() >= Economy.WORDLE_MAX_GUESSES;
        String status = won ? WON : lost ? LOST : PLAYING;
        int reward = won ? Economy.WORDLE_REWARDS.get(guesses.size() - 1) : 0;
        jdbc.update(UPSERT, userId, date, String.join(",", guesses), status, reward, Timestamp.from(time.now()), date);

        List<Badge> unlocked = List.of();
        if (won) {
            wallet.credit(u, reward, TxType.WORDLE_REWARD,
                    "Tech Wordle #" + puzzleNumber(today) + ": ghicit din " + guesses.size() + " încercări");
            unlocked = badges.evaluate(u, Badge.WORDSMITH);
        }
        if (!PLAYING.equals(status)) {
            quests.track(u, Action.PLAY_WORDLE, 1);
        }

        Map<String, Object> r = base(today);
        r.put("requiresLogin", false);
        fill(r, new Game(guesses, status, reward, date), answer);
        r.put("stats", stats(userId, today));
        r.put("success", true);
        r.put("lastMarks", Wordle.score(word, answer.word()));
        r.put("message", won ? "Bravo! Ai ghicit „" + answer.word() + "” din " + guesses.size() + " încercări: +" + reward + " AP 🎉"
                : lost ? "Runda s-a terminat. Cuvântul era „" + answer.word() + "”. Revino mâine!"
                : "Mai ai " + (Economy.WORDLE_MAX_GUESSES - guesses.size()) + " încercări.");
        r.put("rewardPoints", reward);
        r.put("newBalance", u.getActivityPoints());
        r.put("badgesUnlocked", unlocked.stream().map(Badge::name).toList());
        return r;
    }

    // ------------------------------------------------------------------ construirea raspunsului

    private Map<String, Object> base(LocalDate today) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("puzzleNumber", puzzleNumber(today));
        r.put("date", today.toString());
        r.put("length", Economy.WORDLE_LENGTH);
        r.put("maxGuesses", Economy.WORDLE_MAX_GUESSES);
        r.put("rewards", Economy.WORDLE_REWARDS);
        r.put("resetsAt", time.startOfTomorrow());
        r.put("secondsUntilReset", time.secondsUntilTomorrow());
        Map<String, Object> community = jdbc.queryForMap("""
                SELECT count(*) FILTER (WHERE status <> 'PLAYING') AS players, count(*) FILTER (WHERE status = 'WON') AS solvers
                FROM wordle_games WHERE game_date = ?""", today.toString());
        r.put("community", community);
        return r;
    }

    private void fill(Map<String, Object> r, Game g, ContentBank.WordleWord answer) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Character, Wordle.Mark> keyboard = new TreeMap<>();
        StringBuilder grid = new StringBuilder();
        for (String guess : g.guesses()) {
            List<Wordle.Mark> marks = answer == null ? null : Wordle.score(guess, answer.word());
            rows.add(Map.of("word", guess, "marks", marks == null ? Collections.nCopies(Economy.WORDLE_LENGTH, "UNKNOWN") : marks));
            if (marks == null) continue;
            grid.append('\n').append(Wordle.emojiRow(marks));
            for (int i = 0; i < guess.length(); i++) {
                char c = guess.charAt(i);
                Wordle.Mark m = marks.get(i);
                Wordle.Mark prev = keyboard.get(c);
                if (prev == null || m.ordinal() < prev.ordinal()) {
                    keyboard.put(c, m);
                }
            }
        }
        boolean finished = !PLAYING.equals(g.status());
        r.put("status", g.status());
        r.put("guesses", rows);
        r.put("guessesLeft", Economy.WORDLE_MAX_GUESSES - g.guesses().size());
        r.put("keyboard", keyboard);
        r.put("gridAvailable", answer != null);
        r.put("nextReward", finished ? null : Economy.WORDLE_REWARDS.get(g.guesses().size()));
        r.put("topicHint", answer != null && (finished || g.guesses().size() >= 3) ? answer.topic() : null);
        if (finished) {
            if (answer != null) {
                r.put("answer", answer.word());
                r.put("hint", answer.hint());
            }
            r.put("reward", g.reward());
            String score = WON.equals(g.status()) ? String.valueOf(g.guesses().size()) : "X";
            r.put("shareText", "Tech Wordle #" + r.get("puzzleNumber") + " " + score + "/" + Economy.WORDLE_MAX_GUESSES + grid);
        }
    }

    private Optional<Game> load(String userId, String date) {
        return jdbc.query("SELECT guesses, status, reward, puzzle_date FROM wordle_games WHERE user_id = ? AND game_date = ?",
                (rs, i) -> {
                    String raw = rs.getString(1);
                    List<String> guesses = raw == null || raw.isEmpty() ? List.of() : Arrays.asList(raw.split(","));
                    return new Game(guesses, rs.getString(2), rs.getInt(3), rs.getString(4));
                }, userId, date).stream().findFirst();
    }

    /** Statistici personale: jocuri, victorii, serie curenta/maxima si distributia incercarilor. */
    Map<String, Object> stats(String userId, LocalDate today) {
        Set<LocalDate> won = new HashSet<>();
        Set<LocalDate> lost = new HashSet<>();
        int[] distribution = new int[Economy.WORDLE_MAX_GUESSES];
        jdbc.query("SELECT game_date, status, guesses FROM wordle_games WHERE user_id = ? AND status <> 'PLAYING'", rs -> {
            LocalDate d = LocalDate.parse(rs.getString(1));
            if (WON.equals(rs.getString(2))) {
                won.add(d);
                int tries = rs.getString(3).split(",").length;
                distribution[Math.min(Economy.WORDLE_MAX_GUESSES, Math.max(1, tries)) - 1]++;
            } else {
                lost.add(d);
            }
        }, userId);
        int current = 0;
        if (!lost.contains(today)) {
            LocalDate d = won.contains(today) ? today : today.minusDays(1);
            while (won.contains(d)) {
                current++;
                d = d.minusDays(1);
            }
        }
        int max = 0;
        for (LocalDate d : won) {
            if (!won.contains(d.minusDays(1))) {
                int run = 0;
                LocalDate x = d;
                while (won.contains(x)) {
                    run++;
                    x = x.plusDays(1);
                }
                max = Math.max(max, run);
            }
        }
        int played = won.size() + lost.size();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("played", played);
        s.put("wins", won.size());
        s.put("winRate", played == 0 ? 0 : Math.round(won.size() * 100.0 / played));
        s.put("currentStreak", current);
        s.put("maxStreak", max);
        s.put("distribution", distribution);
        return s;
    }

    /** Pentru profilul public. */
    @Transactional(readOnly = true)
    public Map<String, Object> publicStats(String userId) {
        Map<String, Object> s = stats(userId, time.today());
        return Map.of("played", s.get("played"), "wins", s.get("wins"), "currentStreak", s.get("currentStreak"),
                "maxStreak", s.get("maxStreak"));
    }
}
