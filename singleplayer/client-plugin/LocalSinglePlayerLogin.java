package LocalSinglePlayerLogin;

import plugin.Plugin;
import plugin.annotations.PluginMeta;
import plugin.api.API;
import rt4.Component;
import rt4.CreateManager;
import rt4.WorldList;
import rt4.client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;

@PluginMeta(
        author = "2009Scape Mobile Single Player",
        description = "Creates and enters the local player session without a network login handshake.",
        version = 2.0
)
public class plugin extends Plugin {
    private boolean loginScreenAnnounced = false;
    private boolean localLoginStarted = false;
    private boolean loginFailureAnnounced = false;
    private boolean loggedInAnnounced = false;

    private Class<?> localLoginBridge;
    private Method localLoginBegin;
    private Method localLoginPoll;
    private Method localLoginFailed;
    private Method localLoginReset;

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

        // Keep account-creation/world-list UI state from racing the local entry
        // path. No LoginManager network state is started by this plugin anymore.
        if (CreateManager.step != 0 || WorldList.step != 0) {
            return;
        }

        String username = loadProfileName();
        API.SetVarcStr(32, username);
        API.SetVarcStr(33, "local");

        try {
            resolveLocalLoginBridge();

            if (!localLoginStarted) {
                Object started = localLoginBegin.invoke(null, username);
                if (!Boolean.TRUE.equals(started)) {
                    announceFailure("local session request was rejected");
                    return;
                }
                localLoginStarted = true;
                System.out.println("SINGLEPLAYER_E2E: LOGIN_ATTEMPT");
                writeStage("Loading local player...");
            }

            Object complete = localLoginPoll.invoke(null);
            if (Boolean.TRUE.equals(complete)) {
                writeStage("Loading world...");
                return;
            }

            Object failed = localLoginFailed.invoke(null);
            if (Boolean.TRUE.equals(failed)) {
                announceFailure("local login bridge reported failure");
            }
        } catch (Throwable failure) {
            announceFailure(failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
    }

    private void resolveLocalLoginBridge() throws Exception {
        if (localLoginBridge != null) return;
        localLoginBridge = Class.forName("rt4.LocalLoginBridge");
        localLoginBegin = localLoginBridge.getMethod("begin", String.class);
        localLoginPoll = localLoginBridge.getMethod("poll");
        localLoginFailed = localLoginBridge.getMethod("hasFailed");
        localLoginReset = localLoginBridge.getMethod("reset");
    }

    private void announceFailure(String reason) {
        if (loginFailureAnnounced) return;
        loginFailureAnnounced = true;
        writeStage("Local login failed");
        System.err.println("SINGLEPLAYER_LOCAL_LOGIN: plugin failure: " + reason);
    }

    private void notifyLocalRuntimeReady() {
        try {
            Class<?> bootstrap = Class.forName("singleplayer.InProcessBootstrap");
            bootstrap.getMethod("markClientReady").invoke(null);
            System.out.println("SINGLEPLAYER_RUNTIME: CLIENT_ATTACHED");

            Class<?> localCommands =
                    Class.forName("singleplayer.InProcessBootstrap$LocalCommands");
            Object directPing = localCommands.getMethod("ping").invoke(null);
            if (Boolean.TRUE.equals(directPing)) {
                System.out.println("SINGLEPLAYER_LOCAL_COMMAND: DIRECT_PING_OK");
            }
        } catch (ClassNotFoundException ignored) {
            // Keep the plugin loadable in a bare RT4 development environment.
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
        try {
            resolveLocalLoginBridge();
            localLoginReset.invoke(null);
        } catch (Throwable ignored) { }
        loginScreenAnnounced = false;
        localLoginStarted = false;
        loginFailureAnnounced = false;
        loggedInAnnounced = false;
    }
}
