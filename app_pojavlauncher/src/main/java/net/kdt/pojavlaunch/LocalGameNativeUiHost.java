package net.kdt.pojavlaunch;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * Tiny lifecycle host placed in the launcher layout.
 * The actual native game UI is overlaid on android.R.id.content so it never
 * participates in or changes the RT4 TextureView's measured geometry.
 */
public final class LocalGameNativeUiHost extends FrameLayout {
    private LocalGameNativeUiController controller;

    public LocalGameNativeUiHost(Context context) {
        super(context);
        init();
    }

    public LocalGameNativeUiHost(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public LocalGameNativeUiHost(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(true);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (controller != null) return;
        Context context = getContext();
        if (!(context instanceof Activity)) return;
        // Post until setContentView has attached the whole hierarchy. This keeps
        // the native overlay completely outside RT4 measurement/layout.
        post(() -> {
            if (isAttachedToWindow() && controller == null) {
                controller = new LocalGameNativeUiController((Activity) context);
            }
        });
    }

    @Override
    protected void onDetachedFromWindow() {
        if (controller != null) {
            controller.close();
            controller = null;
        }
        super.onDetachedFromWindow();
    }
}
