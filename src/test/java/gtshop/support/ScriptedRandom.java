package gtshop.support;

import gtshop.game.GameRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/** GameRandom pentru teste: intoarce valorile puse in coada, altfel un generator cu seed fix. */
public class ScriptedRandom extends GameRandom {
    private final Deque<Integer> queue = new ArrayDeque<>();
    private final Random fallback = new Random(42);

    public synchronized void push(int... values) {
        for (int v : values) {
            queue.addLast(v);
        }
    }

    public synchronized void clear() {
        queue.clear();
    }

    @Override
    public synchronized int nextInt(int bound) {
        Integer v = queue.pollFirst();
        if (v == null) {
            return fallback.nextInt(bound);
        }
        if (v < 0 || v >= bound) {
            throw new IllegalStateException("Valoare scriptata " + v + " in afara [0," + bound + ")");
        }
        return v;
    }
}
