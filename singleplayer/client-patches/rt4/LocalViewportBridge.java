package rt4;

import java.awt.Insets;

/**
 * Expands RT4's retained software framebuffer to the Android-owned logical
 * viewport. Cacio is already created at this size before the JVM starts; this
 * bridge changes the actual RT4 software canvas before Android reveals the game.
 */
public final class LocalViewportBridge {
    private static int appliedWidth = -1;
    private static int appliedHeight = -1;

    private LocalViewportBridge() {}

    public static synchronized boolean ensureConfiguredViewport() {
        if (!Boolean.getBoolean("singleplayer")) return true;

        int width = Integer.getInteger("singleplayer.viewportWidth", 765);
        int height = Integer.getInteger("singleplayer.viewportHeight", 503);
        width = Math.max(765, width);
        height = Math.max(503, height);

        if (GameShell.canvas == null || GameShell.frame == null) {
            return false;
        }
        if (appliedWidth == width
                && appliedHeight == height
                && GameShell.canvasWidth == width
                && GameShell.canvasHeight == height) {
            return true;
        }

        try {
            Insets insets = GameShell.frame.getInsets();
            GameShell.frameWidth = width;
            GameShell.frameHeight = height;
            GameShell.canvasWidth = width;
            GameShell.canvasHeight = height;
            GameShell.leftMargin = 0;
            GameShell.topMargin = 0;

            GameShell.frame.setSize(
                    insets.left + width + insets.right,
                    insets.top + height + insets.bottom);
            GameShell.canvas.setSize(width, height);
            GameShell.canvas.setLocation(insets.left, insets.top);

            // The software renderer owns a fixed-size pixel buffer. Rebuild it
            // only after RT4 itself has accepted the new logical dimensions.
            SoftwareRaster.frameBuffer = FrameBuffer.create(
                    height, width, GameShell.canvas);
            if (InterfaceList.topLevelInterface != -1) {
                InterfaceList.method3712(true);
            }
            DisplayMode.aLong89 = 0L;
            GameShell.fullRedraw = true;
            appliedWidth = width;
            appliedHeight = height;
            System.out.println(
                    "SINGLEPLAYER_VIEWPORT: RT4_SOFTWARE_READY " + width + "x" + height);
            return true;
        } catch (Throwable failure) {
            System.err.println("SINGLEPLAYER_VIEWPORT: APPLY_FAILED " + failure);
            return false;
        }
    }
}
