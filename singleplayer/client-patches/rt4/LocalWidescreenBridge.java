package rt4;

import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Expands the mobile presentation only after the local client has reached the
 * playable state. The bootstrap framebuffer deliberately remains 765x503.
 *
 * This is intentionally best-effort. Any failure leaves the known-good fixed
 * presentation alive rather than participating in game/world readiness.
 */
public final class LocalWidescreenBridge {
    private static final int BOOTSTRAP_WIDTH = 765;
    private static final int BOOTSTRAP_HEIGHT = 503;
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean();

    private LocalWidescreenBridge() {}

    public static void scheduleAfterGameReady() {
        if (!Boolean.getBoolean("singleplayer")) return;
        if (!SCHEDULED.compareAndSet(false, true)) return;

        Thread worker = new Thread(() -> {
            try {
                // Let RT4 render at least one unquestionably playable frame before
                // presentation-only resizing begins. This never gates GAME_READY.
                Thread.sleep(350L);
                applyRuntimeViewport();
            } catch (Throwable failure) {
                logFailure(failure);
            }
        }, "SinglePlayerWidescreen");
        worker.setDaemon(true);
        worker.start();
    }

    private static void applyRuntimeViewport() throws Exception {
        int width = Math.max(
                BOOTSTRAP_WIDTH,
                Integer.getInteger("singleplayer.targetWidth", BOOTSTRAP_WIDTH));
        int height = Math.max(
                BOOTSTRAP_HEIGHT,
                Integer.getInteger("singleplayer.targetHeight", BOOTSTRAP_HEIGHT));

        System.out.println(
                "SINGLEPLAYER_WIDESCREEN: BEGIN old="
                        + BOOTSTRAP_WIDTH + "x" + BOOTSTRAP_HEIGHT
                        + " target=" + width + "x" + height);

        if (width == BOOTSTRAP_WIDTH && height == BOOTSTRAP_HEIGHT) {
            writeReady(width, height);
            System.out.println("SINGLEPLAYER_WIDESCREEN: BOOTSTRAP_SIZE_RETAINED");
            return;
        }

        final int targetWidth = width;
        final int targetHeight = height;
        final Throwable[] awtFailure = new Throwable[1];

        Runnable awtResize = () -> {
            try {
                resizeCacioBackingStore(targetWidth, targetHeight);
                resizeRt4Frame(targetWidth, targetHeight);
            } catch (Throwable failure) {
                awtFailure[0] = failure;
            }
        };

        if (EventQueue.isDispatchThread()) {
            awtResize.run();
        } else {
            EventQueue.invokeAndWait(awtResize);
        }
        if (awtFailure[0] != null) {
            throw new IllegalStateException("AWT widescreen resize failed", awtFailure[0]);
        }

        resizeGlfwWindowBestEffort(targetWidth, targetHeight);
        GameShell.fullRedraw = true;
        writeReady(targetWidth, targetHeight);
        System.out.println(
                "SINGLEPLAYER_WIDESCREEN: READY "
                        + targetWidth + "x" + targetHeight);
    }

