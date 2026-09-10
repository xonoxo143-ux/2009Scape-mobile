package MobileTouchControls;

import plugin.Plugin;
import plugin.annotations.PluginMeta;
import plugin.api.API;
import rt4.Component;
import rt4.Cs1ScriptRunner;
import rt4.HookRequest;
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
    private int scrollHookRemainder;
    private boolean mouseHeld;
    private boolean pendingMouseRelease;
    private int pendingReleaseX;
    private int pendingReleaseY;
    private boolean announcedTap;
    private boolean announcedLongPress;
    private boolean announcedCameraDrag;
    private boolean announcedDragEnd;
    private boolean announcedCancel;

    @Override
    public void Draw(long timeDelta) {
        // A normal mouse drag release is deliberately delayed until the next
        // rendered frame. That guarantees Mouse.loop() observes at least one
        // held-button client loop even when Android delivers a very quick drag.
        flushPendingMouseRelease();

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

        boolean inventory = component.type == 2;
        boolean genericDraggable =
                component.onDrag != null
                        || component.onDragStart != null
                        || component.onDragRelease != null;
        boolean draggable = inventory || genericDraggable;

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
                inventory,
                genericDraggable,
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
                announceOnce("TAP");
                break;

            case MobileGestureBridge.LONG_PRESS:
                endHeldMouseIfNecessary(event.x, event.y);
                injectClick(event.x, event.y, true);
                announceOnce("LONG_PRESS");
                break;

            case MobileGestureBridge.DRAG_BEGIN:
                beginDrag(event.x, event.y);
                if (dragMode == DragMode.CAMERA) {
                    announceOnce("DRAG_BEGIN:CAMERA");
                }
                break;

            case MobileGestureBridge.DRAG_MOVE:
                moveDrag(event.x, event.y, event.value1, event.value2);
                break;

            case MobileGestureBridge.DRAG_END:
                endDrag(event.x, event.y);
                announceOnce("DRAG_END");
                break;

            case MobileGestureBridge.PINCH:
                endHeldMouseIfNecessary(event.x, event.y);
                applyPinch(event.value1);
                break;

            case MobileGestureBridge.CANCEL:
                cancelDrag(event.x, event.y);
                announceOnce("CANCEL");
                break;

            default:
                break;
        }
    }

    private void beginDrag(int x, int y) {
        cancelDrag(x, y);

        HitRegion hit = chooseHitRegion(x, y);
        if (hit != null) {
            if (hit.hasDraggableTargetAt(x, y)) {
                dragMode = DragMode.MOUSE;
                beginMouseDrag(x, y);
                return;
            }
            if (hit.scrollable) {
                dragMode = DragMode.SCROLL;
                scrollComponent = hit.component;
                scrollHookRemainder = 0;
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

                    if (scrollComponent.onScroll != null) {
                        scrollHookRemainder += -dy;
                        while (Math.abs(scrollHookRemainder) >= 45) {
                            int wheelStep = scrollHookRemainder > 0 ? 1 : -1;
                            HookRequest request = new HookRequest();
                            request.aBoolean158 = true;
                            request.source = scrollComponent;
                            request.mouseY = wheelStep;
                            request.arguments = scrollComponent.onScroll;
                            InterfaceList.lowPriorityRequests.addTail(request);
                            scrollHookRemainder -= wheelStep * 45;
                        }
                    }
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
        scrollHookRemainder = 0;
    }

    private void cancelDrag(int x, int y) {
        if (mouseHeld || pendingMouseRelease) {
            cancelMouseDragImmediately(x, y);
        }
        dragMode = DragMode.NONE;
        scrollComponent = null;
        scrollHookRemainder = 0;
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
            if (region.hasDraggableTargetAt(x, y)) {
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
        if (!mouseHeld) {
            return;
        }
        pendingMouseRelease = true;
        pendingReleaseX = x;
        pendingReleaseY = y;
        mouseHeld = false;
    }

    private void flushPendingMouseRelease() {
        if (!pendingMouseRelease) {
            return;
        }

        Mouse instance = Mouse.instance;
        if (instance != null) {
            synchronized (instance) {
                Mouse.idleLoops = 0;
                Mouse.eventMouseX = clamp(pendingReleaseX, 0, CLIENT_WIDTH - 1);
                Mouse.eventMouseY = clamp(pendingReleaseY, 0, CLIENT_HEIGHT - 1);
                Mouse.eventAction = 0;
            }
        }
        pendingMouseRelease = false;
    }

    private void cancelMouseDragImmediately(int x, int y) {
        Mouse instance = Mouse.instance;
        if (instance != null) {
            synchronized (instance) {
                Mouse.idleLoops = 0;
                Mouse.eventMouseX = clamp(x, 0, CLIENT_WIDTH - 1);
                Mouse.eventMouseY = clamp(y, 0, CLIENT_HEIGHT - 1);
                Mouse.eventAction = 0;
                // If Mouse.loop() has not consumed the press yet, suppress the
                // one-shot button as well so CANCEL cannot turn into a click.
                Mouse.eventButton = 0;
            }
        }
        mouseHeld = false;
        pendingMouseRelease = false;
    }

    private void endHeldMouseIfNecessary(int x, int y) {
        if (mouseHeld) {
            endMouseDrag(x, y);
        }
        dragMode = DragMode.NONE;
        scrollComponent = null;
        scrollHookRemainder = 0;
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

    private void announceOnce(String event) {
        if ("TAP".equals(event)) {
            if (announcedTap) return;
            announcedTap = true;
        } else if ("LONG_PRESS".equals(event)) {
            if (announcedLongPress) return;
            announcedLongPress = true;
        } else if ("DRAG_BEGIN:CAMERA".equals(event)) {
            if (announcedCameraDrag) return;
            announcedCameraDrag = true;
        } else if ("DRAG_END".equals(event)) {
            if (announcedDragEnd) return;
            announcedDragEnd = true;
        } else if ("CANCEL".equals(event)) {
            if (announcedCancel) return;
            announcedCancel = true;
        }
        System.out.println("SINGLEPLAYER_TOUCH: " + event);
    }

    @Override
    public void OnLogout() {
        cancelDrag(Mouse.lastMouseX, Mouse.lastMouseY);
        MobileGestureBridge.clear();
        announcedTap = false;
        announcedLongPress = false;
        announcedCameraDrag = false;
        announcedDragEnd = false;
        announcedCancel = false;
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
        final boolean inventory;
        final boolean genericDraggable;
        final boolean scrollable;

        HitRegion(
                Component component,
                int x,
                int y,
                int width,
                int height,
                boolean inventory,
                boolean genericDraggable,
                boolean scrollable) {
            this.component = component;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.inventory = inventory;
            this.genericDraggable = genericDraggable;
            this.scrollable = scrollable;
        }

        boolean contains(int px, int py) {
            return px >= x && py >= y && px < x + width && py < y + height;
        }

        boolean hasDraggableTargetAt(int px, int py) {
            if (genericDraggable) {
                return true;
            }
            if (!inventory || component.objTypes == null
                    || component.baseWidth <= 0 || component.baseHeight <= 0) {
                return false;
            }

            int cellWidth = 32 + component.invMarginX;
            int cellHeight = 32 + component.invMarginY;
            if (cellWidth <= 0 || cellHeight <= 0) {
                return false;
            }

            int localX = px - x;
            int localY = py - y;
            if (localX < 0 || localY < 0) {
                return false;
            }

            int column = localX / cellWidth;
            int row = localY / cellHeight;
            if (column < 0 || column >= component.baseWidth
                    || row < 0 || row >= component.baseHeight) {
                return false;
            }

            int slot = row * component.baseWidth + column;
            return slot >= 0
                    && slot < component.objTypes.length
                    && component.objTypes[slot] > 0;
        }
    }
}
