package singleplayer;

/** Process-local Android lifecycle state shared by the RT4 client and world engine. */
public final class MobileLifecycleBridge {
    private static volatile boolean appPaused;

    private MobileLifecycleBridge() {}

    public static void setAppPaused(boolean paused) {
        appPaused = paused;
        System.out.println("SINGLEPLAYER_LIFECYCLE: " + (paused ? "PAUSED" : "RESUMED"));
    }

    public static boolean isAppPaused() {
        return appPaused;
    }
}
