package singleplayer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Starts the retained 2009Scape/RT4 engines under one local game authority. */
public final class InProcessBootstrap {
    private static final String STAGE_FILE = "singleplayer-game-stage.txt";

    static {
        // Log capture is part of the normal runtime, not a diagnostic-only build
        // feature. Diagnostic payloads may add extra timing/watchdog detail, but
        // every full payload must still rotate and refresh singleplayer-debug.log.
        SinglePlayerDebug.install();
    }

    private InProcessBootstrap() {}

    /** Temporary public adapter while retained RT4 classes cannot import package-private config. */
    public static int getViewDistance() {
        return LocalGameRuntime.get().config().viewDistance();
    }

    /** Called by the transitional login adapter once RT4 has entered the world. */
    public static void markClientReady() {
        LocalGameRuntime.get().markClientReady();
    }

    public static void main(String[] args) throws Throwable {
        System.setProperty("singleplayer", "true");
        LocalGameRuntime runtime = LocalGameRuntime.get();
        runtime.beginStart();

        try {
            setStage("Starting single-player...");
            System.out.println("SINGLEPLAYER_E2E: COMBINED_JVM_START");

            setStage("Preparing local data...");
            verifySQLite();
            System.out.println("SINGLEPLAYER_E2E: SQLITE_READY");

            // World and RT4 still contain historical independent distance fields.
            // Until those classes are physically merged, write both from the one
            // GameConfig value before either side begins normal gameplay.
            applyWorldViewDistance(runtime.config().viewDistance());

            setStage("Loading world...");
            invokeMain("core.Server", new String[]{"worldprops/local.conf"});
            runtime.markWorldReady();
            System.out.println("SINGLEPLAYER_E2E: WORLD_READY");

            applyClientViewDistance(runtime.config().viewDistance());

            // RT4 still enters through its historical login path during this
            // checkpoint. Login/session/socket removal follows after parity tests.
            setStage("Starting game...");
            invokeMain("rt4.client", new String[]{"1", "live", "english", "game0"});
        } catch (Throwable failure) {
            runtime.fail(failure);
            throw failure;
        }
    }

    /**
     * Reuses the existing MapDistance enum instead of forking world logic. The
     * field is changed before gameplay begins and all existing users of
     * MapDistance.RENDERING continue to work unchanged.
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void applyWorldViewDistance(int distance) throws Exception {
        Class<? extends Enum> type =
                (Class<? extends Enum>) Class.forName("core.game.world.map.MapDistance");
        Enum rendering = Enum.valueOf(type, "RENDERING");
        Field field = type.getDeclaredField("distance");
        field.setAccessible(true);
        field.setInt(rendering, distance);

        int actual = (Integer) type.getMethod("getDistance").invoke(rendering);
        if (actual != distance) {
            throw new IllegalStateException(
                    "World view distance authority mismatch: " + actual + " != " + distance);
        }
        System.out.println("SINGLEPLAYER_RUNTIME: WORLD_VIEW_DISTANCE=" + actual);
    }

    /**
     * RT4's distance fields are already mutable. Drive them from the same value
     * rather than maintaining an independent client setting.
     */
    private static void applyClientViewDistance(int distance) throws Exception {
        Class<?> type = Class.forName("rt4.GlobalConfig");
        type.getField("TILE_DISTANCE").setInt(null, distance);
        type.getField("VIEW_DISTANCE").setInt(null, distance * 128);
        type.getField("VIEW_FADE_DISTANCE").setFloat(
                null, ((float) distance / 28.0f) * 256.0f);

        int actual = type.getField("TILE_DISTANCE").getInt(null);
        if (actual != distance) {
            throw new IllegalStateException(
                    "RT4 view distance authority mismatch: " + actual + " != " + distance);
        }
        System.out.println("SINGLEPLAYER_RUNTIME: CLIENT_VIEW_DISTANCE=" + actual);
    }

