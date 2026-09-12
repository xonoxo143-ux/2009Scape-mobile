package rt4;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import singleplayer.MobileGestureBridge;

/** Retained software widget pixels and the real Android-gesture plugin path. */
public final class SoftwareViewportRegression {
    private static final int WIDTH = 1289;
    private static final int HEIGHT = 503;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void setApplied(String name, int value) throws Exception {
        Field field = LocalViewportBridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setInt(null, value);
    }

    private static Component panel(int x, int y, int width, int height, int color) {
        Component component = new Component();
        component.overlayer = -1;
        component.type = 3;
        component.x = x;
        component.y = y;
        component.width = width;
        component.height = height;
        component.color = color;
        component.filled = true;
        return component;
    }

    private static void checkOverlayPixels() throws Exception {
        Method prepare = null;
        try {
            prepare = LocalViewportBridge.class.getDeclaredMethod("prepareSoftwareInterfaceRedraw");
            prepare.setAccessible(true);
        } catch (NoSuchMethodException oldBuild) {
            // The preceding payload has no software overlay invalidation hook.
            // Let its actual widget pixels demonstrate the regression below.
        }
        int[] pixels = new int[WIDTH * HEIGHT];
        SoftwareRaster.setSize(pixels, WIDTH, HEIGHT);
        Component[] panels = {
            panel(0, HEIGHT - 100, 400, 100, 0x00ff00),
            panel(WIDTH - 180, 100, 180, 300, 0xff00ff)
        };
        InterfaceList.topLevelInterface = 746;
        for (int frame = 0; frame < 3; frame++) {
            // The 3D world covers the resizable root, including the HUD's area.
            SoftwareRaster.setClip(0, 0, WIDTH, HEIGHT);
            SoftwareRaster.fillRect(0, 0, WIDTH, HEIGHT, 0x123456);
            Arrays.fill(InterfaceList.aBooleanArray100, false);
            Arrays.fill(InterfaceList.aBooleanArray116, false);
            InterfaceList.rectangles = panels.length;
            if (prepare != null) prepare.invoke(null);
            // LoginManager transfers pending invalidations at the start of draw.
            for (int i = 0; i < InterfaceList.rectangles; i++) {
                InterfaceList.aBooleanArray116[i] = InterfaceList.aBooleanArray100[i];
                InterfaceList.aBooleanArray100[i] = false;
            }
            InterfaceList.rectangles = 0;
            Cs1ScriptRunner.renderComponent(0, 0, 0, panels, WIDTH, -1, 0, HEIGHT, -1);
            require(pixels[(HEIGHT - 1) * WIDTH] == 0x00ff00,
                    "Resizable chat overlay was erased by world redraw");
            require(pixels[200 * WIDTH + WIDTH - 1] == 0xff00ff,
                    "Resizable side panel was erased by world redraw");
        }
        Arrays.fill(InterfaceList.aBooleanArray100, false);
        client.gameState = 25;
        if (prepare != null) prepare.invoke(null);
        require(!InterfaceList.aBooleanArray100[0], "Overlay invalidation ran during map loading");
        client.gameState = 30;
        GlRenderer.enabled = true;
        if (prepare != null) prepare.invoke(null);
        require(!InterfaceList.aBooleanArray100[0], "Software hook changed the GL path");
        GlRenderer.enabled = false;
        System.out.println("Retained software HUD pixel checks passed");
    }

    private static void checkTouch() {
        MobileTouchControls.plugin plugin = new MobileTouchControls.plugin();
        // Prevent an unrelated League overlay from claiming the test gesture.
        InterfaceList.topLevelInterface = -1;
        MobileGestureBridge.clear();
        MobileGestureBridge.receive(MobileGestureBridge.TAP, WIDTH - 1, HEIGHT - 1, 0, 0);
        plugin.ComponentDraw(-1, null, 0, 0);
        require(Mouse.eventMouseDownX == WIDTH - 1 && Mouse.eventMouseDownY == HEIGHT - 1,
                "Right-edge tap was clamped to the old fixed viewport: "
                        + Mouse.eventMouseDownX + "," + Mouse.eventMouseDownY);
        MobileGestureBridge.receive(MobileGestureBridge.TAP, WIDTH + 100, HEIGHT + 100, 0, 0);
        plugin.ComponentDraw(-1, null, 0, 0);
        require(Mouse.eventMouseDownX == WIDTH - 1 && Mouse.eventMouseDownY == HEIGHT - 1,
                "Out-of-bounds tap escaped the actual canvas");
        GameShell.canvasWidth = 765;
        GameShell.canvasHeight = 503;
        MobileGestureBridge.receive(MobileGestureBridge.TAP, 1000, 600, 0, 0);
        plugin.ComponentDraw(-1, null, 0, 0);
        require(Mouse.eventMouseDownX == 764 && Mouse.eventMouseDownY == 502,
                "Touch plugin stopped respecting the current canvas after a size change");
        System.out.println("Actual mobile gesture-to-mouse coordinate checks passed");
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("singleplayer", "true");
        setApplied("appliedWidth", WIDTH);
        setApplied("appliedHeight", HEIGHT);
        GameShell.canvasWidth = WIDTH;
        GameShell.canvasHeight = HEIGHT;
        GlRenderer.enabled = false;
        client.gameState = 30;
        if (args.length == 0 || !"touch".equals(args[0])) checkOverlayPixels();
        if (args.length == 0 || !"hud".equals(args[0])) checkTouch();
    }
}
