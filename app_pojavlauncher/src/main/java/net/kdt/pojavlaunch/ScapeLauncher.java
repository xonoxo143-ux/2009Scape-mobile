package net.kdt.pojavlaunch;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.services.ProgressServiceKeeper;

public class ScapeLauncher extends BaseActivity {

    private TextView settings;
    private TextView updateStatus;
    private Button playHD;
    private Button playSD;
    private Button updateFromGitHub;
    private static final int FILE_SELECT_CODE_JSON = 0;
    private static final int FILE_SELECT_CODE_ZIP = 1;
    private ProgressServiceKeeper mProgressServiceKeeper;
    private ProgressLayout mProgressLayout;
    private boolean updateRunning = false;
    private boolean gameSessionStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dummy_launcher);
        settings = findViewById(R.id.settings);
        updateStatus = findViewById(R.id.updateStatus);
        playHD = findViewById(R.id.playHD);
        playSD = findViewById(R.id.playSD);
        updateFromGitHub = findViewById(R.id.updateFromGitHub);
        mProgressLayout = findViewById(R.id.progress_layout);

        ProgressKeeper.addTaskCountListener((mProgressServiceKeeper = new ProgressServiceKeeper(this)));
        ProgressKeeper.addTaskCountListener(mProgressLayout);

        mProgressLayout.observe(ProgressLayout.UNPACK_RUNTIME);
        mProgressLayout.observe(ProgressLayout.INSTALL_MODPACK);

        playHD.setOnClickListener(view -> launchGame(MainActivity.class));
        playSD.setOnClickListener(view -> launchGame(JavaGUILauncherActivity.class));
        updateFromGitHub.setOnClickListener(view -> runGitHubUpdate());
        settings.setOnClickListener(view -> showBottomDialog());
    }

    private void launchGame(Class<?> activityClass) {
        if (!runtimeReady() || updateRunning) return;
        gameSessionStarted = true;
        updateFromGitHub.setEnabled(false);
        updateStatus.setText("Restart the launcher before updating the client.");
        Intent intent = new Intent(ScapeLauncher.this, activityClass);
        startActivity(intent);
    }

    private void runGitHubUpdate() {
        if (gameSessionStarted) {
            Toast.makeText(this,
                    "Client updates are only available before starting a game session.",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (!runtimeReady() || updateRunning) return;

        updateRunning = true;
        setLaunchButtonsEnabled(false);
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
                    setLaunchButtonsEnabled(true);
                    updateFromGitHub.setEnabled(!gameSessionStarted);
                    if (updated) {
                        updateStatus.setText("Client updated from GitHub. Ready to play.");
                        Toast.makeText(ScapeLauncher.this,
                                "RT4 client update installed.", Toast.LENGTH_LONG).show();
                    } else {
                        updateStatus.setText("RT4 client is already up to date.");
                    }
                });
            }

            @Override
            public void onError(Exception error) {
                runOnUiThread(() -> {
                    updateRunning = false;
                    setLaunchButtonsEnabled(true);
                    updateFromGitHub.setEnabled(!gameSessionStarted);
                    String message = error.getMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = error.getClass().getSimpleName();
                    }
                    updateStatus.setText("Update failed. Existing client was kept.");
                    Toast.makeText(ScapeLauncher.this,
                            "GitHub update failed: " + message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setLaunchButtonsEnabled(boolean enabled) {
        playHD.setEnabled(enabled);
        playSD.setEnabled(enabled);
    }

    private boolean runtimeReady(){
        if(mProgressLayout.hasProcesses()){
            Toast.makeText(this, R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
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