    /**
     * Direct typed client -> world command boundary.
     *
     * 2009Scape already converts its protocol into Packet subclasses before game
     * logic sees an action. Those classes are therefore retained as local game
     * commands. Reflection keeps this bootstrap independently compilable while
     * still bypassing packet serialization, TCP, decoding and ISAAC when a RT4
     * call site is switched to this bridge.
     */
    public static final class LocalCommands {
        private static final ConcurrentHashMap<String, Constructor<?>> CONSTRUCTORS =
                new ConcurrentHashMap<>();
        private static volatile Class<?> playerClass;
        private static volatile Method getPlayerByName;
        private static volatile Method enqueue;

        private LocalCommands() {}

        public static boolean isAvailable() {
            try {
                resolveEngineTypes();
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }

        public static boolean ping() {
            return command("Ping", new Class<?>[0]);
        }

        public static boolean worldspaceWalk(int x, int y, boolean run) {
            return command(
                    "WorldspaceWalk",
                    new Class<?>[]{int.class, int.class, boolean.class},
                    x, y, run);
        }

        public static boolean interactWalk(int x, int y, boolean run) {
            return command(
                    "InteractWalk",
                    new Class<?>[]{int.class, int.class, boolean.class},
                    x, y, run);
        }

        public static boolean minimapWalk(
                int x,
                int y,
                int clickedX,
                int clickedY,
                int rotation,
                boolean run) {
            return command(
                    "MinimapWalk",
                    new Class<?>[]{
                        int.class, int.class, int.class,
                        int.class, int.class, boolean.class
                    },
                    x, y, clickedX, clickedY, rotation, run);
        }

        public static boolean npcAction(int option, int npcIndex) {
            return command(
                    "NpcAction",
                    new Class<?>[]{int.class, int.class},
                    option, npcIndex);
        }

        public static boolean playerAction(int option, int otherIndex) {
            return command(
                    "PlayerAction",
                    new Class<?>[]{int.class, int.class},
                    option, otherIndex);
        }

        public static boolean sceneryAction(int option, int id, int x, int y) {
            return command(
                    "SceneryAction",
                    new Class<?>[]{int.class, int.class, int.class, int.class},
                    option, id, x, y);
        }

        public static boolean groundItemAction(int option, int id, int x, int y) {
            return command(
                    "GroundItemAction",
                    new Class<?>[]{int.class, int.class, int.class, int.class},
                    option, id, x, y);
        }

        public static boolean itemAction(
                int option,
                int itemId,
                int slot,
                int iface,
                int child) {
            return command(
                    "ItemAction",
                    new Class<?>[]{int.class, int.class, int.class, int.class, int.class},
                    option, itemId, slot, iface, child);
        }

        public static boolean itemExamine(int id) {
            return command("ItemExamine", new Class<?>[]{int.class}, id);
        }

        public static boolean sceneryExamine(int id) {
            return command("SceneryExamine", new Class<?>[]{int.class}, id);
        }

        public static boolean npcExamine(int id) {
            return command("NpcExamine", new Class<?>[]{int.class}, id);
        }

        public static boolean interfaceAction(
                int opcode,
                int option,
                int iface,
                int child,
                int slot,
                int itemId) {
            return command(
                    "IfAction",
                    new Class<?>[]{
                        int.class, int.class, int.class,
                        int.class, int.class, int.class
                    },
                    opcode, option, iface, child, slot, itemId);
        }

        public static boolean continueOption(
                int iface, int child, int slot, int opcode) {
            return command(
                    "ContinueOption",
                    new Class<?>[]{int.class, int.class, int.class, int.class},
                    iface, child, slot, opcode);
        }

        public static boolean closeInterface() {
            return command("CloseIface", new Class<?>[0]);
        }

        public static boolean trackingFocus(boolean focused) {
            return command(
                    "TrackingFocus",
                    new Class<?>[]{boolean.class},
                    focused);
        }

        public static boolean trackingCamera(int x, int y) {
            return command(
                    "TrackingCameraPos",
                    new Class<?>[]{int.class, int.class},
                    x, y);
        }

        public static boolean trackingDisplay(
                int windowMode, int width, int height, int displayMode) {
            return command(
                    "TrackingDisplayUpdate",
                    new Class<?>[]{int.class, int.class, int.class, int.class},
                    windowMode, width, height, displayMode);
        }

        public static boolean trackingMouseClick(
                int x, int y, boolean rightClick, int delay) {
            return command(
                    "TrackingMouseClick",
                    new Class<?>[]{int.class, int.class, boolean.class, int.class},
                    x, y, rightClick, delay);
        }

        public static boolean addFriend(String username) {
            return command("AddFriend", new Class<?>[]{String.class}, username);
        }

        public static boolean removeFriend(String username) {
            return command("RemoveFriend", new Class<?>[]{String.class}, username);
        }

        public static boolean addIgnore(String username) {
            return command("AddIgnore", new Class<?>[]{String.class}, username);
        }

        public static boolean removeIgnore(String username) {
            return command("RemoveIgnore", new Class<?>[]{String.class}, username);
        }

        public static boolean joinClan(String clanName) {
            return command("JoinClan", new Class<?>[]{String.class}, clanName);
        }

        public static boolean setClanRank(String username, int rank) {
            return command(
                    "SetClanRank",
                    new Class<?>[]{String.class, int.class},
                    username, rank);
        }

        public static boolean kickFromClan(String username) {
            return command("KickFromClan", new Class<?>[]{String.class}, username);
        }

        public static boolean commandLine(String text) {
            return command("Command", new Class<?>[]{String.class}, text);
        }

        public static boolean inputPrompt(String response) {
            return command(
                    "InputPromptResponse",
                    new Class<?>[]{Object.class},
                    response);
        }

        public static boolean inputPrompt(int response) {
            return command(
                    "InputPromptResponse",
                    new Class<?>[]{Object.class},
                    Integer.valueOf(response));
        }

        private static boolean command(
                String simpleName, Class<?>[] tailTypes, Object... tailArgs) {
            try {
                resolveEngineTypes();
                Object player = getPlayerByName.invoke(null, playerName());
                if (player == null) return false;

                Class<?>[] types = new Class<?>[tailTypes.length + 1];
                Object[] args = new Object[tailArgs.length + 1];
                types[0] = playerClass;
                args[0] = player;
                System.arraycopy(tailTypes, 0, types, 1, tailTypes.length);
                System.arraycopy(tailArgs, 0, args, 1, tailArgs.length);

                String key = simpleName + java.util.Arrays.toString(types);
                Constructor<?> constructor = CONSTRUCTORS.get(key);
                if (constructor == null) {
                    Class<?> commandType =
                            Class.forName("core.net.packet.in.Packet$" + simpleName);
                    constructor = commandType.getConstructor(types);
                    Constructor<?> previous =
                            CONSTRUCTORS.putIfAbsent(key, constructor);
                    if (previous != null) constructor = previous;
                }

                Object packet = constructor.newInstance(args);
                enqueue.invoke(null, packet);
                return true;
            } catch (Throwable failure) {
                System.err.println(
                        "SINGLEPLAYER_LOCAL_COMMAND: " + simpleName + " failed: " + failure);
                return false;
            }
        }

        private static synchronized void resolveEngineTypes() throws Exception {
            if (enqueue != null) return;

            playerClass = Class.forName("core.game.node.entity.player.Player");
            Class<?> repository =
                    Class.forName("core.game.world.repository.Repository");
            getPlayerByName = repository.getMethod("getPlayerByName", String.class);

            Class<?> packetClass = Class.forName("core.net.packet.in.Packet");
            Class<?> processor = Class.forName("core.net.packet.PacketProcessor");
            enqueue = processor.getMethod("enqueue", packetClass);
        }

        private static String playerName() {
            return System.getProperty("singlePlayerName", "Player");
        }
    }

