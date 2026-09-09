package singleplayer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

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

        invokeMain("core.Server", new String[]{"worldprops/local.conf"});
        System.out.println("SINGLEPLAYER_E2E: WORLD_READY");

        invokeMain("rt4.client", new String[]{"1", "live", "english", "game0"});
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
