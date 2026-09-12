package singleplayer;

/** Android lifecycle ingress for the single local game authority. */
public final class MobileLifecycleBridge {
    private static volatile boolean requestedPaused;

    private MobileLifecycleBridge() {}

    /**
     * Android may transiently pause/resume the launcher while the embedded JVM
     * and world are still booting. A lifecycle callback is allowed to record the
     * desired foreground state at that point, but it must never suspend world
     * initialization. Pause becomes effective only after the RT4 client has
     * reached its playable RUNNING/PAUSED phase.
     */
    public static void setAppPaused(boolean paused) {
        requestedPaused = paused;
        LocalGameRuntime runtime = LocalGameRuntime.get();
        LocalGameRuntime.State state = runtime.state();
        if (state == LocalGameRuntime.State.RUNNING
                || state == LocalGameRuntime.State.PAUSED) {
            runtime.setAppPaused(paused);
            System.out.println(
                    "SINGLEPLAYER_LIFECYCLE: " + (paused ? "PAUSED" : "RESUMED"));
            return;
        }

        System.out.println(
                "SINGLEPLAYER_LIFECYCLE: DEFERRED_"
                        + (paused ? "PAUSE" : "RESUME")
                        + " state=" + state);
    }

    public static boolean isAppPaused() {
        LocalGameRuntime runtime = LocalGameRuntime.get();
        LocalGameRuntime.State state = runtime.state();

        // Bootstrap is deliberately unpausable. In particular, MajorUpdateWorker
        // must not freeze while GameWorld.prompt()/Server.main() are still
        // establishing the local authority.
        if (state != LocalGameRuntime.State.RUNNING
                && state != LocalGameRuntime.State.PAUSED) {
            return false;
        }

        if (runtime.isAppPaused() != requestedPaused) {
            runtime.setAppPaused(requestedPaused);
        }
        return runtime.isAppPaused();
    }
}