    private static void verifySQLite() throws Exception {
        Class.forName("org.sqlite.JDBC");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE android_sqlite_probe(value INTEGER)");
            statement.execute("INSERT INTO android_sqlite_probe(value) VALUES (2009)");
            try (ResultSet result = statement.executeQuery(
                    "SELECT value FROM android_sqlite_probe LIMIT 1")) {
                if (!result.next() || result.getInt(1) != 2009) {
                    throw new IllegalStateException(
                            "SQLite JNI probe returned the wrong result");
                }
            }
        }
    }

    private static void setStage(String value) {
        String home = System.getProperty("clientHomeOverride", "").trim();
        if (home.length() == 0) return;
        try {
            Files.writeString(
                    Path.of(home, STAGE_FILE),
                    value + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // Stage text is UX-only and must never prevent the game from starting.
        }
    }

    private static void invokeMain(String className, String[] args) throws Throwable {
        try {
            Class<?> type = Class.forName(className);
            Method main = type.getMethod("main", String[].class);
            main.invoke(null, (Object) args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause != null) throw cause;
            throw e;
        }
    }
}

/** One authoritative configuration object for the local game instance. */
final class GameConfig {
    static final String VIEW_DISTANCE_PROPERTY = "singleplayer.viewDistance";
    static final int DEFAULT_VIEW_DISTANCE = 28;
    static final int MIN_VIEW_DISTANCE = 15;
    static final int MAX_LEGACY_SCENE_VIEW_DISTANCE = 48;

