package singleplayer;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Single authority for the embedded game instance.
 *
 * This deliberately starts small. During migration, legacy RT4 and 2009Scape
 * code remain intact behind this authority. Networking, login/session state,
 * presentation and persistence are progressively moved under this runtime
 * rather than replaced all at once.
 */
public final class LocalGameRuntime {
    public enum State {
        NEW,
        STARTING,
        WORLD_READY,
        CLIENT_READY,
        RUNNING,
        PAUSED,
        STOPPING,
        STOPPED,
        FAILED
    }

    private static final LocalGameRuntime INSTANCE = new LocalGameRuntime();

    private final AtomicReference<State> state =
            new AtomicReference<>(State.NEW);
    private volatile GameConfig config = GameConfig.load();
    private volatile boolean appPaused;

    private LocalGameRuntime() {}

    public static LocalGameRuntime get() {
        return INSTANCE;
    }

    public synchronized void beginStart() {
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

    public void markWorldReady() {
        transition(State.STARTING, State.WORLD_READY);
    }

    public void markClientReady() {
        State current = state.get();
        if (current != State.WORLD_READY && current != State.CLIENT_READY) {
            throw new IllegalStateException(
                    "Client became ready from unexpected state " + current);
        }
        state.set(State.CLIENT_READY);
        if (!appPaused) {
            state.set(State.RUNNING);
        }
        log("CLIENT_READY");
    }

    public synchronized void setAppPaused(boolean paused) {
        appPaused = paused;
        State current = state.get();
        if (paused) {
            if (current == State.RUNNING || current == State.CLIENT_READY) {
                state.set(State.PAUSED);
            }
        } else if (current == State.PAUSED || current == State.CLIENT_READY) {
            state.set(State.RUNNING);
        }
        log(paused ? "PAUSED" : "RESUMED");
    }

    public boolean isAppPaused() {
        return appPaused;
    }

    public GameConfig config() {
        return config;
    }

    public State state() {
        return state.get();
    }

    public void fail(Throwable failure) {
        state.set(State.FAILED);
        log("FAILED " + failure.getClass().getName() + ": " + failure.getMessage());
    }

    private void transition(State expected, State next) {
        if (!state.compareAndSet(expected, next)) {
            State current = state.get();
            if (current == next) return;
            throw new IllegalStateException(
                    "Expected runtime state " + expected + " but was " + current);
        }
        log(next.name());
    }

    private static void log(String message) {
        System.out.println("SINGLEPLAYER_RUNTIME: " + message);
    }
}
