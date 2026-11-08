package gtshop.util;

import java.util.List;

/**
 * Avatarele permise. Nu mai acceptam URL-uri arbitrare (sursa de XSS in atribute si tracking extern).
 */
public final class AvatarCatalog {
    public static final String DEFAULT = "images/user-avatar.svg";
    public static final List<String> IDS = List.of(
            "robot", "astronaut", "ninja", "wizard", "knight", "fox",
            "owl", "panda", "cat", "dragon", "rocket", "hacker");

    private AvatarCatalog() {
    }

    public static List<String> paths() {
        return IDS.stream().map(id -> "images/avatars/" + id + ".svg").toList();
    }

    public static boolean isAllowed(String path) {
        return DEFAULT.equals(path) || paths().contains(path);
    }

    /** Normalizeaza valorile vechi din baza de date (ex. https://example.com/avatar.png). */
    public static String safe(String path) {
        return isAllowed(path) ? path : DEFAULT;
    }
}
