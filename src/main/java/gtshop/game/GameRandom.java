package gtshop.game;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/** Sursa de aleatoriu pentru jocuri (roata, cufere). Criptografic sigura: rezultatele nu pot fi prezise. */
@Component
public class GameRandom {
    private final SecureRandom random = new SecureRandom();

    /** Intreg uniform in [0, bound). */
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }
}
