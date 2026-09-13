package rt4;

import java.util.Arrays;

/** Keeps the complete retained software HUD present on every composed frame. */
public final class SoftwareHudBridge {
    private static final int RESIZABLE_ROOT = 746;
    private static boolean announced;
    private static boolean renderCadenceAligned;

    private SoftwareHudBridge() {}

    /**
     * RT4 advances game logic at 50 Hz, but the mobile plugin raises the software
     * render target to 60 Hz. This hook is driven by the logic timer, so at 60 Hz
     * some display frames can occur without a preceding HUD invalidation pass.
     * Those render-only frames are the one-frame UI dropouts seen on device: the
     * world still draws, while retained HUD rectangles wait for the next logic
     * tick.
     *
     * Keep software presentation on the same 50 Hz cadence as RT4 logic. The
     * GameShell loop checks the logic threshold before the render threshold, so
     * every displayed software frame is preceded by this hook. Also request a
     * full framebuffer presentation rather than depending on RT4's old partial
     * dirty-rectangle copy path.
     */
    public static void prepareFrameBlit() {
        if (!Boolean.getBoolean("singleplayer")
                || client.gameState != 30
                || GlRenderer.enabled
                || LocalViewportBridge.presentationWindowMode(0) != 2
                || InterfaceList.topLevelInterface != RESIZABLE_ROOT) {
            return;
        }

        if (!renderCadenceAligned) {
            GameShell.setFpsTarget(50);
            renderCadenceAligned = true;
        }

        Arrays.fill(InterfaceList.aBooleanArray100, true);
        Arrays.fill(InterfaceList.aBooleanArray116, true);
        Arrays.fill(InterfaceList.rectangleRedraw, true);
        GameShell.fullRedraw = true;

        if (!announced) {
            announced = true;
            System.out.println("SINGLEPLAYER_HUD: SOFTWARE_RENDER_SYNC_50HZ_ARMED");
        }
    }
}
