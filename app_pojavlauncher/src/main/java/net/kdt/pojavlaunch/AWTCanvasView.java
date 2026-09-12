package net.kdt.pojavlaunch;

import android.content.*;
import android.graphics.*;
import android.text.*;
import android.util.*;
import android.view.*;

import java.io.*;
import java.util.*;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.*;

public class AWTCanvasView extends TextureView implements TextureView.SurfaceTextureListener, Runnable {
    // These are deliberately the immutable Cacio/JVM bootstrap dimensions.
    // Runtime presentation can expand after GAME_READY without changing them.
    public static final int AWT_CANVAS_WIDTH = 765;
    public static final int AWT_CANVAS_HEIGHT = 503;
    private static final String WIDESCREEN_TARGET_FILE = "singleplayer-widescreen-target.txt";
    private static final String WIDESCREEN_READY_FILE = "singleplayer-widescreen-ready.txt";
    private static final double NANOS = 1000000000.0;

    private volatile int mLogicalWidth = AWT_CANVAS_WIDTH;
    private volatile int mLogicalHeight = AWT_CANVAS_HEIGHT;
    private volatile boolean mRuntimeViewportApplied = false;
    private volatile boolean mIsDestroyed = false;
    private volatile boolean mRenderingPaused = false;
    private final Object mRenderPauseLock = new Object();

    private final Runnable mRuntimeViewportPoll = new Runnable() {
        @Override
        public void run() {
            if (mRuntimeViewportApplied) return;
            File ready = dataFile(WIDESCREEN_READY_FILE);
            if (ready != null && ready.isFile()) {
                int[] dimensions = readDimensions(ready);
                if (dimensions != null) {
                    applyRuntimeViewport(dimensions[0], dimensions[1]);
                    Log.i(
                            "SinglePlayerViewport",
                            "Applied post-ready viewport "
                                    + dimensions[0] + "x" + dimensions[1]);
                    return;
                }
            }
            postDelayed(this, 200L);
        }
    };

    public AWTCanvasView(Context ctx) {
        this(ctx, null);
    }

    public AWTCanvasView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setSurfaceTextureListener(this);
        prepareRuntimeViewportTarget();
        post(this::refreshSize);
        postDelayed(mRuntimeViewportPoll, 200L);
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
    protected void onDetachedFromWindow() {
        removeCallbacks(mRuntimeViewportPoll);
        super.onDetachedFromWindow();
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

    /**
     * Publish a passive target for the JVM-side bridge. This file does not alter
     * Cacio's startup screen and therefore cannot participate in world startup.
     */
    private void prepareRuntimeViewportTarget() {
        try {
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int viewWidth = Math.max(1, metrics.widthPixels);
            int viewHeight = Math.max(1, metrics.heightPixels);
            float aspect = (float) viewWidth / (float) viewHeight;
            float bootstrapAspect = (float) AWT_CANVAS_WIDTH / (float) AWT_CANVAS_HEIGHT;

            int targetWidth;
            int targetHeight;
            if (aspect >= bootstrapAspect) {
                targetHeight = AWT_CANVAS_HEIGHT;
                targetWidth = Math.max(
                        AWT_CANVAS_WIDTH,
                        Math.round(targetHeight * aspect));
            } else {
                targetWidth = AWT_CANVAS_WIDTH;
                targetHeight = Math.max(
                        AWT_CANVAS_HEIGHT,
                        Math.round(targetWidth / Math.max(0.01f, aspect)));
            }

            File ready = dataFile(WIDESCREEN_READY_FILE);
            if (ready != null && ready.exists()) {
                ready.delete();
            }
            File target = dataFile(WIDESCREEN_TARGET_FILE);
            if (target != null) {
                try (FileWriter writer = new FileWriter(target, false)) {
                    writer.write(targetWidth + "x" + targetHeight);
                    writer.write(System.lineSeparator());
                }
            }
            Log.i(
                    "SinglePlayerViewport",
                    "Bootstrap 765x503; post-ready target "
                            + targetWidth + "x" + targetHeight
                            + " from Android " + viewWidth + "x" + viewHeight);
        } catch (Throwable failure) {
            Log.w("SinglePlayerViewport", "Unable to prepare widescreen target", failure);
        }
    }

    private static File dataFile(String name) {
        try {
            if (Tools.DIR_DATA == null) return null;
            return new File(Tools.DIR_DATA, name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int[] readDimensions(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line = reader.readLine();
            if (line == null) return null;
            String[] parts = line.trim().toLowerCase(Locale.ROOT).split("x", 2);
            if (parts.length != 2) return null;
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            if (width < AWT_CANVAS_WIDTH || height < AWT_CANVAS_HEIGHT) return null;
            return new int[] {width, height};
        } catch (Throwable ignored) {
            return null;
        }
    }
}
