package progescps;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Provides lines of input to GameManager. The UI submits strings via submit(...).
 * GameManager blocks on nextLine() until a line is available.
 */
public class InputProvider {
    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>();

    /**
     * Blocks until a line of input is available.
     */
    public String nextLine() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        }
    }

    /**
     * Submit a line (from the UI). This will unblock any waiting nextLine().
     */
    public void submit(String line) {
        if (line == null) line = "";
        try {
            queue.put(line);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Convenience: submit trimmed line.
     */
    public void submitTrimmed(String line) {
        submit(line == null ? "" : line.trim());
    }

    /**
     * Clear any pending inputs in the queue. Useful when starting a fresh game
     * so stale inputs from the previous session don't get consumed immediately.
     */
    public void clearPending() {
        queue.clear();
    }
}