package rt4;

import java.lang.reflect.Method;
import java.security.SecureRandom;

/**
 * Socketless single-player login adapter.
 *
 * The world still runs its existing authenticator, Login.proceedWith(),
 * LoginParser and outgoing login/game encoders. This class only replaces the
 * transport handshake: it asks the already-running world to create the local
 * session, then consumes the exact success metadata and first rebuild packet
 * from LocalPresentationBridge using the same RT4 state transitions as the
 * retained LoginManager.
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
    private static Method beginLocalLogin;

    private LocalLoginBridge() {}

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
        if (username == null || username.trim().isEmpty()) {
            fail(3, "empty profile name");
            return false;
        }

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

            Player.usernameInput = JagString.of(username.trim());
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
                    username.trim(),
                    Preferences.antiAliasingMode,
                    DisplayMode.getWindowMode(),
                    GameShell.canvasWidth,
                    GameShell.canvasHeight,
                    seed);
            if (!Boolean.TRUE.equals(accepted)) {
                fail(-4, "world rejected local session creation");
                return false;
            }

            state = WAIT_RESPONSE;
            System.out.println("SINGLEPLAYER_LOCAL_LOGIN: REQUESTED username=" + username.trim());
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

                // From this point onward the retained Protocol decoder should see
                // exactly the same BufferedSocket API it always used, but backed
                // solely by LocalPresentationBridge rather than java.net.Socket.
                Protocol.socket = BufferedSocket.createLocal(GameShell.signLink);

                client.method4221();
                SceneGraph.centralZoneX = -1;
                Protocol.readRebuildPacket(false);
                Protocol.opcode = -1;
                state = COMPLETE;
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
        state = IDLE;
        LoginManager.step = 0;
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

    private static void fail(int reply, String reason) {
        state = FAILED;
        LoginManager.reply = reply;
        LoginManager.step = 0;
        System.err.println("SINGLEPLAYER_LOCAL_LOGIN: FAILED " + reason);
    }
}
