package net.kdt.pojavlaunch.customcontrols.keyboard;

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.utils.KeyEncoder;

/**
 * This class is intended for sending characters used in chat via the virtual keyboard.
 */
public class TouchCharInput extends androidx.appcompat.widget.AppCompatEditText {
    public interface PreviewListener {
        void onPreviewChanged(String text, boolean keyboardActive);
    }

    public static final String TEXT_FILLER = "                              ";
    public static boolean softKeyboardIsActive = false;

    private boolean mIsDoingInternalChanges = false;
    private CharacterSenderStrategy mCharacterSender;
    private PreviewListener mPreviewListener;
    private final StringBuilder mPreviewText = new StringBuilder();

    public TouchCharInput(@NonNull Context context) {
        this(context, null);
    }

    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.editTextStyle);
    }

    public TouchCharInput(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    /**
     * Mirror IME edits into the game and into a lightweight Android preview.
     * The hidden EditText keeps its historical filler so keyboard composition
     * behavior remains unchanged; only the user's actual characters are shown.
     */
    @Override
    protected void onTextChanged(CharSequence text, int start, int before, int count) {
        super.onTextChanged(text, start, before, count);
        if (mIsDoingInternalChanges) {
            return;
        }

        Log.i("TouchCharInput", "New Event (before/after)!: " + before + " : " + count);

        if (before > 0) {
            int removals = Math.min(before, mPreviewText.length());
            for (int i = 0; i < removals; i++) {
                KeyEncoder.sendUnicodeBackspace();
                mPreviewText.deleteCharAt(mPreviewText.length() - 1);
            }
        }

        if (count > 0) {
            int end = Math.min(text.length(), start + count);
            for (int i = Math.max(0, start); i < end; i++) {
                char c = text.charAt(i);
                Log.i("TouchCharInput", "New Event!: " + c);
                if (mCharacterSender != null) {
                    KeyEncoder.sendEncodedChar(c, c);
                }
                mPreviewText.append(c);
            }
        }

        notifyPreview();
    }

    /**
     * When we change from app to app, the keyboard gets disabled.
     * So, we disable the object.
     */
    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (!hasWindowFocus) {
            disable();
        }
    }

    /** Intercepts the back key to disable focus. */
    @Override
    public boolean onKeyPreIme(final int keyCode, final KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && event.getAction() == KeyEvent.ACTION_UP) {
            disable();
        }
        return super.onKeyPreIme(keyCode, event);
    }

    /** Toggle the soft keyboard, depending on the state. */
    public void switchKeyboardState() {
        InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(INPUT_METHOD_SERVICE);
        if (hasFocus()) {
            clear();
            disable();
        } else {
            enable();
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    /**
     * Clear hidden IME filler and visible preview. It does not alter previously
     * submitted in-game chat text.
     */
    @SuppressLint("SetTextI18n")
    public void clear() {
        mIsDoingInternalChanges = true;
        setText(TEXT_FILLER);
        setSelection(TEXT_FILLER.length());
        mPreviewText.setLength(0);
        mIsDoingInternalChanges = false;
        notifyPreview();
    }

    /** Regain ability to take focus and receive text input. */
    public void enable() {
        softKeyboardIsActive = true;
        setEnabled(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setVisibility(VISIBLE);
        requestFocus();
        notifyPreview();
    }

    /** Lose ability to take focus and receive text input. */
    public void disable() {
        softKeyboardIsActive = false;
        clear();
        setVisibility(GONE);
        clearFocus();
        setEnabled(false);
        notifyPreview();
    }

    /** Send the enter key. */
    private void sendEnter() {
        if (mCharacterSender != null) {
            mCharacterSender.sendEnter();
        }
        clear();
    }

    public void setCharacterSender(CharacterSenderStrategy characterSender) {
        mCharacterSender = characterSender;
    }

    public void setPreviewListener(PreviewListener previewListener) {
        mPreviewListener = previewListener;
        notifyPreview();
    }

    private void notifyPreview() {
        if (mPreviewListener != null) {
            mPreviewListener.onPreviewChanged(mPreviewText.toString(), softKeyboardIsActive);
        }
    }

    private void setup() {
        setOnEditorActionListener((textView, actionId, keyEvent) -> {
            sendEnter();
            disable();
            return false;
        });
        clear();
        disable();
    }
}
