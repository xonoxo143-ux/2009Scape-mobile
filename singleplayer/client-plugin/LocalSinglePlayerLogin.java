package LocalSinglePlayerLogin;

import plugin.Plugin;
import plugin.annotations.PluginMeta;
import plugin.api.API;
import rt4.Component;
import rt4.CreateManager;
import rt4.JagString;
import rt4.LoginManager;
import rt4.WorldList;
import rt4.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

@PluginMeta(
        author = "2009Scape Mobile Single Player",
        description = "Transitional login adapter while local session removal is in progress.",
        version = 1.5
)
public class plugin extends Plugin {
    private long lastAttemptMs = 0L;
    private boolean loginScreenAnnounced = false;
    private boolean loggedInAnnounced = false;

    @Override
    public void Update() {
        driveSinglePlayerLogin();
    }

    @Override
    public void ComponentDraw(
            int componentIndex,
            Component component,
            int screenX,
            int screenY) {
        driveSinglePlayerLogin();
    }

    private void driveSinglePlayerLogin() {
        if (API.IsLoggedIn() || client.gameState == 30) {
            if (!loggedInAnnounced) {
                System.out.println("SINGLEPLAYER_E2E: LOGGED_IN");
                notifyLocalRuntimeReady();
                writeStage("Ready");
                markGameReady(true);
                loggedInAnnounced = true;
            }
            return;
        }

        if (client.gameState != 10) {
            return;
        }

        if (!loginScreenAnnounced) {
            System.out.println("SINGLEPLAYER_E2E: LOGIN_SCREEN");
            writeStage("Entering world...");
            loginScreenAnnounced = true;
        }

        if (LoginManager.anInt4937 != 0
                || LoginManager.step != 0
                || CreateManager.step != 0
                || WorldList.step != 0) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastAttemptMs < 3000L) {
            return;
        }

        String username = loadProfileName();
        String password = "local";

        API.SetVarcStr(32, username);
        API.SetVarcStr(33, password);
        lastAttemptMs = now;
        System.out.println("SINGLEPLAYER_E2E: LOGIN_ATTEMPT");
        LoginManager.method3896(
                JagString.of(username),
                JagString.of(password),
                0);
    }

    private void notifyLocalRuntimeReady() {
        try {
            Class<?> bootstrap = Class.forName("singleplayer.InProcessBootstrap");
            bootstrap.getMethod("markClientReady").invoke(null);
            System.out.println("SINGLEPLAYER_RUNTIME: CLIENT_ATTACHED");

            // Shadow proof for the new client->world direct command boundary.
            // This does not replace any gameplay command yet and is intentionally
            // harmless: Ping only refreshes the local player's heartbeat.
            Class<?> localCommands =
                    Class.forName("singleplayer.InProcessBootstrap$LocalCommands");
            Object directPing = localCommands.getMethod("ping").invoke(null);
            if (Boolean.TRUE.equals(directPing)) {
                System.out.println("SINGLEPLAYER_LOCAL_COMMAND: DIRECT_PING_OK");
            }
        } catch (ClassNotFoundException ignored) {
            // The known-good legacy APK can still run without native overlay code.
        } catch (Throwable failure) {
            System.err.println(
                    "SINGLEPLAYER_RUNTIME: client attach failed: " + failure);
        }
    }

    private void writeStage(String value) {
        String home = System.getProperty("clientHomeOverride", "").trim();
        if (home.length() == 0) return;
        File stage = new File(home, "singleplayer-game-stage.txt");
        try (FileWriter writer = new FileWriter(stage, false)) {
            writer.write(value);
            writer.write(System.lineSeparator());
        } catch (Exception ignored) { }
    }

    private void markGameReady(boolean ready) {
        String home = System.getProperty("clientHomeOverride", "").trim();
        if (home.length() == 0) return;
        File marker = new File(home, "singleplayer-game-ready.flag");
        try {
            if (ready) {
                if (!marker.exists()) marker.createNewFile();
            } else if (marker.exists()) {
                marker.delete();
            }
        } catch (Exception ignored) { }
    }

    private String loadProfileName() {
        String direct = System.getProperty("singlePlayerName", "").trim();
        if (direct.length() > 0) return direct;

        String home = System.getProperty("clientHomeOverride", "");
        if (home.length() > 0) {
            File profile = new File(home, "singleplayer-profile.txt");
            if (profile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(profile))) {
                    String line = reader.readLine();
                    if (line != null && line.trim().length() > 0) {
                        return line.trim();
                    }
                } catch (Exception ignored) { }
            }
        }
        return "Player";
    }

    @Override
    public void OnLogout() {
        markGameReady(false);
        writeStage("Returning to game...");
        lastAttemptMs = 0L;
        loginScreenAnnounced = false;
        loggedInAnnounced = false;
    }
}
