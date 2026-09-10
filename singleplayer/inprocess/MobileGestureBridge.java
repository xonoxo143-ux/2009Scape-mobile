package singleplayer;

import java.util.ArrayDeque;

/**
 * Process-local bridge between Android touch recognition and the RT4 client.
 *
 * Android/JNI calls receive(). The RT4 single-player touch plugin drains the
 * queue on the client render thread, so Android never mutates game state.
 */
public final class MobileGestureBridge {
    public static final int TAP = 2000;
    public static final int LONG_PRESS = 2001;
    public static final int DRAG_BEGIN = 2002;
    public static final int DRAG_MOVE = 2003;
    public static final int DRAG_END = 2004;
    public static final int PINCH = 2005;
    public static final int CANCEL = 2006;

    private static final int MAX_EVENTS = 256;
    private static final ArrayDeque<Event> EVENTS = new ArrayDeque<>(MAX_EVENTS);

    private MobileGestureBridge() {}

    public static void receive(int type, int x, int y, int value1, int value2) {
        synchronized (EVENTS) {
            if (EVENTS.size() >= MAX_EVENTS) {
                EVENTS.removeFirst();
            }
            EVENTS.addLast(new Event(type, x, y, value1, value2));
        }
    }

    public static Event poll() {
        synchronized (EVENTS) {
            return EVENTS.pollFirst();
        }
    }

    public static void clear() {
        synchronized (EVENTS) {
            EVENTS.clear();
        }
    }

    public static final class Event {
        public final int type;
        public final int x;
        public final int y;
        public final int value1;
        public final int value2;

        private Event(int type, int x, int y, int value1, int value2) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.value1 = value1;
            this.value2 = value2;
        }
    }
}
