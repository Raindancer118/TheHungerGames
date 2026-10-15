import java.util.ArrayList;
import java.util.List;

public class Benchmark {

    public static void main(String[] args) {
        int playerCount = 100;
        List<Player> players = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            players.add(new Player());
        }

        int iterations = 100_000;

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            // Unoptimized (multiple loops)
            for (Player p : players) { p.doA(); }
            for (Player p : players) { p.doB(); }
            for (Player p : players) { p.doC(); }
        }
        long durationUnoptimized = System.nanoTime() - start;

        start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            // Optimized (single loop)
            for (Player p : players) {
                p.doA();
                p.doB();
                p.doC();
            }
        }
        long durationOptimized = System.nanoTime() - start;

        System.out.println("Unoptimized: " + (durationUnoptimized / 1_000_000) + " ms");
        System.out.println("Optimized: " + (durationOptimized / 1_000_000) + " ms");
    }

    static class Player {
        int state = 0;
        void doA() { state++; }
        void doB() { state--; }
        void doC() { state += 2; }
    }
}
