package singleplayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;

/** Starts the legacy game subsystems under one local game authority. */
public final class InProcessBootstrap {
    private static final String STAGE_FILE = "singleplayer-game-stage.txt";

    private InProcessBootstrap() {}

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

            setStage("Loading world...");
            invokeMain("core.Server", new String[]{"worldprops/local.conf"});
            runtime.markWorldReady();
            System.out.println("SINGLEPLAYER_E2E: WORLD_READY");

            // RT4 still enters through its legacy main/login path during this
            // migration stage. LocalGameRuntime remains the authority around it;
            // login/session/network assumptions are removed in later checkpoints.
            setStage("Starting game...");
            invokeMain("rt4.client", new String[]{"1", "live", "english", "game0"});
        } catch (Throwable failure) {
            runtime.fail(failure);
            throw failure;
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

/**
 * Authoritative configuration for the local game instance.
 * Legacy subsystems temporarily consume mirrored system properties; the value
 * itself is owned here and published before either subsystem initializes.
 */
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

/** Single process authority around the legacy world/client during migration. */
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
