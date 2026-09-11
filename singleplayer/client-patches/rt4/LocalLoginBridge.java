package rt4;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.security.SecureRandom;

/**
 * Socketless single-player login/runtime adapter.
 *
 * The world creates the Player directly and retains its normal LoginParser and
 * initial presentation encoders. RT4 consumes that success/rebuild stream from
 * memory. tickAutoLogin() is driven by the Android-owned NanoTimer, so entering
 * the local world no longer depends on a client plugin or a network login form.
 */
public final class LocalLoginBridge {
    private static final int IDLE = 0;
    private static final int WAIT_RESPONSE = 1;
    private static final int WAIT_HEADER = 2;
    private static final int WAIT_REBUILD = 3;
    private static final int COMPLETE = 4;
    private static final int FAILED = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static int state = IDLE;
    private static String activeUsername;
    private static Method beginLocalLogin;
    private static Method attachLeagueRuntime;
    private static Method endLocalSession;

    private static boolean loginScreenAnnounced;
    private static boolean readyAnnounced;
    private static boolean leagueAttached;
    private static long retryAfterMs;

    private LocalLoginBridge() {}

    /**
     * Per-frame single-player entry driver. This replaces the historical login
     * plugin as the primary boot path while leaving RT4's ordinary login UI code
     * untouched as dead compatibility code for now.
     */
    public static synchronized void tickAutoLogin() {
        if (!Boolean.getBoolean("singleplayer")) return;

        if (client.gameState == 30) {
            // RT4/world readiness owns game visibility. League attachment is an
            // optional extension and must never be able to strand Android on the
            // loading overlay. Attach it independently and keep retrying after
            // the game is already visible if Player.init is still finishing.
            if (!readyAnnounced) {
                readyAnnounced = true;
                writeStage("Ready");
                markGameReady(true);
                notifyLocalRuntimeReady();
                System.out.println("SINGLEPLAYER_E2E: LOGGED_IN");
            }
            if (!leagueAttached) {
                leagueAttached = tryAttachLeagueRuntime();
            }
            return;
        }

        // A real logout returns to state 10. Region rebuilds transiently use 25,
        // so do not tear the world session down for those.
        if (state == COMPLETE && client.gameState == 10) {
            reset();
            readyAnnounced = false;
            loginScreenAnnounced = false;
            markGameReady(false);
            writeStage("Returning to game...");
        }

        if (client.gameState != 10 || CreateManager.step != 0 || WorldList.step != 0) {
            return;
        }

        if (!loginScreenAnnounced) {
            loginScreenAnnounced = true;
            writeStage("Entering world...");
            System.out.println("SINGLEPLAYER_E2E: LOGIN_SCREEN");
        }

        if (state == FAILED) {
            writeStage("Local login failed");
            return;
        }

        if (state == IDLE) {
            long now = System.currentTimeMillis();
            if (now < retryAfterMs) return;
            if (!begin(loadProfileName())) {
                retryAfterMs = now + 250L;
                return;
            }
            writeStage("Loading local player...");
            System.out.println("SINGLEPLAYER_E2E: LOGIN_ATTEMPT");
        }

        if (isInProgress()) {
            poll();
        }
    }

    public static synchronized boolean begin(String username) {
        if (!Boolean.getBoolean("singleplayer") || client.gameState != 10) {
            return false;
        }
        if (state == WAIT_RESPONSE || state == WAIT_HEADER || state == WAIT_REBUILD) {
            return true;
        }
        if (state == COMPLETE) {
            return true;
        }
        if (state == FAILED) {
            return false;
        }
        if (username == null || username.trim().isEmpty()) {
            fail(3, "empty profile name");
            return false;
        }

        String normalized = username.trim();
        try {
            if (Protocol.socket != null) {
                Protocol.socket.close();
                Protocol.socket = null;
            }

            LocalPresentationBridge.reset();
            LocalPresentationBridge.requestServerToClientCutover();

            int[] seed = new int[4];
            for (int i = 0; i < seed.length; i++) {
                seed[i] = RANDOM.nextInt();
            }

            int[] inboundSeed = seed.clone();
            for (int i = 0; i < inboundSeed.length; i++) {
                inboundSeed[i] += 50;
            }

            Protocol.outboundBuffer.offset = 0;
            Protocol.outboundBuffer.setKey(seed);
            Protocol.inboundBuffer.offset = 0;
            Protocol.inboundBuffer.setKey(inboundSeed);

            Player.usernameInput = JagString.of(normalized);
            Player.password = JagString.of("local");
            Player.name37 = Player.usernameInput.encode37();
            Preferences.sentToServer = true;

            LoginManager.reply = -2;
            LoginManager.step = 0;
            LoginManager.loops = 0;
            LoginManager.errors = 0;
            LoginManager.aBoolean247 = false;

            Method method = resolveBeginLocalLogin();
            Object accepted = method.invoke(
                    null,
                    normalized,
                    Preferences.antiAliasingMode,
                    DisplayMode.getWindowMode(),
                    GameShell.canvasWidth,
                    GameShell.canvasHeight,
                    seed);
            if (!Boolean.TRUE.equals(accepted)) {
                // Repository teardown after a previous logout may still be
                // settling. Stay idle and retry on a later game tick.
                LocalPresentationBridge.reset();
                return false;
            }

            activeUsername = normalized;
            leagueAttached = false;
            state = WAIT_RESPONSE;
            System.out.println("SINGLEPLAYER_LOCAL_LOGIN: REQUESTED username=" + normalized);
            return true;
        } catch (Throwable failure) {
            fail(-4, failure.getClass().getSimpleName() + ": " + failure.getMessage());
            return false;
        }
    }

