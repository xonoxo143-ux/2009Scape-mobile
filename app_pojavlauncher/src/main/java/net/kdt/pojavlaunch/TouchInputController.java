package net.kdt.pojavlaunch;

import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * Single touch recognizer for the 2009Scape canvas.
 *
 * Recognition happens on Android; semantic routing happens inside RT4.
 * A gesture is permanently classified once it leaves PRESS_PENDING, so a
 * camera drag/pinch can never accidentally become a click on release.
 */
public final class TouchInputController implements View.OnTouchListener {
    private enum State {
        IDLE,
        PRESS_PENDING,
        LONG_PRESS,
        DRAG,
        PINCH
    }

    private static final int INVALID_POINTER = -1;

    private final AWTCanvasView canvas;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final int touchSlopSquared;
    private final long longPressTimeoutMs;

    private State state = State.IDLE;
    private int activePointerId = INVALID_POINTER;
    private float downX;
    private float downY;
    private float lastX;
    private float lastY;
    private float lastPinchSpan;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (state != State.PRESS_PENDING || activePointerId == INVALID_POINTER) {
                return;
            }
            state = State.LONG_PRESS;
            send(
                    AWTInputBridge.GESTURE_LONG_PRESS,
                    toClientX(downX),
                    toClientY(downY),
                    0,
                    0);
        }
    };

    public TouchInputController(AWTCanvasView canvas) {
        this.canvas = canvas;
        ViewConfiguration configuration = ViewConfiguration.get(canvas.getContext());
        int touchSlop = configuration.getScaledTouchSlop();
        touchSlopSquared = touchSlop * touchSlop;
        longPressTimeoutMs = ViewConfiguration.getLongPressTimeout();
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                beginPress(event);
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                beginPinch(event);
                return true;

            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                return true;

            case MotionEvent.ACTION_POINTER_UP:
                handlePointerUp(event);
                return true;

            case MotionEvent.ACTION_UP:
                finishGesture(event);
                return true;

            case MotionEvent.ACTION_CANCEL:
                cancelGesture();
                return true;

            default:
                return true;
        }
    }

    public void cancel() {
        cancelGesture();
    }

    private void beginPress(MotionEvent event) {
        cancelLongPress();
        state = State.PRESS_PENDING;
        activePointerId = event.getPointerId(0);
        downX = lastX = event.getX(0);
        downY = lastY = event.getY(0);
        handler.postDelayed(longPressRunnable, longPressTimeoutMs);
    }

    private void beginPinch(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return;
        }

        cancelLongPress();
        if (state == State.DRAG) {
            send(
                    AWTInputBridge.GESTURE_CANCEL,
                    toClientX(lastX),
                    toClientY(lastY),
                    0,
                    0);
        }

        state = State.PINCH;
        activePointerId = INVALID_POINTER;
        lastPinchSpan = pointerSpan(event);
    }

    private void handleMove(MotionEvent event) {
        if (state == State.PINCH) {
            if (event.getPointerCount() >= 2) {
                float span = pointerSpan(event);
                float spanDelta = span - lastPinchSpan;
                lastPinchSpan = span;

                if (Math.abs(spanDelta) >= 0.5f) {
                    float centerX = (event.getX(0) + event.getX(1)) * 0.5f;
                    float centerY = (event.getY(0) + event.getY(1)) * 0.5f;
                    int scaledSpanDelta = Math.round(
                            spanDelta * AWTCanvasView.AWT_CANVAS_WIDTH
                                    / Math.max(1f, canvas.getWidth()));
                    if (scaledSpanDelta != 0) {
                        send(
                                AWTInputBridge.GESTURE_PINCH,
                                toClientX(centerX),
                                toClientY(centerY),
                                scaledSpanDelta,
                                0);
                    }
                }
            }
            return;
        }

        if (activePointerId == INVALID_POINTER) {
            return;
        }

        int pointerIndex = event.findPointerIndex(activePointerId);
        if (pointerIndex < 0) {
            cancelGesture();
            return;
        }

        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        if (state == State.PRESS_PENDING) {
            float dxFromDown = x - downX;
            float dyFromDown = y - downY;
            if (dxFromDown * dxFromDown + dyFromDown * dyFromDown
                    > touchSlopSquared) {
                cancelLongPress();
                state = State.DRAG;
                send(
                        AWTInputBridge.GESTURE_DRAG_BEGIN,
                        toClientX(downX),
                        toClientY(downY),
                        0,
                        0);
            }
        }

        if (state == State.DRAG) {
            int dx = toClientDeltaX(x - lastX);
            int dy = toClientDeltaY(y - lastY);
            if (dx != 0 || dy != 0) {
                send(
                        AWTInputBridge.GESTURE_DRAG_MOVE,
                        toClientX(x),
                        toClientY(y),
                        dx,
                        dy);
            }
        }

        lastX = x;
        lastY = y;
    }

    private void handlePointerUp(MotionEvent event) {
        if (state == State.PINCH) {
            // Pinch owns the entire physical touch sequence. Do not fall back to
            // a one-finger tap/drag when one of the two fingers is released.
            return;
        }

        int liftedPointerId = event.getPointerId(event.getActionIndex());
        if (liftedPointerId == activePointerId) {
            finishGestureAt(lastX, lastY);
        }
    }

    private void finishGesture(MotionEvent event) {
        if (state == State.PINCH) {
            reset();
            return;
        }

        int pointerIndex = activePointerId == INVALID_POINTER
                ? -1
                : event.findPointerIndex(activePointerId);
        float x = pointerIndex >= 0 ? event.getX(pointerIndex) : lastX;
        float y = pointerIndex >= 0 ? event.getY(pointerIndex) : lastY;
        finishGestureAt(x, y);
    }

    private void finishGestureAt(float x, float y) {
        cancelLongPress();

        if (state == State.PRESS_PENDING) {
            send(
                    AWTInputBridge.GESTURE_TAP,
                    toClientX(x),
                    toClientY(y),
                    0,
                    0);
        } else if (state == State.DRAG) {
            send(
                    AWTInputBridge.GESTURE_DRAG_END,
                    toClientX(x),
                    toClientY(y),
                    0,
                    0);
        }
        // LONG_PRESS deliberately emits nothing on release. The RuneScape
        // context menu remains open for a subsequent normal tap.

        reset();
    }

    private void cancelGesture() {
        cancelLongPress();

        if (state == State.DRAG || state == State.LONG_PRESS) {
            send(
                    AWTInputBridge.GESTURE_CANCEL,
                    toClientX(lastX),
                    toClientY(lastY),
                    0,
                    0);
        }

        reset();
    }

    private void reset() {
        state = State.IDLE;
        activePointerId = INVALID_POINTER;
        lastPinchSpan = 0f;
    }

    private void cancelLongPress() {
        handler.removeCallbacks(longPressRunnable);
    }

    private float pointerSpan(MotionEvent event) {
        if (event.getPointerCount() < 2) {
            return 0f;
        }
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return (float) Math.hypot(dx, dy);
    }

    private int toClientX(float x) {
        return clamp(
                Math.round(x * AWTCanvasView.AWT_CANVAS_WIDTH
                        / Math.max(1f, canvas.getWidth())),
                0,
                AWTCanvasView.AWT_CANVAS_WIDTH - 1);
    }

    private int toClientY(float y) {
        return clamp(
                Math.round(y * AWTCanvasView.AWT_CANVAS_HEIGHT
                        / Math.max(1f, canvas.getHeight())),
                0,
                AWTCanvasView.AWT_CANVAS_HEIGHT - 1);
    }

    private int toClientDeltaX(float dx) {
        return Math.round(
                dx * AWTCanvasView.AWT_CANVAS_WIDTH
                        / Math.max(1f, canvas.getWidth()));
    }

    private int toClientDeltaY(float dy) {
        return Math.round(
                dy * AWTCanvasView.AWT_CANVAS_HEIGHT
                        / Math.max(1f, canvas.getHeight()));
    }

    private void send(int type, int x, int y, int value1, int value2) {
        AWTInputBridge.sendMobileGesture(type, x, y, value1, value2);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
