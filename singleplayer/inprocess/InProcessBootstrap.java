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