    private final int viewDistance;

    private GameConfig(int viewDistance) {
        this.viewDistance = Math.max(
                MIN_VIEW_DISTANCE,
                Math.min(MAX_LEGACY_SCENE_VIEW_DISTANCE, viewDistance));
    }

    static GameConfig load() {
        return new GameConfig(Integer.getInteger(
                VIEW_DISTANCE_PROPERTY,
                DEFAULT_VIEW_DISTANCE));
    }

    void publishLegacyProperties() {
        System.setProperty(VIEW_DISTANCE_PROPERTY, Integer.toString(viewDistance));
    }

    int viewDistance() {
        return viewDistance;
    }

    @Override
    public String toString() {
        return "GameConfig{viewDistance=" + viewDistance + '}';
    }
}

/** Single process authority around retained world/client engines during migration. */
final class LocalGameRuntime {
    enum State {
        NEW,
        STARTING,
        WORLD_READY,
        RUNNING,
        PAUSED,
        STOPPED,
        FAILED
    }

    private static final LocalGameRuntime INSTANCE = new LocalGameRuntime();

    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);
    private volatile GameConfig config = GameConfig.load();
    private volatile boolean appPaused;

    private LocalGameRuntime() {}

    static LocalGameRuntime get() {
        return INSTANCE;
    }

    synchronized void beginStart() {
        State current = state.get();
        if (current != State.NEW && current != State.STOPPED) {
            throw new IllegalStateException(
                    "Local game runtime cannot start from " + current);
        }
        config = GameConfig.load();
        config.publishLegacyProperties();
        appPaused = false;
        state.set(State.STARTING);
        log("STARTING " + config);
    }

    void markWorldReady() {
        if (!state.compareAndSet(State.STARTING, State.WORLD_READY)
                && state.get() != State.WORLD_READY) {
            throw new IllegalStateException(
                    "World became ready from unexpected state " + state.get());
        }
        log("WORLD_READY");
    }

    synchronized void markClientReady() {
        State current = state.get();
        if (current != State.WORLD_READY
                && current != State.RUNNING
                && current != State.PAUSED) {
            throw new IllegalStateException(
                    "Client became ready from unexpected state " + current);
        }
        state.set(appPaused ? State.PAUSED : State.RUNNING);
        log("CLIENT_READY");
    }

    synchronized void setAppPaused(boolean paused) {
        appPaused = paused;
        State current = state.get();
        if (paused && (current == State.WORLD_READY || current == State.RUNNING)) {
            state.set(State.PAUSED);
        } else if (!paused && current == State.PAUSED) {
            state.set(State.RUNNING);
        }
        log(paused ? "PAUSED" : "RESUMED");
    }

    boolean isAppPaused() {
        return appPaused;
    }

    GameConfig config() {
        return config;
    }

    State state() {
        return state.get();
    }

    void fail(Throwable failure) {
        state.set(State.FAILED);
        log("FAILED " + failure.getClass().getName() + ": " + failure.getMessage());
    }

    private static void log(String message) {
        System.out.println("SINGLEPLAYER_RUNTIME: " + message);
    }
}
