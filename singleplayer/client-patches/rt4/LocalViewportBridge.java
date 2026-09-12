package rt4;

import java.awt.Insets;
import java.util.Arrays;

/**
 * Expands RT4's retained software framebuffer to the Android-owned logical
 * viewport. Cacio is already created at this size before the JVM starts; this
 * bridge changes the actual RT4 software canvas before Android reveals the game.
 */
public final class LocalViewportBridge {
    private static int appliedWidth = -1;
    private static int appliedHeight = -1;
    private static int notifiedWidth = -1;
    private static int notifiedHeight = -1;
    private static int layoutAttempts;
    private static long nextLayoutAttemptMs;
    private static boolean layoutConfirmed;
    private static boolean layoutFailureReported;

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
                && GameShell.canvasHeight == height
                && GameShell.frame.getX() == 0
                && GameShell.frame.getY() == 0
                && GameShell.frame.getWidth() == width
                        + GameShell.frame.getInsets().left + GameShell.frame.getInsets().right
                && GameShell.frame.getHeight() == height
                        + GameShell.frame.getInsets().top + GameShell.frame.getInsets().bottom
                && GameShell.canvas.getWidth() == width
                && GameShell.canvas.getHeight() == height
                && GameShell.canvas.getX() == GameShell.frame.getInsets().left
                && GameShell.canvas.getY() == GameShell.frame.getInsets().top) {
            synchronizeWorldLayout(width, height);
            prepareSoftwareInterfaceRedraw();
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

            // The legacy 765-wide window was centered on the wider Cacio screen.
            // Resizing it alone preserves that desktop offset and clips its right
            // edge. Canvas coordinates are relative to this outer window.
            GameShell.frame.setBounds(0, 0,
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
            layoutAttempts = 0;
            nextLayoutAttemptMs = 0L;
            layoutConfirmed = false;
            layoutFailureReported = false;
            System.out.println(
                    "SINGLEPLAYER_VIEWPORT: RT4_SOFTWARE_READY " + width + "x" + height
                            + " frameOrigin=" + GameShell.frame.getX() + "," + GameShell.frame.getY());
            synchronizeWorldLayout(width, height);
            prepareSoftwareInterfaceRedraw();
            return true;
        } catch (Throwable failure) {
            System.err.println("SINGLEPLAYER_VIEWPORT: APPLY_FAILED " + failure);
            return false;
        }
    }

    private static void prepareSoftwareInterfaceRedraw() {
        if (client.gameState != 30 || GlRenderer.enabled
                || presentationWindowMode(0) != 2
                || InterfaceList.topLevelInterface != 746) return;

        // The resizable root overlays HUD components on the world. Its world
        // rectangle is repainted every frame, erasing any unchanged software HUD
        // pixels. Match the retained GL path's interface invalidation without
        // selecting GL: LoginManager propagates these flags before rendering.
        Arrays.fill(InterfaceList.aBooleanArray100, true);
    }

    /** Layout mode is independent of the retained software/GL renderer choice. */
    public static int presentationWindowMode(int rendererWindowMode) {
        if (Boolean.getBoolean("singleplayer")
                && appliedWidth >= 765 && appliedHeight >= 503
                && (appliedWidth > 765 || appliedHeight > 503)) {
            return 2;
        }
        return rendererWindowMode;
    }

    private static void synchronizeWorldLayout(int width, int height) {
        if (client.gameState != 30 || presentationWindowMode(0) != 2) return;

        int root = InterfaceList.topLevelInterface;
        if (root == 746 && notifiedWidth == width && notifiedHeight == height) {
            if (!layoutConfirmed) {
                layoutConfirmed = true;
                layoutAttempts = 0;
                nextLayoutAttemptMs = 0L;
                layoutFailureReported = false;
                GameShell.fullRedraw = true;
                System.out.println("SINGLEPLAYER_WIDESCREEN: READY " + width + "x" + height
                        + " root=" + root
                        + " canvas=" + GameShell.canvas.getWidth() + "x" + GameShell.canvas.getHeight()
                        + " origin=" + GameShell.leftMargin + "," + GameShell.topMargin
                        + " frame=" + GameShell.frame
                        + " renderer=" + (GlRenderer.enabled ? "GL" : "software"));
            }
            return;
        }

        layoutConfirmed = false;
        // Preserve other top-level interfaces, such as a cutscene or full-screen UI.
        if (root != 548 && root != 746) return;
        long now = System.currentTimeMillis();
        if (now < nextLayoutAttemptMs) return;
        if (layoutAttempts >= 3) {
            if (!layoutFailureReported) {
                layoutFailureReported = true;
                System.err.println("SINGLEPLAYER_WIDESCREEN: LAYOUT_UNCONFIRMED root=" + root);
            }
            return;
        }
        nextLayoutAttemptMs = now + 2000L;
        layoutAttempts++;

        // Growing pixels does not switch interface 548 to the resizable 746.
        // Use the retained world command so tabs, overlays and interface slots
        // move together. Queue failure must never gate GAME_READY or local login.
        boolean queued = LocalClientCommands.trackingDisplay(
                2, width, height, Preferences.antiAliasingMode);
        if (queued) {
            notifiedWidth = width;
            notifiedHeight = height;
        }
        System.out.println("SINGLEPLAYER_WIDESCREEN: LAYOUT_REQUEST " + width + "x" + height
                + " root=" + root + " queued=" + queued
                + " attempt=" + layoutAttempts
                + " renderer=" + (GlRenderer.enabled ? "GL" : "software"));
    }
}
