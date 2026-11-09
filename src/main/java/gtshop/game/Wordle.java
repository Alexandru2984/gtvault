package gtshop.game;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Regulile Tech Wordle (fara stare): validarea incercarilor, colorarea literelor si alegerea cuvantului zilei.
 *
 * <p>Cuvantul zilei este acelasi pentru toata comunitatea, dar ordinea cuvintelor este amestecata cu un
 * HMAC peste secretul serverului, deci nu poate fi calculat din codul sursa public. Fiecare cuvant apare
 * o singura data pe ciclu (un ciclu = numarul de cuvinte din banca).</p>
 */
public final class Wordle {
    public enum Mark { HIT, NEAR, MISS }

    private static final Pattern WORD = Pattern.compile("^[A-Z]{" + Economy.WORDLE_LENGTH + "}$");

    private Wordle() {
    }

    /** Normalizeaza incercarea (majuscule, fara spatii); intoarce null daca nu are exact 5 litere A–Z. */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String w = raw.trim().toUpperCase(Locale.ROOT);
        return WORD.matcher(w).matches() ? w : null;
    }

    /**
     * Coloreaza o incercare dupa regulile clasice: intai literele pe pozitia corecta, apoi literele prezente
     * in alta pozitie, in limita aparitiilor ramase in cuvant (deci dublurile sunt tratate corect).
     */
    public static List<Mark> score(String guess, String answer) {
        int n = answer.length();
        Mark[] marks = new Mark[n];
        int[] remaining = new int[26];
        for (int i = 0; i < n; i++) {
            if (guess.charAt(i) == answer.charAt(i)) {
                marks[i] = Mark.HIT;
            } else {
                remaining[answer.charAt(i) - 'A']++;
            }
        }
        for (int i = 0; i < n; i++) {
            if (marks[i] != null) {
                continue;
            }
            int c = guess.charAt(i) - 'A';
            if (remaining[c] > 0) {
                marks[i] = Mark.NEAR;
                remaining[c]--;
            } else {
                marks[i] = Mark.MISS;
            }
        }
        return List.of(marks);
    }

    /** Indexul cuvantului zilei in banca de cuvinte. */
    public static int dailyIndex(LocalDate day, int bankSize, byte[] secret) {
        long epochDay = day.toEpochDay();
        long cycle = Math.floorDiv(epochDay, bankSize);
        int position = (int) Math.floorMod(epochDay, bankSize);
        List<Integer> order = new ArrayList<>(bankSize);
        for (int i = 0; i < bankSize; i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random(seed(secret, "wordle-cycle|" + cycle)));
        return order.get(position);
    }

    static long seed(byte[] secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return ByteBuffer.wrap(mac.doFinal(message.getBytes(StandardCharsets.UTF_8))).getLong();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponibil", e);
        }
    }

    /** Grila de distribuit (fara litere, deci nu dezvaluie cuvantul). */
    public static String emojiRow(List<Mark> marks) {
        StringBuilder sb = new StringBuilder();
        for (Mark m : marks) {
            sb.append(switch (m) {
                case HIT -> "🟩";
                case NEAR -> "🟨";
                case MISS -> "⬛";
            });
        }
        return sb.toString();
    }
}
