package singleplayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Starts the 2009Scape world engine and RT4 client inside the same OpenJDK VM.
 * The existing loopback game protocol remains as an internal compatibility
 * boundary, but there is no second Android process or second JVM.
 */
public final class InProcessBootstrap {
    private InProcessBootstrap() {}

    public static void main(String[] args) throws Throwable {
        System.setProperty("singleplayer", "true");
        System.out.println("SINGLEPLAYER_E2E: COMBINED_JVM_START");

        verifySQLite();
        System.out.println("SINGLEPLAYER_E2E: SQLITE_READY");

        invokeMain("core.Server", new String[]{"worldprops/local.conf"});
        System.out.println("SINGLEPLAYER_E2E: WORLD_READY");

        invokeMain("rt4.client", new String[]{"1", "live", "english", "game0"});
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
                    throw new IllegalStateException("SQLite JNI probe returned the wrong result");
                }
            }
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
