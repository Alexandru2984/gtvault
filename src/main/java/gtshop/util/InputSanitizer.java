package gtshop.util;

import gtshop.web.ApiException;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizare si validare pentru textul introdus de utilizatori.
 * Frontend-ul face oricum escape la afisare; aici aplicam aparare in adancime.
 */
public final class InputSanitizer {
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\\n]]");
    private static final Pattern MULTI_SPACE = Pattern.compile("[ \\t]+");
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{M}\\p{N} ._'-]{2,40}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,189}\\.[^@\\s]{2,}$");

    private InputSanitizer() {
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            throw ApiException.badRequest("Email-ul este obligatoriu.");
        }
        String e = email.trim().toLowerCase(Locale.ROOT);
        if (e.length() > 254 || !EMAIL.matcher(e).matches()) {
            throw ApiException.badRequest("Adresa de email nu este validă.");
        }
        return e;
    }

    public static String cleanName(String raw) {
        if (raw == null) {
            throw ApiException.badRequest("Numele este obligatoriu.");
        }
        String name = Normalizer.normalize(raw, Normalizer.Form.NFC);
        name = CONTROL.matcher(name).replaceAll("").replace('\n', ' ');
        name = MULTI_SPACE.matcher(name).replaceAll(" ").trim();
        if (!NAME.matcher(name).matches()) {
            throw ApiException.badRequest("Numele trebuie să aibă 2-40 de caractere (litere, cifre, spațiu, . _ ' -).");
        }
        return name;
    }

    /** Text liber (recenzii, propuneri, mesaje): fara caractere de control, lungime limitata. */
    public static String cleanText(String raw, int min, int max, String label) {
        String text = raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFC);
        text = CONTROL.matcher(text).replaceAll("");
        text = text.replaceAll("\\n{3,}", "\n\n").trim();
        if (text.length() < min) {
            throw ApiException.badRequest(label + " trebuie să aibă cel puțin " + min + " caractere.");
        }
        if (text.length() > max) {
            throw ApiException.badRequest(label + " poate avea cel mult " + max + " caractere.");
        }
        return text;
    }

    /** Text optional: null/gol devine null. */
    public static String cleanOptional(String raw, int max, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return cleanText(raw, 0, max, label);
    }
}
