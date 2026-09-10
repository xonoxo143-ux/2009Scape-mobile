package singleplayer;

/** Android lifecycle ingress for the single local game authority. */
public final class MobileLifecycleBridge {
    private MobileLifecycleBridge() {}

    public static void setAppPaused(boolean paused) {
        LocalGameRuntime.get().setAppPaused(paused);
        System.out.println(
                "SINGLEPLAYER_LIFECYCLE: " + (paused ? "PAUSED" : "RESUMED"));
    }

    public static boolean isAppPaused() {
        return LocalGameRuntime.get().isAppPaused();
    }
}
