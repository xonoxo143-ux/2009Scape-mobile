package net.kdt.pojavlaunch;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.InputFilter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScapeLauncher extends BaseActivity {

    private TextView updateStatus;
    private TextView gameStatus;
    private Button playSinglePlayer;
    private Button settings;
    private Button worldFiles;
    private Button updateFromGitHub;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ProgressLayout mProgressLayout;
    private boolean updateRunning = false;
    private boolean preparationRunning = false;
    private boolean gameSessionStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dummy_launcher);

        updateStatus = findViewById(R.id.updateStatus);
        gameStatus = findViewById(R.id.serverStatus);
        playSinglePlayer = findViewById(R.id.playSinglePlayer);
        settings = findViewById(R.id.settings);
        worldFiles = findViewById(R.id.serverFiles);
        updateFromGitHub = findViewById(R.id.updateFromGitHub);
        mProgressLayout = findViewById(R.id.progress_layout);

        ProgressKeeper.addTaskCountListener(
                (mProgressServiceKeeper = new ProgressServiceKeeper(this)));
        ProgressKeeper.addTaskCountListener(mProgressLayout);
        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);

        SinglePlayerManager.getWorldRoot(this);

        playSinglePlayer.setOnClickListener(view -> launchSinglePlayer());
        settings.setOnClickListener(view -> showSettingsMenu());
        worldFiles.setOnClickListener(view -> openWorldFiles());
        updateFromGitHub.setOnClickListener(view -> runGitHubUpdate());

        prepareSinglePlayer();
    }

    private void prepareSinglePlayer() {
        preparationRunning = true;
        setPrimaryControlsEnabled(false);
        updateFromGitHub.setEnabled(false);
        gameStatus.setText("Preparing local game...");
        updateStatus.setText("Installing single-player files and runtime.");

        new Thread(() -> {
            try {
                long componentDeadline =
                        android.os.SystemClock.elapsedRealtime() + 120_000L;
                while (ProgressKeeper.hasOngoingTasks()) {
                    if (android.os.SystemClock.elapsedRealtime() >= componentDeadline) {
                        throw new IOException("Launcher component extraction timed out.");
                    }
                    Thread.sleep(200L);
                }

                SinglePlayerManager.prepare(getApplicationContext());
                runOnUiThread(() -> {
                    preparationRunning = false;
                    gameStatus.setText("Ready");
                    updateStatus.setText("");
                    setPrimaryControlsEnabled(true);
                    updateFromGitHub.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    preparationRunning = false;
                    gameStatus.setText("Setup failed");
                    updateStatus.setText("Setup failed: " + safeMessage(e));
                    setPrimaryControlsEnabled(false);
                    updateFromGitHub.setEnabled(false);
                    Toast.makeText(
                            this,
                            "Single-player setup failed: " + safeMessage(e),
                            Toast.LENGTH_LONG).show();
                });
            }
        }, "singleplayer-prepare").start();
    }

    private void launchSinglePlayer() {
        if (!runtimeReady() || updateRunning || preparationRunning || gameSessionStarted) {
            return;
        }

        gameSessionStarted = true;
        setPrimaryControlsEnabled(false);
        updateFromGitHub.setEnabled(false);
        gameStatus.setText("Starting...");
        updateStatus.setText("");

        startActivity(new Intent(this, JavaGUILauncherActivity.class));
    }

    private void runBeforeGameStart(String runningMessage, Runnable action) {
        if (gameSessionStarted) {
            Toast.makeText(this, runningMessage, Toast.LENGTH_LONG).show();
            return;
        }
        action.run();
    }

    private void showSettingsMenu() {
        runBeforeGameStart(
                "Close the running game before changing settings.",
                this::showSettingsMenuDialog);
    }

    private void showSettingsMenuDialog() {
        String[] items = new String[]{
                "Player name",
                "World settings",
                "Advanced launcher settings"
        };

        new AlertDialog.Builder(this)
                .setTitle("Settings")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showPlayerSettingsDialog();
                            break;
                        case 1:
                            showWorldSettingsDialog();
                            break;
                        case 2:
                            showBottomDialog();
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showPlayerSettingsDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        input.setText(SinglePlayerManager.getProfileName(this));
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Player name")
                .setMessage("Local character name.")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    SinglePlayerManager.setProfileName(
                            this, input.getText().toString());
                    updateStatus.setText(
                            "Player: " + SinglePlayerManager.getProfileName(this));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showWorldSettingsDialog() {
        File conf = new File(
                SinglePlayerManager.getWorldRoot(this), "worldprops/local.conf");
        try {
            String text = Tools.read(conf.getAbsolutePath());
            String[] labels = new String[]{
                    "Fake players",
                    "Bots influence Grand Exchange",
                    "Wilderness PvP",
                    "Castle Wars"
            };
            String[] keys = new String[]{
                    "enable_bots",
                    "bots_influence_ge_price",
                    "wild_pvp_enabled",
                    "enable_castle_wars"
            };
            boolean[] values = new boolean[keys.length];
            for (int i = 0; i < keys.length; i++) {
                values[i] = readBooleanSetting(text, keys[i]);
            }

            new AlertDialog.Builder(this)
                    .setTitle("World settings")
                    .setMultiChoiceItems(
                            labels,
                            values,
                            (dialog, which, checked) -> values[which] = checked)
                    .setPositiveButton("Save", (dialog, which) -> {
                        try {
                            String edited = Tools.read(conf.getAbsolutePath());
                            for (int i = 0; i < keys.length; i++) {
                                edited = writeBooleanSetting(
                                        edited, keys[i], values[i]);
                            }
                            Tools.write(conf.getAbsolutePath(), edited);
                            updateStatus.setText("World settings saved.");
                        } catch (IOException e) {
                            Toast.makeText(
                                    this,
                                    "Could not save world settings: "
                                            + safeMessage(e),
                                    Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (IOException e) {
            Toast.makeText(
                    this,
                    "Could not read world settings: " + safeMessage(e),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static boolean readBooleanSetting(String text, String key) {
        Pattern pattern = Pattern.compile(
                "(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*(true|false)");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }

    private static String writeBooleanSetting(
            String text, String key, boolean value) {
        Pattern pattern = Pattern.compile(
                "(?m)^(\\s*" + Pattern.quote(key) + "\\s*=\\s*)(true|false)");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return text;
        return matcher.replaceFirst(
                Matcher.quoteReplacement(matcher.group(1) + value));
    }

    private void openWorldFiles() {
        SinglePlayerManager.getWorldRoot(this);
        String authority = ServerFilesProvider.getAuthority(this);
        Uri rootDocument = DocumentsContract.buildDocumentUri(
                authority, ServerFilesProvider.ROOT_DOCUMENT_ID);

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootDocument);
        }

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException noFilePicker) {
            Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            fallback.addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fallback.putExtra(
                        DocumentsContract.EXTRA_INITIAL_URI, rootDocument);
            }
            try {
                startActivity(fallback);
            } catch (ActivityNotFoundException noTreePicker) {
                Toast.makeText(
                        this,
                        "Android could not open the local world files.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void runGitHubUpdate() {
        runBeforeGameStart(
                "Updates are only available before starting the game.",
                this::runGitHubUpdateBeforeGame);
    }

    private void runGitHubUpdateBeforeGame() {
        if (!runtimeReady() || updateRunning || preparationRunning) return;

        updateRunning = true;
        setPrimaryControlsEnabled(false);
        updateFromGitHub.setEnabled(false);
        gameStatus.setText("Updating...");
        updateStatus.setText("Checking GitHub...");

        GitHubClientUpdater.checkAndUpdate(
                this,
                new GitHubClientUpdater.Listener() {
                    @Override
                    public void onStatus(String status) {
                        runOnUiThread(() -> updateStatus.setText(status));
                    }

                    @Override
                    public void onFinished(boolean updated, String remoteSha) {
                        runOnUiThread(() -> {
                            updateRunning = false;
                            gameStatus.setText("Ready");
                            setPrimaryControlsEnabled(true);
                            updateFromGitHub.setEnabled(!gameSessionStarted);
                            updateStatus.setText(
                                    updated
                                            ? "Updated from GitHub."
                                            : "Already up to date.");
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        runOnUiThread(() -> {
                            updateRunning = false;
                            gameStatus.setText("Ready");
                            setPrimaryControlsEnabled(true);
                            updateFromGitHub.setEnabled(!gameSessionStarted);
                            updateStatus.setText(
                                    "Update failed. Existing files were kept.");
                            Toast.makeText(
                                    ScapeLauncher.this,
                                    "GitHub update failed: "
                                            + safeMessage(error),
                                    Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void setPrimaryControlsEnabled(boolean enabled) {
        playSinglePlayer.setEnabled(enabled);
        settings.setEnabled(enabled);
        worldFiles.setEnabled(enabled);
    }

    private boolean runtimeReady() {
        if (mProgressLayout.hasProcesses()) {
            Toast.makeText(
                    this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showBottomDialog() {
        MyDialogFragment dialog = new MyDialogFragment();
        dialog.show(getSupportFragmentManager(), "tag");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
    }
}