    /** Advance the retained successful-login state from the local byte stream. */
    public static synchronized boolean poll() {
        if (state == COMPLETE) return true;
        if (state == IDLE || state == FAILED) return false;

        try {
            if (state == WAIT_RESPONSE) {
                if (LocalPresentationBridge.availableServerBytes() < 1) {
                    return false;
                }
                int response = LocalPresentationBridge.readServerByte();
                if (response != 2) {
                    fail(response, "world login response=" + response);
                    return false;
                }
                state = WAIT_HEADER;
            }

            if (state == WAIT_HEADER) {
                if (LocalPresentationBridge.availableServerBytes() < 14) {
                    return false;
                }
                Protocol.inboundBuffer.offset = 0;
                int copied = LocalPresentationBridge.readServerBytes(
                        Protocol.inboundBuffer.data, 0, 14);
                if (copied != 14) {
                    throw new IllegalStateException("short local login header: " + copied);
                }

                LoginManager.staffModLevel = Protocol.inboundBuffer.g1();
                LoginManager.blackmarks = Protocol.inboundBuffer.g1();
                LoginManager.playerUnderage = Protocol.inboundBuffer.g1() == 1;
                LoginManager.parentalChatConsent = Protocol.inboundBuffer.g1() == 1;
                LoginManager.parentalAdvertConsent = Protocol.inboundBuffer.g1() == 1;
                LoginManager.mapQuickChat = Protocol.inboundBuffer.g1() == 1;
                MouseRecorder.enabled = Protocol.inboundBuffer.g1() == 1;
                PlayerList.selfId = Protocol.inboundBuffer.g2();
                LoginManager.playerMember = Protocol.inboundBuffer.g1() == 1;
                LoginManager.mapMembers = Protocol.inboundBuffer.g1() == 1;
                LocTypeList.setAllowMembers(LoginManager.mapMembers);
                ObjTypeList.setAllowMembers(LoginManager.mapMembers);
                Protocol.opcode = Protocol.inboundBuffer.g1isaac();
                Protocol.length = Protocol.inboundBuffer.g2();
                state = WAIT_REBUILD;
            }

            if (state == WAIT_REBUILD) {
                if (Protocol.length < 0) {
                    throw new IllegalStateException("invalid initial rebuild length " + Protocol.length);
                }
                if (LocalPresentationBridge.availableServerBytes() < Protocol.length) {
                    return false;
                }

                Protocol.inboundBuffer.offset = 0;
                int copied = LocalPresentationBridge.readServerBytes(
                        Protocol.inboundBuffer.data, 0, Protocol.length);
                if (copied != Protocol.length) {
                    throw new IllegalStateException(
                            "short local rebuild packet: " + copied + " of " + Protocol.length);
                }

                LoginManager.reply = 2;
                LoginManager.step = 0;
                Protocol.socket = BufferedSocket.createLocal(GameShell.signLink);

                client.method4221();
                SceneGraph.centralZoneX = -1;
                Protocol.readRebuildPacket(false);
                Protocol.opcode = -1;

                // Save parsing happens before the success/rebuild stream exists.
                // Player.init may still be finishing concurrently, so attachment
                // is retried from tickAutoLogin until the world reports ready.
                leagueAttached = tryAttachLeagueRuntime();

                state = COMPLETE;
                writeStage("Loading map...");
                System.out.println("SINGLEPLAYER_LOCAL_LOGIN: RT4_REBUILD_READY");
                return true;
            }
        } catch (Throwable failure) {
            fail(-4, failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }
        return false;
    }

    public static synchronized boolean isInProgress() {
        return state == WAIT_RESPONSE || state == WAIT_HEADER || state == WAIT_REBUILD;
    }

    public static synchronized boolean hasFailed() {
        return state == FAILED;
    }

    public static synchronized void reset() {
        closeWorldSession();
        state = IDLE;
        leagueAttached = false;
        LoginManager.step = 0;
        retryAfterMs = System.currentTimeMillis() + 250L;
        if (Protocol.socket != null) {
            Protocol.socket.close();
            Protocol.socket = null;
        }
        LocalPresentationBridge.reset();
    }

    private static Method resolveBeginLocalLogin() throws Exception {
        Method method = beginLocalLogin;
        if (method != null) return method;
        Class<?> probe = Class.forName("core.local.LocalMigrationProbe");
        method = probe.getMethod(
                "beginLocalLogin",
                String.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int[].class);
        beginLocalLogin = method;
        return method;
    }

    private static Method resolveAttachLeagueRuntime() throws Exception {
        Method method = attachLeagueRuntime;
        if (method != null) return method;
        Class<?> probe = Class.forName("core.local.LocalMigrationProbe");
        method = probe.getMethod("attachLeagueRuntime", String.class);
        attachLeagueRuntime = method;
        return method;
    }

    private static Method resolveEndLocalSession() throws Exception {
        Method method = endLocalSession;
        if (method != null) return method;
        Class<?> probe = Class.forName("core.local.LocalMigrationProbe");
        method = probe.getMethod("endLocalSession", String.class);
        endLocalSession = method;
        return method;
    }

    private static boolean tryAttachLeagueRuntime() {
        String username = activeUsername;
        if (username == null || username.isEmpty()) return false;
        try {
            return Boolean.TRUE.equals(resolveAttachLeagueRuntime().invoke(null, username));
        } catch (Throwable failure) {
            System.err.println(
                    "SINGLEPLAYER_LEAGUE: attachment retry: " + failure.getClass().getSimpleName());
            return false;
        }
    }

    private static void closeWorldSession() {
        String username = activeUsername;
        activeUsername = null;
        if (username == null || username.isEmpty()) return;
        try {
            resolveEndLocalSession().invoke(null, username);
        } catch (Throwable failure) {
            System.err.println(
                    "SINGLEPLAYER_LOCAL_LOGIN: world-session close failed: " + failure);
        }
    }

    private static void notifyLocalRuntimeReady() {
        try {
            Class<?> bootstrap = Class.forName("singleplayer.InProcessBootstrap");
            bootstrap.getMethod("markClientReady").invoke(null);
            System.out.println("SINGLEPLAYER_RUNTIME: CLIENT_ATTACHED");
        } catch (Throwable failure) {
            System.err.println("SINGLEPLAYER_RUNTIME: client attach failed: " + failure);
        }
    }

    private static String loadProfileName() {
        String direct = System.getProperty("singlePlayerName", "").trim();
        if (!direct.isEmpty()) return direct;

        String home = System.getProperty("clientHomeOverride", "");
        if (!home.isEmpty()) {
            File profile = new File(home, "singleplayer-profile.txt");
            if (profile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(profile))) {
                    String line = reader.readLine();
                    if (line != null && !line.trim().isEmpty()) return line.trim();
                } catch (Exception ignored) {
                }
            }
        }
        return "Player";
    }

    private static void writeStage(String value) {
        String home = System.getProperty("clientHomeOverride", "").trim();
        if (home.isEmpty()) return;
        try (FileWriter writer = new FileWriter(new File(home, "singleplayer-game-stage.txt"), false)) {
            writer.write(value);
            writer.write(System.lineSeparator());
        } catch (Exception ignored) {
        }
    }

    private static void markGameReady(boolean ready) {
        String home = System.getProperty("clientHomeOverride", "").trim();
        if (home.isEmpty()) return;
        File marker = new File(home, "singleplayer-game-ready.flag");
        try {
            if (ready) {
                if (!marker.exists()) marker.createNewFile();
            } else if (marker.exists()) {
                marker.delete();
            }
        } catch (Exception ignored) {
        }
    }

    private static void fail(int reply, String reason) {
        closeWorldSession();
        state = FAILED;
        leagueAttached = false;
        LoginManager.reply = reply;
        LoginManager.step = 0;
        writeStage("Local login failed");
        System.err.println("SINGLEPLAYER_LOCAL_LOGIN: FAILED " + reason);
    }
}
