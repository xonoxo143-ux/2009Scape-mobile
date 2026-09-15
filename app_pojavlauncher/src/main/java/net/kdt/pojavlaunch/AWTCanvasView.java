package net.kdt.pojavlaunch;

import android.content.*;
import android.graphics.*;
import android.text.*;
import android.util.*;
import android.view.*;

import java.util.*;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.*;

public class AWTCanvasView extends TextureView implements TextureView.SurfaceTextureListener {
    public static final int MIN_CANVAS_WIDTH = 765;
    public static final int MIN_CANVAS_HEIGHT = 503;

    // Frozen once, synchronously, before the game JVM starts. RT4, Cacio,
    // rendering and touch all consume the same immutable-for-process geometry.
    public static volatile int AWT_CANVAS_WIDTH = MIN_CANVAS_WIDTH;
    public static volatile int AWT_CANVAS_HEIGHT = MIN_CANVAS_HEIGHT;
    private static volatile boolean sViewportFrozen = false;

    private static final double NANOS = 1000000000.0;
    private volatile int mSurfaceGeneration;
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
        final int generation = ++mSurfaceGeneration;
        new Thread(() -> renderSurface(texture, generation), "AndroidAWTRenderer").start();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        ++mSurfaceGeneration;
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

    private void renderSurface(SurfaceTexture texture, int generation) {
        Surface surface = null;
        Bitmap rgbArrayBitmap = null;
        final int logicalWidth = AWT_CANVAS_WIDTH;
        final int logicalHeight = AWT_CANVAS_HEIGHT;
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setDither(false);
        paint.setFilterBitmap(false);
        int[] rgbArray = null;
        Rect destination = new Rect();
        final long frameTimeNanos = (long)(NANOS / 60);
        long previousSequence = 0;
        boolean directAnnounced = false;

        try {
            // Capture this surface, rather than looking up a possibly newer one
            // after this renderer thread has been scheduled.
            surface = new Surface(texture);
            rgbArrayBitmap = Bitmap.createBitmap(
                    logicalWidth, logicalHeight, Bitmap.Config.ARGB_8888);
            while (generation == mSurfaceGeneration && surface.isValid()
                    && !Thread.currentThread().isInterrupted()) {
                if (mRenderingPaused) {
                    synchronized (mRenderPauseLock) {
                        while (mRenderingPaused && generation == mSurfaceGeneration) {
                            mRenderPauseLock.wait(250L);
                        }
                    }
                    continue;
                }

                long frameStartNanos = System.nanoTime();
                long sequence = JREUtils.renderRT4Frame(rgbArrayBitmap, previousSequence);
                if (sequence <= 0 || sequence != previousSequence) {
                    boolean havePixels = sequence > 0;
                    if (!havePixels) {
                        // Startup and older payloads still use the proven Cacio
                        // path. The direct path never crosses an Android int[].
                        previousSequence = 0;
                        if (rgbArray == null) rgbArray = new int[logicalWidth * logicalHeight];
                        havePixels = JREUtils.renderAWTScreenFrameInto(rgbArray);
                        if (havePixels) rgbArrayBitmap.setPixels(rgbArray, 0,
                                logicalWidth, 0, 0, logicalWidth, logicalHeight);
                    }
                    if (generation != mSurfaceGeneration || mRenderingPaused) continue;
                    Canvas canvas = surface.lockCanvas(null);
                    if (canvas != null) {
                        try {
                            if (havePixels) {
                                destination.set(0, 0, canvas.getWidth(), canvas.getHeight());
                                canvas.drawBitmap(rgbArrayBitmap, null, destination, paint);
                            } else {
                                canvas.drawRGB(0, 0, 0);
                            }
                        } finally {
                            surface.unlockCanvasAndPost(canvas);
                        }
                        previousSequence = sequence;
                        if (sequence > 0 && !directAnnounced) {
                            directAnnounced = true;
                            Log.i("SINGLEPLAYER_FRAME", "DIRECT_ANDROID_BITMAP "
                                    + logicalWidth + "x" + logicalHeight);
                        }
                    }
                }
                long sleepTime = frameTimeNanos - (System.nanoTime() - frameStartNanos);
                if (sleepTime > 0) Thread.sleep(sleepTime / 1000000, (int)(sleepTime % 1000000));
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable throwable) {
            if (generation == mSurfaceGeneration) Tools.showError(getContext(), throwable);
        } finally {
            JREUtils.releaseAWTRenderer();
            if (rgbArrayBitmap != null) rgbArrayBitmap.recycle();
            if (surface != null) surface.release();
        }
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
