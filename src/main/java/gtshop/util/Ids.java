package gtshop.util;

import java.security.SecureRandom;
import java.util.UUID;

/** Generare de identificatori si coduri cu SecureRandom. */
public final class Ids {
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Fara caractere ambigue (0/O, 1/I/L). */
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();

    private Ids() {
    }

    public static String prefixed(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public static String code(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    /** Cod voucher de forma GT-XXXX-XXXX-XXXX. */
    public static String voucher() {
        return "GT-" + code(4) + "-" + code(4) + "-" + code(4);
    }

    public static SecureRandom random() {
        return RANDOM;
    }
}
