package MobileTouchControls;

import plugin.Plugin;
import plugin.annotations.PluginMeta;
import plugin.api.API;
import rt4.Component;
import rt4.Cs1ScriptRunner;
import rt4.InterfaceList;
import rt4.MonotonicClock;
import rt4.Mouse;
import singleplayer.MobileGestureBridge;

import java.util.ArrayList;
import java.util.List;

@PluginMeta(
        author = "2009Scape Mobile Single Player",
        description = "Native Android-style touch controls for the RT4 client.",
        version = 1.0
)
public class plugin extends Plugin {
    private enum DragMode {
        NONE,
        CAMERA,
        SCROLL,
        MOUSE
    }

    private static final int CLIENT_WIDTH = 765;
    private static final int CLIENT_HEIGHT = 503;

    private List<HitRegion> collectingRegions = new ArrayList<HitRegion>(256);
    private List<HitRegion> stableRegions = new ArrayList<HitRegion>(256);

    private DragMode dragMode = DragMode.NONE;
    private Component scrollComponent;
    private boolean mouseHeld;
    private boolean announcedReady;

    @Override
    public void Draw(long timeDelta) {
        // ComponentDraw callbacks collected between two Draw callbacks represent
        // the previous rendered frame. Swap buffers so routing uses a stable,
        // complete hit map while the next frame is being collected.
        List<HitRegion> oldStable = stableRegions;
        stableRegions = collectingRegions;
        collectingRegions = oldStable;
        collectingRegions.clear();

        drainGestures();
    }

    @Override
    public void ComponentDraw(
            int componentIndex,
            Component component,
            int screenX,
            int screenY) {
        if (component == null || component.hidden) {
            return;
        }

        int width;
        int height;
        if (component.type == 2) {
            width = component.baseWidth <= 0
                    ? 0
                    : component.baseWidth * 32
                            + Math.max(0, component.baseWidth - 1) * component.invMarginX;
            height = component.baseHeight <= 0
                    ? 0
                    : component.baseHeight * 32
                            + Math.max(0, component.baseHeight - 1) * component.invMarginY;
        } else {
            width = component.width;
            height = component.height;
        }

        if (width <= 0 || height <= 0) {
            return;
        }

        boolean draggable =
                component.type == 2
                        || component.onDrag != null
                        || component.onDragStart != null
                        || component.onDragRelease != null;

        boolean scrollable =
                component.scrollMaxV > component.height && component.height > 0;

        boolean blocking =
                draggable
                        || scrollable
                        || component.noClickThrough
                        || component.buttonType != 0
                        || component.clientCode != 0
                        || component.ops != null
                        || component.onClickRepeat != null
                        || component.onHold != null
                        || component.onOptionClick != null;

        if (!blocking) {
            return;
        }

        collectingRegions.add(new HitRegion(
                component,
                screenX,
                screenY,
                width,
                height,
                draggable,
                scrollable));
    }

    private void drainGestures() {
        MobileGestureBridge.Event event;
        while ((event = MobileGestureBridge.poll()) != null) {
            handle(event);
        }
    }

    private void handle(MobileGestureBridge.Event event) {
        switch (event.type) {
            case MobileGestureBridge.TAP:
                endHeldMouseIfNecessary(event.x, event.y);
                injectClick(event.x, event.y, false);
                announce("TAP");
                break;

            case MobileGestureBridge.LONG_PRESS:
                endHeldMouseIfNecessary(event.x, event.y);
                injectClick(event.x, event.y, true);
                announce("LONG_PRESS");
                break;

            case MobileGestureBridge.DRAG_BEGIN:
                beginDrag(event.x, event.y);
                announce("DRAG_BEGIN:" + dragMode.name());
                break;

            case MobileGestureBridge.DRAG_MOVE:
                moveDrag(event.x, event.y, event.value1, event.value2);
                break;

            case MobileGestureBridge.DRAG_END:
                endDrag(event.x, event.y);
                announce("DRAG_END");
                break;

            case MobileGestureBridge.PINCH:
                endHeldMouseIfNecessary(event.x, event.y);
                applyPinch(event.value1);
                announceOnceReady();
                break;

            case MobileGestureBridge.CANCEL:
                cancelDrag(event.x, event.y);
                announce("CANCEL");
                break;

            default:
                break;
        }
    }

