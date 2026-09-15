package rt4;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** Exercises the retained framebuffer implementations, not a replacement renderer. */
public final class DirectFrameBufferRegression {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static FrameBuffer buffer(boolean producer, int width, int height) {
        FrameBuffer buffer = producer ? new ImageProducerFrameBuffer() : new BufferedImageFrameBuffer();
        buffer.init(height, width, new Canvas());
        return buffer;
    }

    private static void presentation(boolean producer) {
        final int width = 1289, height = 503;
        FrameBuffer frame = buffer(producer, width, height);
        BufferedImage screen = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics graphics = screen.getGraphics();
        try {
            Arrays.fill(frame.pixels, 0x123456);
            frame.pixels[width * height - 1] = 0x987654;
            DirectFrameBridge.disable();
            frame.draw(graphics);
            check((screen.getRGB(width - 1, height - 1) & 0xffffff) == 0x987654,
                    "Older shell must retain the full AWT frame");

            check(DirectFrameBridge.acquireFrame(width, height) == null, "Arming cannot invent a frame");
            frame.pixels[0] = 0x00ff00;
            frame.draw(graphics);
            check((screen.getRGB(0, 0) & 0xffffff) == 0x123456, "Direct presentation must skip AWT blit");
            synchronized (DirectFrameBridge.class) {
                int[] completed = DirectFrameBridge.acquireFrame(width, height);
                check(completed != null && completed.length == width * height, "No RT4 sentinel pixel in snapshot");
                check(completed[0] == 0x00ff00 && completed[completed.length - 1] == 0x987654,
                        "Both viewport corners survive");
                frame.pixels[0] = 0xff0000;
                check(completed[0] == 0x00ff00, "Live raster edits must not tear the completed snapshot");
                frame.drawAt(1, 0, 1, graphics, 0);
                check(DirectFrameBridge.acquireFrame(width, height) == completed, "Reuse snapshot storage");
                check(completed[0] == 0xff0000 && completed[completed.length - 1] == 0x987654,
                        "Partial blit still exposes the complete frame and HUD area");
            }
            long oldSequence = DirectFrameBridge.frameSequence();
            client.gameState = 25;
            check(DirectFrameBridge.acquireFrame(width, height) == null, "Rebuild cannot display a stale direct frame");
            frame.draw(graphics);
            check((screen.getRGB(0, 0) & 0xffffff) == 0xff0000, "Rebuild must use AWT");
            client.gameState = 30;
            frame.draw(graphics);
            check(DirectFrameBridge.frameSequence() > oldSequence, "Resume sequence must advance");

            DirectFrameBridge.acquireFrame(765, 503);
            frame.draw(graphics);
            check(DirectFrameBridge.acquireFrame(765, 503) == null, "Mismatched viewport must fall back");
            check(!DirectFrameBridge.publish(new int[2], 765, 503, true), "Reject truncated raster");
            check(!DirectFrameBridge.publish(new int[2], Integer.MAX_VALUE, 2, true), "Reject size overflow");
            DirectFrameBridge.disable();
        } finally {
            graphics.dispose();
        }
    }

    private static void concurrentSnapshots() throws Exception {
        final int width = 321, height = 127;
        DirectFrameBridge.acquireFrame(width, height);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread producer = new Thread(() -> {
            try {
                int[] raster = new int[width * height + 1];
                for (int id = 1; id <= 2000; id++) {
                    Arrays.fill(raster, id);
                    check(DirectFrameBridge.publish(raster, width, height, true), "Frame accepted");
                }
            } catch (Throwable error) { failure.set(error); }
        });
        producer.start();
        do {
            // Exactly the monitor lifetime required by the native bitmap copy.
            synchronized (DirectFrameBridge.class) {
                int[] snapshot = DirectFrameBridge.acquireFrame(width, height);
                if (snapshot != null) {
                    int expected = snapshot[0];
                    for (int pixel : snapshot) check(pixel == expected, "Mixed frame under concurrent read");
                }
            }
            Thread.yield();
        } while (producer.isAlive());
        producer.join();
        if (failure.get() != null) throw new AssertionError(failure.get());
        DirectFrameBridge.disable();
    }

    public static void main(String[] args) throws Exception {
        // Acquiring before any RT4 initialization is deliberately supported.
        check(DirectFrameBridge.acquireFrame(765, 503) == null, "Pre-bootstrap acquire");
        DirectFrameBridge.disable();
        System.setProperty("singleplayer", "true");
        client.gameState = 30;
        GlRenderer.enabled = false;
        presentation(false);
        presentation(true);
        concurrentSnapshots();
        System.out.println("DIRECT_FRAME_REGRESSION: PASS (fallback, viewport, partial blits, snapshot isolation, concurrency)");
    }
}
