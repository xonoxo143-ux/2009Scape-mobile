package rt4;

import singleplayer.MobileLifecycleBridge;

/**
 * RT4 timer owned by the Android single-player runtime.
 *
 * Besides freezing simulation while Android is backgrounded, this is the small
 * always-running client seam that advances socketless local world entry. That
 * keeps login/bootstrap behavior inside the game runtime rather than depending
 * on a separately packaged client plugin.
 */
public final class NanoTimer extends Timer {
    private long nextTick = System.nanoTime();

    @Override
    public void reset() {
        nextTick = System.nanoTime();
    }

    @Override
    public int sleep(int minimumDelayMs, int tickMs) {
        if (MobileLifecycleBridge.isAppPaused()) {
            nextTick = System.nanoTime();
            ThreadUtils.sleep(250L);
            return 0;
        }
        LocalLoginBridge.tickAutoLogin();
        long minimumDelayNs = (long) minimumDelayMs * 1_000_000L;
        long delayNs = nextTick - System.nanoTime();
        if (minimumDelayNs > delayNs) {
            delayNs = minimumDelayNs;
        }
        ThreadUtils.sleep(delayNs / 1_000_000L);
        return advance(tickMs);
    }

    @Override
    public int count(int minimumDelayMs, int tickMs) {
        if (MobileLifecycleBridge.isAppPaused()) {
            // Do not accumulate elapsed wall time while Android is backgrounded.
            // Returning to the app resumes from "now" instead of running a burst
            // of catch-up client ticks.
            nextTick = System.nanoTime();
            ThreadUtils.sleep(250L);
            return 0;
        }
        LocalLoginBridge.tickAutoLogin();
        return advance(tickMs);
    }

    private int advance(int tickMs) {
        int cycles = 0;
        long now = System.nanoTime();
        while (cycles < 10 && (cycles < 1 || nextTick < now)) {
            cycles++;
            nextTick += (long) tickMs * 1_000_000L;
        }
        if (now > nextTick) {
            nextTick = now;
        }
        return cycles;
    }
}
