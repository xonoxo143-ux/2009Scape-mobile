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
    // These are deliberately the immutable Cacio/JVM bootstrap dimensions.
    // Runtime presentation can expand after GAME_READY without changing them.
    public static final int AWT_CANVAS_WIDTH = 765;
    public static final int AWT_CANVAS_HEIGHT = 503;
    private static final double NANOS = 1000000000.0;

    private volatile int mLogicalWidth = AWT_CANVAS_WIDTH;
    private volatile int mLogicalHeight = AWT_CANVAS_HEIGHT;
    private volatile boolean mRuntimeViewportApplied = false;
    private volatile boolean mIsDestroyed = false;
    private volatile boolean mRenderingPaused = false;
    private final Object mRenderPauseLock = new Object();

    public AWTCanvasView(Context ctx) {
        this(ctx, null);
    }

    public AWTCanvasView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setSurfaceTextureListener(this);
        post(this::refreshSize);
    }

    public int getLogicalWidth() {
        return mLogicalWidth;
    }

    public int getLogicalHeight() {
        return mLogicalHeight;
    }

    /**
     * Called only after the embedded JVM confirms that Cacio and RT4 have both
     * expanded their backing stores. Bootstrap remains 765x503 regardless of
     * the target passed here.
     */
    public void applyRuntimeViewport(int width, int height) {
        width = Math.max(AWT_CANVAS_WIDTH, width);
        height = Math.max(AWT_CANVAS_HEIGHT, height);
        mLogicalWidth = width;
        mLogicalHeight = height;
        mRuntimeViewportApplied = true;
        refreshSize();

        SurfaceTexture texture = getSurfaceTexture();
        if (texture != null) {
            texture.setDefaultBufferSize(width, height);
        }
        requestLayout();
        invalidate();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
        texture.setDefaultBufferSize(mLogicalWidth, mLogicalHeight);
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
        texture.setDefaultBufferSize(mLogicalWidth, mLogicalHeight);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        // No sizing work here. This callback fires for every presented frame.
    }

    @Override
    public void run() {
        Canvas canvas;
        Surface surface = new Surface(getSurfaceTexture());
        Bitmap rgbArrayBitmap = null;
        int[] rgbArray = null;
        int activeWidth = -1;
        int activeHeight = -1;

        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setDither(false);
        paint.setFilterBitmap(false);
        long frameEndNanos;
        long frameStartNanos;
        long sleepTime;
        long sleepMillis;
        int sleepNanos;
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

                int width = mLogicalWidth;
                int height = mLogicalHeight;
                if (width != activeWidth || height != activeHeight) {
                    if (rgbArrayBitmap != null) {
                        rgbArrayBitmap.recycle();
                    }
                    activeWidth = width;
                    activeHeight = height;
                    rgbArrayBitmap = Bitmap.createBitmap(
                            activeWidth,
                            activeHeight,
                            Bitmap.Config.ARGB_8888);
                    rgbArray = new int[activeWidth * activeHeight];
                }

                frameStartNanos = System.nanoTime();
                canvas = surface.lockCanvas(null);
                if (canvas == null) {
                    Thread.yield();
                    continue;
                }
                try {
                    if (JREUtils.renderAWTScreenFrameInto(rgbArray)) {
                        rgbArrayBitmap.setPixels(
                                rgbArray,
                                0,
                                activeWidth,
                                0,
                                0,
                                activeWidth,
                                activeHeight);
                        canvas.drawBitmap(rgbArrayBitmap, 0, 0, paint);
                    } else {
                        canvas.drawRGB(0, 0, 0);
                    }
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }

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
        if (rgbArrayBitmap != null) {
            rgbArrayBitmap.recycle();
        }
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

    /**
     * Before GAME_READY, preserve the exact known-good 765x503 aspect-fit path.
     * After the JVM confirms its backing store has expanded, fill Android's
     * content area because the logical framebuffer now has the same aspect.
     */
    private void refreshSize(){
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams == null) return;

        if (mRuntimeViewportApplied) {
            layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else if(getHeight() < getWidth()){
            layoutParams.width = AWT_CANVAS_WIDTH * getHeight() / AWT_CANVAS_HEIGHT;
        }else{
            layoutParams.height = AWT_CANVAS_HEIGHT * getWidth() / AWT_CANVAS_WIDTH;
        }

        setLayoutParams(layoutParams);
    }
}
