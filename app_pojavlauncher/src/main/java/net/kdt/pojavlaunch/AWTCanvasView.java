package net.kdt.pojavlaunch;

import android.content.*;
import android.graphics.*;
import android.text.*;
import android.util.*;
import android.view.*;

import java.util.*;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.*;

public class AWTCanvasView extends TextureView implements TextureView.SurfaceTextureListener, Runnable {
    public static final int MIN_CANVAS_WIDTH = 765;
    public static final int MIN_CANVAS_HEIGHT = 503;

    // Frozen once, synchronously, before the game JVM starts. RT4, Cacio,
    // rendering and touch all consume the same immutable-for-process geometry.
    public static volatile int AWT_CANVAS_WIDTH = MIN_CANVAS_WIDTH;
    public static volatile int AWT_CANVAS_HEIGHT = MIN_CANVAS_HEIGHT;
    private static volatile boolean sViewportFrozen = false;

    private static final double NANOS = 1000000000.0;
    private volatile boolean mIsDestroyed = false;
    private volatile boolean mRenderingPaused = false;
    private final Object mRenderPauseLock = new Object();

    public AWTCanvasView(Context ctx) {
        this(ctx, null);
    }

    public AWTCanvasView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setSurfaceTextureListener(this);
        post(this::applyFrozenViewport);
    }

    /**
     * Convert the physical window aspect ratio into an RT4 logical framebuffer.
     * The historical 765x503 viewport remains the minimum in either dimension;
     * wider/taller devices receive additional world pixels instead of stretching.
     * First call wins so surface/layout callbacks cannot race JVM startup.
     */
    public static synchronized void freezeLogicalViewport(int physicalWidth, int physicalHeight) {
        if (sViewportFrozen) return;

        int width = Math.max(1, physicalWidth);
        int height = Math.max(1, physicalHeight);
        double aspect = (double) width / (double) height;
        double legacyAspect = (double) MIN_CANVAS_WIDTH / (double) MIN_CANVAS_HEIGHT;

        if (aspect >= legacyAspect) {
            AWT_CANVAS_HEIGHT = MIN_CANVAS_HEIGHT;
            AWT_CANVAS_WIDTH = Math.max(
                    MIN_CANVAS_WIDTH,
                    (int) Math.round(MIN_CANVAS_HEIGHT * aspect));
        } else {
            AWT_CANVAS_WIDTH = MIN_CANVAS_WIDTH;
            AWT_CANVAS_HEIGHT = Math.max(
                    MIN_CANVAS_HEIGHT,
                    (int) Math.round(MIN_CANVAS_WIDTH / aspect));
        }
        sViewportFrozen = true;
    }

    public static boolean isLogicalViewportFrozen() {
        return sViewportFrozen;
    }

    public void applyFrozenViewport() {
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams != null) {
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
            setLayoutParams(layoutParams);
        }
        SurfaceTexture texture = getSurfaceTexture();
        if (texture != null) {
            texture.setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
        texture.setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
        mIsDestroyed = false;
        new Thread(this, "AndroidAWTRenderer").start();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        mIsDestroyed = true;
        synchronized (mRenderPauseLock) {
            mRenderPauseLock.notifyAll();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {
        texture.setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        // Geometry is deliberately frozen before JVM startup. Do not recalculate
        // or resize from a presentation callback.
    }

    @Override
    public void run() {
        Canvas canvas;
        Surface surface = new Surface(getSurfaceTexture());
        final int logicalWidth = AWT_CANVAS_WIDTH;
        final int logicalHeight = AWT_CANVAS_HEIGHT;
        Bitmap rgbArrayBitmap = Bitmap.createBitmap(
                logicalWidth, logicalHeight, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setDither(false);
        paint.setFilterBitmap(false);
        long frameEndNanos;
        long frameStartNanos;
        long sleepTime;
        long sleepMillis;
        int sleepNanos;
        final int[] rgbArray = new int[logicalWidth * logicalHeight];
        final long frameTimeNanos = (long)(NANOS / 60);
        long frameDuration;

        try {
            while (!mIsDestroyed && surface.isValid()) {
                if (mRenderingPaused) {
                    synchronized (mRenderPauseLock) {
                        while (mRenderingPaused && !mIsDestroyed) {
                            try {
                                mRenderPauseLock.wait(250L);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                mIsDestroyed = true;
                                break;
                            }
                        }
                    }
                    continue;
                }

                frameStartNanos = System.nanoTime();
                canvas = surface.lockCanvas(null);
                if (canvas == null) continue;
                if (JREUtils.renderAWTScreenFrameInto(rgbArray)) {
                    rgbArrayBitmap.setPixels(
                            rgbArray,
                            0,
                            logicalWidth,
                            0,
                            0,
                            logicalWidth,
                            logicalHeight);
                    canvas.drawBitmap(rgbArrayBitmap, null,
                            new Rect(0, 0, canvas.getWidth(), canvas.getHeight()), paint);
                } else {
                    canvas.drawRGB(0, 0, 0);
                }
                surface.unlockCanvasAndPost(canvas);

                frameEndNanos = System.nanoTime();
                frameDuration = frameEndNanos - frameStartNanos;
                if (frameDuration < frameTimeNanos) {
                    sleepTime = frameTimeNanos - frameDuration;
                    sleepMillis = sleepTime / 1000000;
                    sleepNanos = (int)(sleepTime - sleepMillis * 1000000);
                    try {
                        Thread.sleep(sleepMillis, sleepNanos);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        } catch (Throwable throwable) {
            Tools.showError(getContext(), throwable);
        }
        rgbArrayBitmap.recycle();
        surface.release();
    }

    public void setRenderingPaused(boolean paused) {
        mRenderingPaused = paused;
        if (!paused) {
            synchronized (mRenderPauseLock) {
                mRenderPauseLock.notifyAll();
            }
        }
    }
}
