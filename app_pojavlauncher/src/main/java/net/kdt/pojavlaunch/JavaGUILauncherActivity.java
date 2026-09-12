package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.MainActivity.fullyExit;

import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;

import com.kdt.LoggerView;

import net.kdt.pojavlaunch.customcontrols.keyboard.AwtCharSender;
import net.kdt.pojavlaunch.customcontrols.keyboard.TouchCharInput;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JREUtils;
import net.kdt.pojavlaunch.utils.KeyEncoder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JavaGUILauncherActivity extends BaseActivity {

    private static final int CHAT_KEYBOARD_MAX_X = 520;
    private static final int CHAT_KEYBOARD_TOP_FROM_BOTTOM = 55;
    private static final int CHAT_KEYBOARD_BOTTOM_FROM_BOTTOM = 25;
    private static final long EXIT_BACK_WINDOW_MS = 1500L;

    private AWTCanvasView mTextureView;
    private LoggerView mLoggerView;
    private TouchCharInput mTouchCharInput;
    private TouchInputController mTouchInputController;

    private View mSinglePlayerLoadingOverlay;
    private TextView mSinglePlayerLoadingStatus;
    private Handler mSinglePlayerLoadingHandler;
    private Runnable mSinglePlayerLoadingPoll;
    private File mSinglePlayerStageFile;
    private File mSinglePlayerReadyFile;

    private long mLastBackPressMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_java_gui_launcher);

        mSinglePlayerLoadingOverlay = findViewById(R.id.singleplayerLoadingOverlay);
        mSinglePlayerLoadingStatus = findViewById(R.id.singleplayerLoadingStatus);
        mSinglePlayerLoadingHandler = new Handler(Looper.getMainLooper());
        mSinglePlayerStageFile =
                new File(Tools.DIR_DATA, "singleplayer-game-stage.txt");
        mSinglePlayerReadyFile =
                new File(Tools.DIR_DATA, "singleplayer-game-ready.flag");

        if (mSinglePlayerReadyFile.exists()) {
            mSinglePlayerReadyFile.delete();
        }
        if (mSinglePlayerStageFile.exists()) {
            mSinglePlayerStageFile.delete();
        }
        startSinglePlayerLoadingPoll();

        try {
            File latestLogFile = new File(Tools.DIR_GAME_HOME, "latestlog.txt");
            if (!latestLogFile.exists() && !latestLogFile.createNewFile()) {
                throw new IOException("Failed to create a new log file");
            }
            Logger.begin(latestLogFile.getAbsolutePath());
        } catch (IOException e) {
            Tools.showError(this, e, true);
        }

        MainActivity.GLOBAL_CLIPBOARD =
                (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);

        mTouchCharInput = findViewById(R.id.awt_touch_char);
        mTouchCharInput.setCharacterSender(new AwtCharSender());

        mLoggerView = findViewById(R.id.launcherLoggerView);
        mLoggerView.setVisibility(View.GONE);

        mTextureView = findViewById(R.id.installmod_surfaceview);
        mTouchInputController =
                new TouchInputController(mTextureView, this::onClientTap);
        mTextureView.setOnTouchListener(mTouchInputController);

        installBackHandling();

        // Let Android finish measuring the game surface, then make the Cacio AWT
        // screen and RT4's GLFW canvas agree on that same logical widescreen
        // viewport before the child JVM starts.
        mTextureView.post(() -> {
            mTextureView.configureForCurrentView();
            Logger.appendToLog(
                    "SINGLEPLAYER_UI: LOGICAL_VIEWPORT "
                            + AWTCanvasView.AWT_CANVAS_WIDTH
                            + "x"
                            + AWTCanvasView.AWT_CANVAS_HEIGHT);
            launchCombinedRuntime();
        });
    }

    private void launchCombinedRuntime() {
        try {
            final Runtime runtime = SinglePlayerManager.getRuntime();

            new Thread(() -> {
                try {
                    final int exit = launchJavaRuntime(runtime, "");
                    if (exit != 0) {
                        return;
                    }
                    runOnUiThread(() -> fullyExit());
                } catch (Throwable e) {
                    Logger.appendToLog("Combined single-player launch failed:");
                    Logger.appendToLog(Log.getStackTraceString(e));
                    Tools.showError(JavaGUILauncherActivity.this, e);
                }
            }, "SinglePlayerRuntime").start();
        } catch (Throwable th) {
            Tools.showError(this, th, true);
        }
    }

    private void onClientTap(int clientX, int clientY) {
        // The chat entry stays anchored to the lower-left of RT4's resizable
        // canvas. Keep the historical 503px offsets relative to the bottom so
        // the mobile keyboard trigger follows the expanded viewport.
        int chatMinY = Math.max(
                0,
                AWTCanvasView.AWT_CANVAS_HEIGHT - CHAT_KEYBOARD_TOP_FROM_BOTTOM);
        int chatMaxY = Math.max(
                chatMinY,
                AWTCanvasView.AWT_CANVAS_HEIGHT - CHAT_KEYBOARD_BOTTOM_FROM_BOTTOM);
        if (clientX <= CHAT_KEYBOARD_MAX_X
                && clientY >= chatMinY
                && clientY <= chatMaxY
                && !TouchCharInput.softKeyboardIsActive) {
            mTouchCharInput.switchKeyboardState();
        }
    }

    private void installBackHandling() {
        getOnBackPressedDispatcher().addCallback(
                this,
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (TouchCharInput.softKeyboardIsActive) {
                            mTouchCharInput.disable();
                            return;
                        }

                        long now = android.os.SystemClock.elapsedRealtime();
                        if (now - mLastBackPressMs <= EXIT_BACK_WINDOW_MS) {
                            if (mTouchInputController != null) {
                                mTouchInputController.cancel();
                            }
                            fullyExit();
                            return;
                        }

                        mLastBackPressMs = now;
                        AWTInputBridge.sendKey(
                                (char) AWTInputEvent.VK_ESCAPE,
                                AWTInputEvent.VK_ESCAPE);
                        Toast.makeText(
                                JavaGUILauncherActivity.this,
                                "Back again to exit",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void startSinglePlayerLoadingPoll() {
        mSinglePlayerLoadingOverlay.setVisibility(View.VISIBLE);
        mSinglePlayerLoadingStatus.setText("Starting single-player...");

        mSinglePlayerLoadingPoll =
                new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }

                        String stage = readSinglePlayerStage();
                        if (stage.length() > 0) {
                            mSinglePlayerLoadingStatus.setText(stage);
                        }

                        if (mSinglePlayerReadyFile.isFile()) {
                            mSinglePlayerLoadingOverlay.setVisibility(View.GONE);
                            Logger.appendToLog("SINGLEPLAYER_UI: GAME_VISIBLE");
                            return;
                        }

                        mSinglePlayerLoadingHandler.postDelayed(this, 250L);
                    }
                };
        mSinglePlayerLoadingHandler.post(mSinglePlayerLoadingPoll);
    }

    private String readSinglePlayerStage() {
        if (!mSinglePlayerStageFile.isFile()) {
            return "";
        }

        try (BufferedReader reader =
                new BufferedReader(new FileReader(mSinglePlayerStageFile))) {
            String line = reader.readLine();
            return line == null ? "" : line.trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    @Override
    protected void onPause() {
        if (mTouchInputController != null) {
            mTouchInputController.cancel();
        }
        if (mTextureView != null) {
            mTextureView.setRenderingPaused(true);
        }
        AWTInputBridge.setMobileAppPaused(true);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (mTouchInputController != null) {
            mTouchInputController.cancel();
        }
        if (mSinglePlayerLoadingHandler != null
                && mSinglePlayerLoadingPoll != null) {
            mSinglePlayerLoadingHandler.removeCallbacks(
                    mSinglePlayerLoadingPoll);
        }
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        AWTInputBridge.setMobileAppPaused(false);
        if (mTextureView != null) {
            mTextureView.setRenderingPaused(false);
        }
        final int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            KeyEncoder.sendEncodedChar(
                    event.getKeyCode(), (char) event.getUnicodeChar());
        }
        return true;
    }

    public int launchJavaRuntime(Runtime runtime, String javaArgs) {
        JREUtils.redirectAndPrintJRELog();
        try {
            File worldRoot = SinglePlayerManager.getWorldRoot(this);
            File engineJar = new File(worldRoot, "engine.jar");
            File bootstrapJar =
                    new File(Tools.DIR_DATA, "singleplayer-bootstrap.jar");
            File clientJar = new File(Tools.DIR_DATA, "rt4.jar");

            if (!engineJar.isFile()) {
                throw new IOException(
                        "Single-player world engine is missing: " + engineJar);
            }
            if (!bootstrapJar.isFile()) {
                throw new IOException(
                        "Single-player bootstrap is missing: " + bootstrapJar);
            }
            if (!clientJar.isFile()) {
                throw new IOException(
                        "RT4 client is missing: " + clientJar);
            }

            List<String> javaArgList = new ArrayList<>();

            Tools.getCacioJavaArgs(javaArgList, false);
            javaArgList.add(
                    "-DconfigFile=" + Tools.DIR_DATA + "/config.json");
            javaArgList.add(
                    "-DpluginDir=" + Tools.DIR_DATA + "/plugins/");
            javaArgList.add(
                    "-DclientHomeOverride=" + Tools.DIR_DATA);
            javaArgList.add(
                    "-DsinglePlayerName="
                            + SinglePlayerManager.getProfileName(this));
            javaArgList.add("-Dsingleplayer=true");
            javaArgList.add(
                    "-DglfwWidth=" + AWTCanvasView.AWT_CANVAS_WIDTH);
            javaArgList.add(
                    "-DglfwHeight=" + AWTCanvasView.AWT_CANVAS_HEIGHT);
            javaArgList.add(
                    "-Dorg.sqlite.lib.path="
                            + getApplicationInfo().nativeLibraryDir);
            javaArgList.add(
                    "-Dorg.sqlite.lib.name=libsqlitejdbc.so");
            javaArgList.add("-Djava.awt.headless=false");
            javaArgList.add("-cp");
            javaArgList.add(
                    bootstrapJar.getAbsolutePath()
                            + ":"
                            + engineJar.getAbsolutePath()
                            + ":"
                            + clientJar.getAbsolutePath());
            javaArgList.add("singleplayer.InProcessBootstrap");

            LauncherPreferences.PREF_RAM_ALLOCATION =
                    Math.max(
                            LauncherPreferences.PREF_RAM_ALLOCATION,
                            4096);

            Logger.appendToLog(
                    "Info: combined Java arguments: "
                            + Arrays.toString(
                                    javaArgList.toArray(new String[0])));

            return JREUtils.launchJavaVM(
                    this,
                    runtime,
                    worldRoot,
                    javaArgList,
                    LauncherPreferences.PREF_CUSTOM_JAVA_ARGS);
        } catch (Throwable th) {
            Logger.appendToLog(
                    "Combined single-player launch failed:");
            Logger.appendToLog(Log.getStackTraceString(th));
            Tools.showError(this, th, true);
            return -1;
        }
    }
}
