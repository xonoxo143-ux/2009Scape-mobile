package rt4;

import java.awt.Canvas;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Exercises the retained display sender with a recording local world endpoint. */
public final class ViewportLayoutRegression {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void set(String name, Object value) throws Exception {
        Field field = LocalViewportBridge.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("singleplayer", "true");
        GlRenderer.enabled = false;
        DisplayMode.resizable = false;
        set("appliedWidth", 1289);
        set("appliedHeight", 503);
        GameShell.canvasWidth = 1289;
        GameShell.canvasHeight = 503;

        // A wide software canvas must never send the fixed world layout again.
        ClientProt.sendWindowDetails();
        require(LocalClientCommands.mode == 2, "Wide software display sent fixed layout");
        require(LocalClientCommands.width == 1289 && LocalClientCommands.height == 503,
                "Display notification did not preserve the input/render dimensions");
        require(!GlRenderer.enabled, "Layout change switched renderer");

        Method synchronize = LocalViewportBridge.class.getDeclaredMethod(
                "synchronizeWorldLayout", int.class, int.class);
        synchronize.setAccessible(true);
        LocalClientCommands.calls = 0;
        InterfaceList.topLevelInterface = 548;
        client.gameState = 25;
        synchronize.invoke(null, 1289, 503);
        require(LocalClientCommands.calls == 0, "Layout command ran during map loading");

        client.gameState = 30;
        synchronize.invoke(null, 1289, 503);
        require(LocalClientCommands.calls == 1 && LocalClientCommands.mode == 2,
                "Playable fixed root did not request the resizable world layout");

        GameShell.canvas = new Canvas();
        GameShell.canvas.setSize(1289, 503);
        InterfaceList.topLevelInterface = 746;
        for (int i = 0; i < 5; i++) synchronize.invoke(null, 1289, 503);
        require(LocalClientCommands.calls == 1, "Confirmed layout kept sending commands");

        InterfaceList.topLevelInterface = 100;
        synchronize.invoke(null, 1289, 503);
        require(LocalClientCommands.calls == 1, "Unrelated top-level interface was replaced");

        InterfaceList.topLevelInterface = 548;
        LocalClientCommands.calls = 0;
        LocalClientCommands.accept = false;
        for (int i = 0; i < 6; i++) {
            set("nextLayoutAttemptMs", 0L);
            synchronize.invoke(null, 1289, 503);
        }
        require(LocalClientCommands.calls == 3, "Failed layout retries were not bounded");

        set("appliedWidth", 765);
        set("appliedHeight", 503);
        require(LocalViewportBridge.presentationWindowMode(0) == 0,
                "Legacy-size presentation was forced into widescreen");
        System.clearProperty("singleplayer");
        set("appliedWidth", 1289);
        require(LocalViewportBridge.presentationWindowMode(1) == 1,
                "Non-single-player display behavior changed");
        System.out.println("Viewport layout regression checks passed");
    }
}

// Only the command destination is replaced: ClientProt, LocalViewportBridge,
// DisplayMode, and the other retained RT4 classes come from the real build.
final class LocalClientCommands {
    static int calls;
    static int mode;
    static int width;
    static int height;
    static boolean accept = true;

    public static boolean trackingDisplay(int windowMode, int w, int h, int detail) {
        calls++;
        mode = windowMode;
        width = w;
        height = h;
        return accept;
    }
}