    private void beginDrag(int x, int y) {
        cancelDrag(x, y);

        HitRegion hit = chooseHitRegion(x, y);
        if (hit != null) {
            if (hit.draggable) {
                dragMode = DragMode.MOUSE;
                beginMouseDrag(x, y);
                return;
            }
            if (hit.scrollable) {
                dragMode = DragMode.SCROLL;
                scrollComponent = hit.component;
                moveMouse(x, y);
                return;
            }

            // Any other interactive/blocking interface owns the gesture. Preserve
            // normal RuneScape drag semantics rather than rotating the camera
            // through the interface.
            dragMode = DragMode.MOUSE;
            beginMouseDrag(x, y);
            return;
        }

        if (isInsideWorldViewport(x, y)) {
            dragMode = DragMode.CAMERA;
            moveMouse(x, y);
        } else {
            dragMode = DragMode.MOUSE;
            beginMouseDrag(x, y);
        }
    }

    private void moveDrag(int x, int y, int dx, int dy) {
        switch (dragMode) {
            case CAMERA:
                // Match the direction of the old camera-mouse implementation,
                // but apply the delta directly instead of synthesizing arrow keys.
                API.UpdateCameraYaw(-dx * 2.0D);
                API.UpdateCameraPitch(dy * 2.0D);
                moveMouse(x, y);
                break;

            case SCROLL:
                if (scrollComponent != null) {
                    int max = Math.max(0, scrollComponent.scrollMaxV - scrollComponent.height);
                    int target = scrollComponent.scrollY - dy;
                    scrollComponent.scrollY = clamp(target, 0, max);
                    InterfaceList.redraw(scrollComponent);
                }
                moveMouse(x, y);
                break;

            case MOUSE:
                continueMouseDrag(x, y);
                break;

            default:
                break;
        }
    }

    private void endDrag(int x, int y) {
        if (dragMode == DragMode.MOUSE) {
            endMouseDrag(x, y);
        } else {
            moveMouse(x, y);
        }
        dragMode = DragMode.NONE;
        scrollComponent = null;
    }

    private void cancelDrag(int x, int y) {
        if (mouseHeld) {
            endMouseDrag(x, y);
        }
        dragMode = DragMode.NONE;
        scrollComponent = null;
    }

    private void applyPinch(int spanDelta) {
        if (spanDelta == 0) {
            return;
        }

        // Fingers spreading means zoom in (smaller camera distance).
        int current = API.GetCameraZoom();
        int target = current - spanDelta * 4;
        API.SetCameraZoom(clamp(target, 1, 2000));
    }

    private HitRegion chooseHitRegion(int x, int y) {
        HitRegion scrollCandidate = null;
        HitRegion blockingCandidate = null;

        // Later-drawn components are considered on top.
        for (int i = stableRegions.size() - 1; i >= 0; i--) {
            HitRegion region = stableRegions.get(i);
            if (!region.contains(x, y)) {
                continue;
            }
            if (region.draggable) {
                return region;
            }
            if (scrollCandidate == null && region.scrollable) {
                scrollCandidate = region;
            }
            if (blockingCandidate == null) {
                blockingCandidate = region;
            }
        }

        return scrollCandidate != null ? scrollCandidate : blockingCandidate;
    }

    private boolean isInsideWorldViewport(int x, int y) {
        Component viewport = InterfaceList.aClass13_26;
        int left = Cs1ScriptRunner.anInt2503;
        int top = InterfaceList.anInt5574;

        if (viewport == null || left < 0 || top < 0) {
            return false;
        }

        return x >= left
                && y >= top
                && x < left + viewport.width
                && y < top + viewport.height;
    }

