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

    private TextView settings;
    private TextView updateStatus;
    private TextView serverStatus;
    private Button playSinglePlayer;
    private Button worldSettings;
    private Button playerSettings;
    private Button serverFiles;
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
        settings = findViewById(R.id.settings);
        updateStatus = findViewById(R.id.updateStatus);
        serverStatus = findViewById(R.id.serverStatus);
        playSinglePlayer = findViewById(R.id.playSinglePlayer);
        worldSettings = findViewById(R.id.worldSettings);
        playerSettings = findViewById(R.id.playerSettings);
        serverFiles = findViewById(R.id.serverFiles);
        updateFromGitHub = findViewById(R.id.updateFromGitHub);
        mProgressLayout = findViewById(R.id.progress_layout);

        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));
        ProgressKeeper.addTaskCountListener(mProgressLayout);

        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);

        ServerFilesProvider.ensureServerRoot(this);

        playSinglePlayer.setOnClickListener(view -> launchSinglePlayer());
        worldSettings.setOnClickListener(view -> showWorldSettings());
        playerSettings.setOnClickListener(view -> showPlayerSettings());
        serverFiles.setOnClickListener(view -> openServerFiles());
        updateFromGitHub.setOnClickListener(view -> runGitHubUpdate());
        settings.setOnClickListener(view -> showBottomDialog());

        refreshServerStatus();
        prepareSinglePlayerFiles();
    }

    private void prepareSinglePlayerFiles() {
        preparationRunning = true;
        setPrimaryControlsEnabled(false);
        updateStatus.setText("Preparing single-player files...");
        new Thread(() -> {
            try {
                while (!runtimeReadyWithoutToast()) {
                    Thread.sleep(200L);
                }
                LocalServerManager.ensureInstalled(getApplicationContext());
                runOnUiThread(() -> {
                    preparationRunning = false;
                    setPrimaryControlsEnabled(true);
                    updateStatus.setText("Single-player files ready.");
                    refreshServerStatus();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    preparationRunning = false;
                    setPrimaryControlsEnabled(true);
                    updateStatus.setText("Single-player setup failed.");
                    Toast.makeText(this, "Single-player setup failed: " + safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        }, "singleplayer-prepare").start();
    }

    private void launchSinglePlayer() {
        if (!runtimeReady() || updateRunning || preparationRunning) return;

        gameSessionStarted = true;
        setPrimaryControlsEnabled(false);
        updateFromGitHub.setEnabled(false);
        serverStatus.setText("Server: starting...");
        updateStatus.setText("Starting your local world...");

        LocalServerManager.startAndWait(this, new LocalServerManager.Listener() {
            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> {
                    updateStatus.setText(status);
                    refreshServerStatus();
                });
            }

            @Override
            public void onReady() {
                runOnUiThread(() -> {
                    serverStatus.setText("Server: ready • Control API: "
                            + (LocalServerManager.isControlApiReady() ? "ready" : "starting"));
                    updateStatus.setText("Loading local profile " + LocalServerManager.getProfileName(ScapeLauncher.this) + "...");
                    Intent intent = new Intent(ScapeLauncher.this, JavaGUILauncherActivity.class);
                    startActivity(intent);
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    gameSessionStarted = false;
                    setPrimaryControlsEnabled(true);
                    updateFromGitHub.setEnabled(true);
                    serverStatus.setText("Server: failed to start");
                    updateStatus.setText("Local server failed. Existing files were kept.");
                    Toast.makeText(ScapeLauncher.this,
                            "Local server failed: " + safeMessage(error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void refreshServerStatus() {
        if (LocalServerManager.isGameServerReady()) {
            serverStatus.setText("Server: ready • Control API: "
                    + (LocalServerManager.isControlApiReady() ? "ready" : "starting"));
        } else {
            serverStatus.setText("Server: stopped");
        }
    }

    private void showPlayerSettings() {
        if (gameSessionStarted || LocalServerManager.isGameServerReady()) {
            Toast.makeText(this, "Close the running local world before changing profiles.", Toast.LENGTH_LONG).show();
            return;
        }

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        input.setText(LocalServerManager.getProfileName(this));
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(this)
                .setTitle("Local player profile")
                .setMessage("This is a local save name, not an online account.")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    LocalServerManager.setProfileName(this, input.getText().toString());
                    updateStatus.setText("Local profile: " + LocalServerManager.getProfileName(this));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showWorldSettings() {
        if (gameSessionStarted || LocalServerManager.isGameServerReady()) {
            Toast.makeText(this, "World settings apply before the local server starts.", Toast.LENGTH_LONG).show();
            return;
        }

        File conf = new File(ServerFilesProvider.ensureServerRoot(this), "worldprops/local.conf");
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
            for (int i = 0; i < keys.length; i++) values[i] = readBooleanSetting(text, keys[i]);

            new AlertDialog.Builder(this)
                    .setTitle("World settings")
                    .setMultiChoiceItems(labels, values, (dialog, which, checked) -> values[which] = checked)
                    .setPositiveButton("Save", (dialog, which) -> {
                        try {
                            String edited = Tools.read(conf.getAbsolutePath());
                            for (int i = 0; i < keys.length; i++) {
                                edited = writeBooleanSetting(edited, keys[i], values[i]);
                            }
                            Tools.write(conf.getAbsolutePath(), edited);
                            updateStatus.setText("World settings saved.");
                        } catch (IOException e) {
                            Toast.makeText(this, "Could not save world settings: " + safeMessage(e), Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not read local world settings: " + safeMessage(e), Toast.LENGTH_LONG).show();
        }
    }

    private static boolean readBooleanSetting(String text, String key) {
        Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(key) + "\\s*=\\s*(true|false)");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() && Boolean.parseBoolean(matcher.group(1));
    }

    private static String writeBooleanSetting(String text, String key, boolean value) {
        Pattern pattern = Pattern.compile("(?m)^(\\s*" + Pattern.quote(key) + "\\s*=\\s*)(true|false)");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return text;
        return matcher.replaceFirst(Matcher.quoteReplacement(matcher.group(1) + value));
    }

    private void openServerFiles() {
        ServerFilesProvider.ensureServerRoot(this);
        String authority = ServerFilesProvider.getAuthority(this);
        Uri rootDocument = DocumentsContract.buildDocumentUri(authority,
                ServerFilesProvider.ROOT_DOCUMENT_ID);

        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootDocument);
        }

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException noFilePicker) {
            Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                fallback.putExtra(DocumentsContract.EXTRA_INITIAL_URI, rootDocument);
            }
            try {
                startActivity(fallback);
            } catch (ActivityNotFoundException noTreePicker) {
                Toast.makeText(this,
                        "Android could not open a document browser. Server files are still exposed as 2009Scape Server Files.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void runGitHubUpdate() {
        if (gameSessionStarted || LocalServerManager.isGameServerReady()) {
            Toast.makeText(this,
                    "Updates are only available before starting the local world.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!runtimeReady() || updateRunning || preparationRunning) return;

        updateRunning = true;
        setPrimaryControlsEnabled(false);
        updateFromGitHub.setEnabled(false);
        updateStatus.setText("Checking GitHub...");

        GitHubClientUpdater.checkAndUpdate(this, new GitHubClientUpdater.Listener() {
            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> updateStatus.setText(status));
            }

            @Override
            public void onFinished(boolean updated, String remoteSha) {
                runOnUiThread(() -> {
                    updateRunning = false;
                    setPrimaryControlsEnabled(true);
                    updateFromGitHub.setEnabled(!gameSessionStarted);
                    if (updated) {
                        updateStatus.setText("Client updated from GitHub. Ready to play.");
                        Toast.makeText(ScapeLauncher.this,
                                "RT4 client update installed.", Toast.LENGTH_LONG).show();
                    } else {
                        updateStatus.setText("Client is already up to date.");
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    updateRunning = false;
                    setPrimaryControlsEnabled(true);
                    updateFromGitHub.setEnabled(!gameSessionStarted);
                    updateStatus.setText("Update failed. Existing files were kept.");
                    Toast.makeText(ScapeLauncher.this,
                            "GitHub update failed: " + safeMessage(error), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setPrimaryControlsEnabled(boolean enabled) {
        playSinglePlayer.setEnabled(enabled);
        worldSettings.setEnabled(enabled);
        playerSettings.setEnabled(enabled);
    }

    private boolean runtimeReadyWithoutToast() {
        return !mProgressLayout.hasProcesses();
    }

    private boolean runtimeReady() {
        if (mProgressLayout.hasProcesses()) {
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return error.getClass().getSimpleName();
        return message;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showBottomDialog() {
        MyDialogFragment dialog = new MyDialogFragment();
        dialog.show(getSupportFragmentManager(), "tag");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshServerStatus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ProgressKeeper.removeTaskCountListener(mProgressServiceKeeper);
    }
}
