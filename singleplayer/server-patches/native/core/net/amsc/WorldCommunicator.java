package core.net.amsc;

import core.net.EventProducer;
import core.net.IoSession;
import core.net.NioReactor;
import core.net.producer.MSHSEventProducer;
import core.game.node.entity.player.info.login.LoginParser;
import core.tools.SystemLogger;
import core.game.world.GameWorld;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles world communication.
 *
 * In the Android single-player runtime this class remains only as a compatibility
 * API for retained social/content code. It must never establish the old hosted
 * management-server transport: the in-process world is the sole authority.
 */
public final class WorldCommunicator {

    /** The handshake events producer. */
    private static final EventProducer HANDSHAKE_PRODUCER = new MSHSEventProducer();

    /** The current state. */
    private static ManagementServerState state = ManagementServerState.CONNECTING;

    /** The I/O session. */
    private static IoSession session;

    /** The world information. */
    private static final WorldStatistics[] WORLDS = new WorldStatistics[10];

    /** The current login attempts. */
    private static final Map<String, LoginParser> loginAttempts = new ConcurrentHashMap<>();

    /** The NIO reactor. */
    private static NioReactor reactor;

    /** Registers a new hosted-world connection. */
    public static void register(IoSession session) {
        if (isSinglePlayer()) {
            System.out.println("SINGLEPLAYER_MANAGEMENT: REGISTER_IGNORED");
            return;
        }
        WorldCommunicator.session = session;
        session.setProducer(HANDSHAKE_PRODUCER);
        session.write(true);
        WORLDS[GameWorld.getSettings().getWorldId() - 1] =
                new WorldStatistics(GameWorld.getSettings().getWorldId());
        session.setObject(WORLDS[GameWorld.getSettings().getWorldId() - 1]);
    }

    /** Attempts to connect to the management server in hosted mode only. */
    public static void connect() {
        if (isSinglePlayer()) {
            terminate();
            System.out.println("SINGLEPLAYER_MANAGEMENT: CONNECT_BLOCKED");
            return;
        }
        try {
            setState(ManagementServerState.CONNECTING);
            reactor = NioReactor.connect(GameWorld.getSettings().getMsAddress(), 5555);
            reactor.start();
        } catch (Throwable e) {
            e.printStackTrace();
            terminate();
        }
    }

    /**
     * Checks if the Management server is locally hosted.
     * Retained for hosted compatibility; never used by the local Android world.
     */
    private static boolean isLocallyHosted() throws IOException {
        InetAddress address = InetAddress.getByName(GameWorld.getSettings().getMsAddress());
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()) {
            return true;
        }
        return NetworkInterface.getByInetAddress(address) != null;
    }

    /** Terminates any management transport. */
    public static void terminate() {
        setState(ManagementServerState.NOT_AVAILABLE);
        session = null;
        if (reactor != null) {
            reactor.terminate();
            reactor = null;
        }
    }

    /** Gets and removes the login attempt for the given username. */
    public static LoginParser finishLoginAttempt(String username) {
        return loginAttempts.remove(username);
    }

    /** Gets the local world. */
    public static WorldStatistics getLocalWorld() {
        int index = GameWorld.getSettings().getWorldId() - 1;
        if (WORLDS[index] == null) {
            WORLDS[index] = new WorldStatistics(GameWorld.getSettings().getWorldId());
        }
        return WORLDS[index];
    }

    /** Gets the id of the world the player is connected to. */
    public static int getWorld(String playerName) {
        for (int i = 0; i < WORLDS.length; i++) {
            if (WORLDS[i] != null && WORLDS[i].getPlayers().contains(playerName)) {
                return i;
            }
        }
        return -1;
    }

    /** Gets the world statistics for the given index. */
    public static WorldStatistics getWorld(int id) {
        if (id < 1 || id > WORLDS.length) {
            return null;
        }
        return WORLDS[id - 1];
    }

    /** Gets the management session; always null in single-player. */
    public static IoSession getSession() {
        return isSinglePlayer() ? null : session;
    }

    /**
     * Checks if this world is connected to the management server.
     * Single-player deliberately reports false even if stale hosted state exists.
     */
    public static boolean isEnabled() {
        return !isSinglePlayer() && state == ManagementServerState.AVAILABLE;
    }

    /** Gets the login attempts mapping. */
    public static Map<String, LoginParser> getLoginAttempts() {
        return loginAttempts;
    }

    /** Gets the state. */
    public static ManagementServerState getState() {
        return isSinglePlayer() ? ManagementServerState.NOT_AVAILABLE : state;
    }

    /** Sets the state. Hosted callbacks cannot enable management in single-player. */
    public static void setState(ManagementServerState state) {
        if (isSinglePlayer() && state != ManagementServerState.NOT_AVAILABLE) {
            WorldCommunicator.state = ManagementServerState.NOT_AVAILABLE;
            return;
        }
        if (WorldCommunicator.state != state) {
            WorldCommunicator.state = state;
            state.set();
        }
    }

    /** Gets the reactor. */
    public static NioReactor getReactor() {
        return isSinglePlayer() ? null : reactor;
    }

    /** Sets the reactor. */
    public static void setReactor(NioReactor reactor) {
        if (isSinglePlayer()) {
            if (reactor != null) reactor.terminate();
            WorldCommunicator.reactor = null;
            return;
        }
        WorldCommunicator.reactor = reactor;
    }

    private static boolean isSinglePlayer() {
        return java.lang.Boolean.getBoolean("singleplayer");
    }
}
