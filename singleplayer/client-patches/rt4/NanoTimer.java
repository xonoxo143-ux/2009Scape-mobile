package rt4;

import singleplayer.MobileLifecycleBridge;

/** RT4 timer that freezes client simulation while the Android app is backgrounded. */
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
            ThreadUtils.sleep(100L);
            return 0;
        }
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
            ThreadUtils.sleep(100L);
            return 0;
        }
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