    private void injectClick(int x, int y, boolean rightClick) {
        Mouse instance = Mouse.instance;
        if (instance == null) {
            return;
        }

        synchronized (instance) {
            Mouse.idleLoops = 0;
            Mouse.eventMouseX = clamp(x, 0, CLIENT_WIDTH - 1);
            Mouse.eventMouseY = clamp(y, 0, CLIENT_HEIGHT - 1);
            Mouse.eventMouseDownX = Mouse.eventMouseX;
            Mouse.eventMouseDownY = Mouse.eventMouseY;
            Mouse.eventTime = MonotonicClock.currentTimeMillis();
            Mouse.eventButton = rightClick ? 2 : 1;

            // A tap is a one-shot click, not a held button. Mouse.loop() consumes
            // eventButton on the next client loop while eventAction remains up.
            Mouse.eventAction = 0;
        }
    }

    private void beginMouseDrag(int x, int y) {
        Mouse instance = Mouse.instance;
        if (instance == null) {
            return;
        }

        synchronized (instance) {
            Mouse.idleLoops = 0;
            Mouse.eventMouseX = clamp(x, 0, CLIENT_WIDTH - 1);
            Mouse.eventMouseY = clamp(y, 0, CLIENT_HEIGHT - 1);
            Mouse.eventMouseDownX = Mouse.eventMouseX;
            Mouse.eventMouseDownY = Mouse.eventMouseY;
            Mouse.eventTime = MonotonicClock.currentTimeMillis();
            Mouse.eventButton = 1;
            Mouse.eventAction = 1;
            mouseHeld = true;
        }
    }

    private void continueMouseDrag(int x, int y) {
        Mouse instance = Mouse.instance;
        if (instance == null || !mouseHeld) {
            return;
        }

        synchronized (instance) {
            Mouse.idleLoops = 0;
            Mouse.eventMouseX = clamp(x, 0, CLIENT_WIDTH - 1);
            Mouse.eventMouseY = clamp(y, 0, CLIENT_HEIGHT - 1);
            Mouse.eventAction = 1;
        }
    }

    private void endMouseDrag(int x, int y) {
        Mouse instance = Mouse.instance;
        if (instance == null) {
            mouseHeld = false;
            return;
        }

        synchronized (instance) {
            Mouse.idleLoops = 0;
            Mouse.eventMouseX = clamp(x, 0, CLIENT_WIDTH - 1);
            Mouse.eventMouseY = clamp(y, 0, CLIENT_HEIGHT - 1);
            Mouse.eventAction = 0;
            mouseHeld = false;
        }
    }

    private void endHeldMouseIfNecessary(int x, int y) {
        if (mouseHeld) {
            endMouseDrag(x, y);
        }
        dragMode = DragMode.NONE;
        scrollComponent = null;
    }

    private void moveMouse(int x, int y) {
        Mouse instance = Mouse.instance;
        if (instance == null) {
            return;
        }

        synchronized (instance) {
            Mouse.idleLoops = 0;
            Mouse.eventMouseX = clamp(x, 0, CLIENT_WIDTH - 1);
            Mouse.eventMouseY = clamp(y, 0, CLIENT_HEIGHT - 1);
        }
    }

    private void announce(String event) {
        System.out.println("SINGLEPLAYER_TOUCH: " + event);
    }

    private void announceOnceReady() {
        if (!announcedReady) {
            announcedReady = true;
            System.out.println("SINGLEPLAYER_TOUCH: BRIDGE_READY");
        }
    }

    @Override
    public void OnLogout() {
        cancelDrag(Mouse.lastMouseX, Mouse.lastMouseY);
        MobileGestureBridge.clear();
        announcedReady = false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class HitRegion {
        final Component component;
        final int x;
        final int y;
        final int width;
        final int height;
        final boolean draggable;
        final boolean scrollable;

        HitRegion(
                Component component,
                int x,
                int y,
                int width,
                int height,
                boolean draggable,
                boolean scrollable) {
            this.component = component;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.draggable = draggable;
            this.scrollable = scrollable;
        }

        boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }
    }
}
