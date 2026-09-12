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
    private static final int BASE_CANVAS_WIDTH = 765;
    private static final int BASE_CANVAS_HEIGHT = 503;

    /**
     * RT4's fixed-mode dimensions remain our minimum logical viewport. On wide
     * mobile displays we expand the logical canvas instead of aspect-fitting the
     * old 765x503 image into black bars or stretching it.
     */
    public static volatile int AWT_CANVAS_WIDTH = BASE_CANVAS_WIDTH;
    public static volatile int AWT_CANVAS_HEIGHT = BASE_CANVAS_HEIGHT;

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
        post(this::configureForCurrentView);
    }

    /**
     * Choose a logical RT4 viewport with the same aspect ratio as the Android
     * game surface while never shrinking below the historical 765x503 canvas.
     * This reveals more world on the extra axis rather than distorting pixels.
     */
    public void configureForCurrentView() {
        int viewWidth = getWidth();
        int viewHeight = getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            viewWidth = Math.max(1, metrics.widthPixels);
            viewHeight = Math.max(1, metrics.heightPixels);
        }

        float aspect = (float) viewWidth / (float) Math.max(1, viewHeight);
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

        refreshSize();
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
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {
        texture.setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        texture.setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
    }

    @Override
    public void run() {
        final int canvasWidth = AWT_CANVAS_WIDTH;
        final int canvasHeight = AWT_CANVAS_HEIGHT;
        Canvas canvas;
        Surface surface = new Surface(getSurfaceTexture());
        Bitmap rgbArrayBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setDither(false);
        paint.setFilterBitmap(false);
        long frameEndNanos;
        long frameStartNanos;
        long sleepTime;
        long sleepMillis;
        int sleepNanos;
        final int[] rgbArray = new int[canvasWidth * canvasHeight];
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

    /** Keep the renderer surface full-screen; the logical buffer now matches it. */
    private void refreshSize(){
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams == null) return;
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        setLayoutParams(layoutParams);
    }

}
