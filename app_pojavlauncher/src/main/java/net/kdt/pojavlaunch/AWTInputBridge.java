package net.kdt.pojavlaunch;

public class AWTInputBridge {
    public static final int EVENT_TYPE_CHAR = 1000;
    public static final int EVENT_TYPE_CURSOR_POS = 1003;
    public static final int EVENT_TYPE_KEY = 1005;
    public static final int EVENT_TYPE_MOUSE_BUTTON = 1006;

    // High-level single-player touch gestures. These bypass AWT event synthesis
    // and are consumed by singleplayer.MobileGestureBridge inside the Java 17 VM.
    public static final int GESTURE_TAP = 2000;
    public static final int GESTURE_LONG_PRESS = 2001;
    public static final int GESTURE_DRAG_BEGIN = 2002;
    public static final int GESTURE_DRAG_MOVE = 2003;
    public static final int GESTURE_DRAG_END = 2004;
    public static final int GESTURE_PINCH = 2005;
    public static final int GESTURE_CANCEL = 2006;
    
    public static void sendKey(char keychar, int keycode) {
        // TODO: Android -> AWT keycode mapping
        nativeSendData(EVENT_TYPE_KEY, (int) keychar, keycode, 1, 0);
        nativeSendData(EVENT_TYPE_KEY, (int) keychar, keycode, 0, 0);
    }

    public static void sendKey(char keychar, int keycode, int state) {
        // TODO: Android -> AWT keycode mapping
        nativeSendData(EVENT_TYPE_KEY, (int) keychar, keycode, state, 0);
    }

    public static void sendChar(char keychar){
        nativeSendData(EVENT_TYPE_CHAR, (int) keychar, 0, 0, 0);
    }
    
    public static void sendMousePress(int awtButtons, boolean isDown) {
        nativeSendData(EVENT_TYPE_MOUSE_BUTTON, awtButtons, isDown ? 1 : 0, 0, 0);
    }
    
    public static void sendMousePress(int awtButtons) {
        sendMousePress(awtButtons, true);
        sendMousePress(awtButtons, false);
    }
    
    public static void sendMousePos(int x, int y) {
        nativeSendData(EVENT_TYPE_CURSOR_POS, x, y, 0, 0);
    }

    public static void sendMobileGesture(int type, int x, int y, int value1, int value2) {
        nativeSendData(type, x, y, value1, value2);
    }

    public static void setMobileAppPaused(boolean paused) {
        nativeSetMobilePaused(paused);
    }
    
    static {
        System.loadLibrary("pojavexec_awt");
    }
    
    public static native void nativeSendData(int type, int i1, int i2, int i3, int i4);
    public static native void nativeClipboardReceived(String data, String mimeTypeSub);
    public static native void nativeMoveWindow(int xoff, int yoff);
    private static native void nativeSetMobilePaused(boolean paused);
}