    private static void resizeCacioBackingStore(int width, int height) throws Exception {
        Class<?> factoryClass = firstClass(
                "com.github.caciocavallosilano.cacio.peer.managed.FullScreenWindowFactory",
                "net.java.openjdk.cacio.peer.managed.FullScreenWindowFactory");
        Method getDimension = declaredMethod(factoryClass, "getScreenDimension", "getScreenSize");
        Dimension screenDimension = (Dimension) getDimension.invoke(null);

        Class<?> screenClass = firstClass(
                "com.github.caciocavallosilano.cacio.ctc.CTCScreen",
                "net.java.openjdk.cacio.ctc.CTCScreen");
        Method getInstance = screenClass.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        Object screen = getInstance.invoke(null);

        Field screenBufferField = screenClass.getDeclaredField("screenBuffer");
        screenBufferField.setAccessible(true);
        BufferedImage oldBuffer = (BufferedImage) screenBufferField.get(screen);
        int imageType = oldBuffer == null ? BufferedImage.TYPE_INT_ARGB : oldBuffer.getType();
        if (imageType == BufferedImage.TYPE_CUSTOM) imageType = BufferedImage.TYPE_INT_ARGB;

        BufferedImage newBuffer = new BufferedImage(width, height, imageType);
        if (oldBuffer != null) {
            Graphics2D graphics = newBuffer.createGraphics();
            try {
                graphics.drawImage(oldBuffer, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }

        // CTCScreen.getCurrentScreenRGB() reuses this array. Install the larger
        // scratch array before the larger dimensions become observable so the
        // Cacio capture path never indexes past its backing array.
        try {
            Field pixelsField = screenClass.getDeclaredField("dataBufAux");
            pixelsField.setAccessible(true);
            pixelsField.set(null, new int[width * height]);
        } catch (NoSuchFieldException ignored) {
            // Older Cacio variants allocate on demand.
        }

        screenBufferField.set(screen, newBuffer);
        screenDimension.setSize(width, height);
        System.out.println(
                "SINGLEPLAYER_WIDESCREEN: CACIO_SCREEN_RESIZED "
                        + width + "x" + height);
    }

    private static void resizeRt4Frame(int width, int height) {
        if (GameShell.frame != null) {
            Insets insets = GameShell.frame.getInsets();
            GameShell.frame.setSize(
                    width + insets.left + insets.right,
                    height + insets.top + insets.bottom);
            GameShell.frame.validate();
        }

        // Keep RT4's HD/resizable bookkeeping aligned with the new backing
        // screen. configureFrame() handles the canvas and interface relayout.
        GlRenderer.canvasWidth = width;
        GlRenderer.canvasHeight = height;
        GameShell.configureFrame();
        System.out.println(
                "SINGLEPLAYER_WIDESCREEN: RT4_FRAME_RESIZED frame="
                        + GameShell.frameWidth + "x" + GameShell.frameHeight
                        + " canvas=" + GameShell.canvasWidth + "x" + GameShell.canvasHeight);
    }

    private static void resizeGlfwWindowBestEffort(int width, int height) {
        if (!GlRenderer.enabled) return;
        try {
            Field windowField = GlRenderer.class.getDeclaredField("LWJGLwindow");
            windowField.setAccessible(true);
            long window = windowField.getLong(null);
            if (window == 0L) return;

            Class<?> glfw = Class.forName("org.lwjgl.glfw.GLFW");
            Method setWindowSize = glfw.getMethod(
                    "glfwSetWindowSize", long.class, int.class, int.class);
            setWindowSize.invoke(null, window, width, height);
            System.out.println(
                    "SINGLEPLAYER_WIDESCREEN: GLFW_RESIZED "
                            + width + "x" + height);
        } catch (Throwable failure) {
            // Cacio/RT4 resizing is independently useful. Do not roll back or
            // destabilize a playable client if a particular GLFW build differs.
            System.err.println(
                    "SINGLEPLAYER_WIDESCREEN: GLFW_RESIZE_SKIPPED "
                            + failure.getClass().getSimpleName()
                            + ": " + failure.getMessage());
        }
    }

    private static Class<?> firstClass(String... names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException failure) {
                last = failure;
            }
        }
        throw last == null ? new ClassNotFoundException() : last;
    }

    private static Method declaredMethod(Class<?> type, String... names)
            throws NoSuchMethodException {
        NoSuchMethodException last = null;
        for (String name : names) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException failure) {
                last = failure;
            }
        }
        throw last == null ? new NoSuchMethodException() : last;
    }

    private static void writeReady(int width, int height) {
        String home = System.getProperty("clientHomeOverride", "").trim();
        if (home.isEmpty()) return;
        File marker = new File(home, "singleplayer-widescreen-ready.txt");
        try (FileWriter writer = new FileWriter(marker, false)) {
            writer.write(width + "x" + height);
            writer.write(System.lineSeparator());
        } catch (Exception failure) {
            System.err.println(
                    "SINGLEPLAYER_WIDESCREEN: marker write failed: " + failure);
        }
    }

    private static void logFailure(Throwable failure) {
        System.err.println(
                "SINGLEPLAYER_WIDESCREEN: FAILED "
                        + failure.getClass().getSimpleName()
                        + ": " + failure.getMessage());
        failure.printStackTrace(System.err);
    }
}
