package rt4;

/** Completed software frames borrowed by the Android renderer under this class's monitor. */
public final class DirectFrameBridge {
    private static int requestedWidth;
    private static int requestedHeight;
    private static int[] snapshot;
    private static long sequence;
    private static boolean announced;

    private DirectFrameBridge() {}

    /** Called at the retained framebuffer's final blit, after world, HUD and plugins. */
    public static boolean present(FrameBuffer frame) {
        return publish(frame.pixels, frame.width, frame.height,
                Boolean.getBoolean("singleplayer") && client.gameState == 30
                        && !GlRenderer.enabled);
    }

    static synchronized boolean publish(int[] pixels, int width, int height, boolean playable) {
        long count = (long) width * height;
        if (!playable || width <= 0 || height <= 0
                || width != requestedWidth || height != requestedHeight
                || pixels == null || count > pixels.length || count > Integer.MAX_VALUE) {
            snapshot = null;
            return false;
        }
        // The render thread may immediately reuse its own pixels. Keep one
        // reusable, completed snapshot and never expose the live raster to JNI.
        if (snapshot == null || snapshot.length != (int) count) {
            snapshot = new int[(int) count];
        }
        System.arraycopy(pixels, 0, snapshot, 0, (int) count);
        sequence = sequence == Long.MAX_VALUE ? 1 : sequence + 1;
        if (!announced) {
            announced = true;
            System.out.println("SINGLEPLAYER_FRAME: DIRECT_RT4 " + width + "x" + height);
        }
        return true;
    }

    /**
     * JNI must hold this class's monitor until it has copied the returned array.
     * Calling this only arms presentation; it never initializes RT4 or gates boot.
     * An older Android shell never calls it, so ordinary AWT blits remain active.
     */
    public static synchronized int[] acquireFrame(int width, int height) {
        if (width <= 0 || height <= 0 || (long) width * height > Integer.MAX_VALUE) {
            disable();
            return null;
        }
        if (width != requestedWidth || height != requestedHeight) {
            requestedWidth = width;
            requestedHeight = height;
            snapshot = null;
        }
        // Only inspect game classes after the render thread has published a
        // frame. An early native lookup must not initialize the game's classes.
        if (snapshot != null && (client.gameState != 30 || GlRenderer.enabled)) {
            snapshot = null;
        }
        return snapshot;
    }

    public static synchronized long frameSequence() {
        return sequence;
    }

    public static synchronized void disable() {
        requestedWidth = 0;
        requestedHeight = 0;
        snapshot = null;
    }
}
