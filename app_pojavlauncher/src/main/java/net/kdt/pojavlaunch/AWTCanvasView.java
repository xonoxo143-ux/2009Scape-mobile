package net.kdt.pojavlaunch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import net.kdt.pojavlaunch.utils.JREUtils;

public class AWTCanvasView extends TextureView implements TextureView.SurfaceTextureListener, Runnable {
    private static final int BASE_CANVAS_WIDTH = 765;
    private static final int BASE_CANVAS_HEIGHT = 503;

    /**
     * RT4's fixed-mode dimensions remain our minimum logical viewport. On wide
     * mobile displays we expand the logical canvas instead of aspect-fitting the
     * old 765x503 image into black bars or stretching it.
     *
     * These values are deliberately frozen exactly once before the child JVM is
     * started. Cacio, RT4/GLFW, touch translation and this renderer must all see
     * the same dimensions for the entire runtime.
     */
    public static volatile int AWT_CANVAS_WIDTH = BASE_CANVAS_WIDTH;
    public static volatile int AWT_CANVAS_HEIGHT = BASE_CANVAS_HEIGHT;
    private static volatile boolean sViewportFrozen;

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
    }

    /**
     * Freeze the logical RT4 viewport from an Android display/window size.
     * First call wins for the lifetime of the process. Surface callbacks are
     * consumers of this state; they are never allowed to resize the game later.
     */
    public static synchronized void freezeLogicalViewport(int viewWidth, int viewHeight) {
        if (sViewportFrozen) return;

        viewWidth = Math.max(1, viewWidth);
        viewHeight = Math.max(1, viewHeight);
        float aspect = (float) viewWidth / (float) viewHeight;
        float baseAspect = (float) BASE_CANVAS_WIDTH / (float) BASE_CANVAS_HEIGHT;

        if (aspect >= baseAspect) {
            AWT_CANVAS_HEIGHT = BASE_CANVAS_HEIGHT;
            AWT_CANVAS_WIDTH = Math.max(
                    BASE_CANVAS_WIDTH,
                    Math.round(BASE_CANVAS_HEIGHT * aspect));
        } else {
            AWT_CANVAS_WIDTH = BASE_CANVAS_WIDTH;
            AWT_CANVAS_HEIGHT = Math.max(
                    BASE_CANVAS_HEIGHT,
                    Math.round(BASE_CANVAS_WIDTH / Math.max(0.01f, aspect)));
        }
        sViewportFrozen = true;
    }

    public static boolean isLogicalViewportFrozen() {
        return sViewportFrozen;
    }

    /** Defensive fallback for a surface created outside the normal launcher path. */
    private static void ensureLogicalViewportFrozen(Context context) {
        if (sViewportFrozen) return;
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        freezeLogicalViewport(metrics.widthPixels, metrics.heightPixels);
    }

    /** Apply the already-frozen logical size to the Android TextureView. */
    public void applyFrozenViewport() {
        ensureLogicalViewportFrozen(getContext());
        refreshSize();
        SurfaceTexture texture = getSurfaceTexture();
        if (texture != null) {
            texture.setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
        ensureLogicalViewportFrozen(getContext());
        texture.setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
        mIsDestroyed = false;
        new Thread(this, "AndroidAWTRenderer").start();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        mIsDestroyed = true;
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {
        // Android may resize/re-layout the view, but the running Cacio/RT4
        // framebuffer is immutable. Reapply the frozen buffer rather than
        // deriving a new game size from this callback.
        texture.setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        // Intentionally no sizing work here. This callback fires continuously.
    }

    @Override
    public void run() {
        ensureLogicalViewportFrozen(getContext());
        final int canvasWidth = AWT_CANVAS_WIDTH;
        final int canvasHeight = AWT_CANVAS_HEIGHT;
        Canvas canvas;
        Surface surface = new Surface(getSurfaceTexture());
        Bitmap rgbArrayBitmap = Bitmap.createBitmap(
                canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        int[] rgbArray = new int[canvasWidth * canvasHeight];
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setDither(false);
        paint.setFilterBitmap(false);
        long frameEndNanos;
        long frameStartNanos;
        long sleepTime;
        long sleepMillis;
        int sleepNanos;
        final long frameTimeNanos = (long) (NANOS / 60);
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
                if (JREUtils.renderAWTScreenFrameInto(rgbArray)) {
                    rgbArrayBitmap.setPixels(
                            rgbArray,
                            0,
                            canvasWidth,
                            0,
                            0,
                            canvasWidth,
                            canvasHeight);
                    canvas.drawBitmap(rgbArrayBitmap, 0, 0, paint);
                } else {
                    canvas.drawRGB(0, 0, 0);
                }
                surface.unlockCanvasAndPost(canvas);

                frameEndNanos = System.nanoTime();
                frameDuration = frameEndNanos - frameStartNanos;
                if (frameDuration < frameTimeNanos) {
                    sleepTime = frameTimeNanos - frameDuration;
                    sleepMillis = sleepTime / 1000000;
                    sleepNanos = (int) (sleepTime - sleepMillis * 1000000);
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

    /** Keep the renderer surface full-screen; the logical buffer matches it. */
    private void refreshSize() {
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams == null) return;
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        setLayoutParams(layoutParams);
    }
}
