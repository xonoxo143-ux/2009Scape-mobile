package rt4;

import java.util.Arrays;

/** Keeps the complete retained software HUD present on every composed frame. */
public final class SoftwareHudBridge {
    private static final int RESIZABLE_ROOT = 746;
    private static boolean announced;

    private SoftwareHudBridge() {}

    /**
     * RT4's software path uses two different dirty-state stages:
     *
     * 1. aBooleanArray116 decides whether ordinary interface components are
     *    actually rendered into the software framebuffer this frame.
     * 2. rectangleRedraw decides which completed framebuffer rectangles are
     *    copied to the AWT canvas at the end of the frame.
     *
     * The resizable root rebuilds its rectangle list while rendering. The list
     * can therefore contain more/different rectangle indexes than the preceding
     * frame. Dirtying only the previous pending list (or only the final blit
     * list) leaves newly allocated HUD rectangles with a false render gate. The
     * world is then rendered underneath them and a one-frame UI dropout appears.
     *
     * Arm all three 100-slot tables before the frame. LoginManager may rewrite
     * the indexes that existed last frame, but newly allocated indexes remain
     * true, so every current HUD rectangle is both rendered and presented.
     */
    public static void prepareFrameBlit() {
        if (!Boolean.getBoolean("singleplayer")
                || client.gameState != 30
                || GlRenderer.enabled
                || LocalViewportBridge.presentationWindowMode(0) != 2
                || InterfaceList.topLevelInterface != RESIZABLE_ROOT) {
            return;
        }

        Arrays.fill(InterfaceList.aBooleanArray100, true);
        Arrays.fill(InterfaceList.aBooleanArray116, true);
        Arrays.fill(InterfaceList.rectangleRedraw, true);
        if (!announced) {
            announced = true;
            System.out.println("SINGLEPLAYER_HUD: FULL_SOFTWARE_RENDER_AND_BLIT_ARMED");
        }
    }
}
