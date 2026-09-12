package rt4;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Frame;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.Point;
import com.github.caciocavallosilano.cacio.ctc.CTCScreen;

/** Runs against the exact Cacio 17 screen implementation shipped in the APK. */
public final class ViewportGeometryRegression {
    private static final int WIDTH = 1289;
    private static final int HEIGHT = 503;

    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static void checkCorners() {
        SoftwareRaster.fillRect(0, 0, WIDTH, HEIGHT, 0x123456);
        SoftwareRaster.fillRect(0, 0, 8, 8, 0xff0000);
        SoftwareRaster.fillRect(WIDTH - 8, 0, 8, 8, 0x00ff00);
        SoftwareRaster.fillRect(0, HEIGHT - 8, 8, 8, 0x0000ff);
        SoftwareRaster.fillRect(WIDTH - 8, HEIGHT - 8, 8, 8, 0xff00ff);
        Graphics graphics = GameShell.canvas.getGraphics();
        try {
            SoftwareRaster.frameBuffer.draw(graphics);
        } finally {
            graphics.dispose();
        }
        int[] screen = CTCScreen.getCurrentScreenRGB();
        require((screen[0] & 0xffffff) == 0xff0000, "Top-left canvas corner is shifted");
        require((screen[WIDTH - 1] & 0xffffff) == 0x00ff00, "Top-right canvas corner is clipped");
        require((screen[(HEIGHT - 1) * WIDTH] & 0xffffff) == 0x0000ff,
                "Bottom-left canvas corner is shifted");
        require((screen[HEIGHT * WIDTH - 1] & 0xffffff) == 0xff00ff,
                "Bottom-right canvas corner is clipped");
    }

    private static void runChecks() {
        System.setProperty("singleplayer", "true");
        System.setProperty("singleplayer.viewportWidth", Integer.toString(WIDTH));
        System.setProperty("singleplayer.viewportHeight", Integer.toString(HEIGHT));
        GlRenderer.enabled = false;
        client.gameState = 30;
        InterfaceList.topLevelInterface = -1;
        GameShell.frame = new Frame();
        GameShell.frame.setUndecorated(true);
        GameShell.frame.setLayout(null);
        GameShell.frame.setBackground(Color.BLACK);
        GameShell.frame.setSize(765, 503);
        GameShell.frame.setVisible(true);
        GameShell.frame.setLocationRelativeTo(null);
        GameShell.canvas = new Canvas();
        GameShell.frame.add(GameShell.canvas);
        GameShell.canvas.setBounds(0, 0, 765, 503);
        GameShell.frameWidth = GameShell.canvasWidth = 765;
        GameShell.frameHeight = GameShell.canvasHeight = 503;

        require(GameShell.frame.getX() == 262, "Legacy centering setup did not reproduce the phone offset");
        require(LocalViewportBridge.ensureConfiguredViewport(), "Viewport preparation failed");
        require(GameShell.canvas.getLocationOnScreen().equals(new Point(0, 0)),
                "Wide canvas retained the legacy frame offset: " + GameShell.canvas.getLocationOnScreen());
        require(GameShell.frame.getWidth() == WIDTH && GameShell.frame.getHeight() == HEIGHT,
                "Frame dimensions do not match the Cacio screen");
        checkCorners();

        // An unchanged cached size must not hide a later move of the outer frame.
        GameShell.frame.setLocation(262, 0);
        require(LocalViewportBridge.ensureConfiguredViewport(), "Viewport drift repair failed");
        require(GameShell.canvas.getLocationOnScreen().equals(new Point(0, 0)),
                "Fast path ignored outer-window movement");
        checkCorners();
        require(!GlRenderer.enabled, "Geometry correction changed renderer");
        System.out.println("Cacio viewport geometry and four-corner pixel checks passed");
    }

    public static void main(String[] args) {
        int result = 0;
        try {
            EventQueue.invokeAndWait(ViewportGeometryRegression::runChecks);
        } catch (Throwable failure) {
            failure.printStackTrace();
            result = 1;
        } finally {
            if (GameShell.frame != null) GameShell.frame.dispose();
        }
        System.exit(result);
    }
}
