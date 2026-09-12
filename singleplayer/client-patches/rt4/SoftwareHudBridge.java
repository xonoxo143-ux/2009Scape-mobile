package rt4;

import java.util.Arrays;

/** Keeps the complete retained software HUD present on every composed frame. */
public final class SoftwareHudBridge {
    private static final int RESIZABLE_ROOT = 746;
    private static boolean announced;

    private SoftwareHudBridge() {}

    /**
     * RT4's software path rebuilds the resizable rectangle list while drawing a
     * frame. Marking the previous frame's pending-invalidations is not enough:
     * the newly rebuilt HUD rectangle indexes can then miss the final AWT blit,
     * which presents a world-only frame and makes every HUD element flicker.
     *
     * rectangleRedraw survives that rectangle-list rebuild and is consumed only
     * after the current frame has been fully composed. Arming the whole fixed
     * 100-slot table therefore makes the current frame's actual rectangles blit
     * from the final framebuffer, without changing viewport dimensions or GL.
     */
    public static void prepareFrameBlit() {
        if (!Boolean.getBoolean("singleplayer")
                || client.gameState != 30
                || GlRenderer.enabled
                || LocalViewportBridge.presentationWindowMode(0) != 2
                || InterfaceList.topLevelInterface != RESIZABLE_ROOT) {
            return;
        }

        Arrays.fill(InterfaceList.rectangleRedraw, true);
        if (!announced) {
            announced = true;
            System.out.println("SINGLEPLAYER_HUD: FULL_SOFTWARE_FRAME_BLIT_ARMED");
        }
    }
}
