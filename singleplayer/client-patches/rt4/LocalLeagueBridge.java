package rt4;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Read-only RT4-facing view of the single-player league runtime.
 *
 * League rules and persistence remain world-authoritative. This bridge exists so
 * future RT4/Android league UI can render points, tasks and relics directly from
 * the in-process world without introducing a new packet protocol.
 */
public final class LocalLeagueBridge {
    private static volatile Class<?> playerClass;
    private static volatile Method getPlayerByName;
    private static volatile Method points;
    private static volatile Method completedTasks;
    private static volatile Method unlockedRelics;
    private static volatile Method hasCompletedTask;
    private static volatile Method hasRelic;

    private LocalLeagueBridge() {}

    public static boolean isAvailable() {
        if (!Boolean.getBoolean("singleplayer")) return false;
        try {
            return player() != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static int points() {
        try {
            Object player = player();
            if (player == null) return 0;
            return ((Number) points.invoke(null, player)).intValue();
        } catch (Throwable failure) {
            return 0;
        }
    }

    public static boolean hasCompletedTask(String id) {
        try {
            Object player = player();
            if (player == null || id == null) return false;
            return Boolean.TRUE.equals(hasCompletedTask.invoke(null, player, id));
        } catch (Throwable failure) {
            return false;
        }
    }

    public static boolean hasRelic(String id) {
        try {
            Object player = player();
            if (player == null || id == null) return false;
            return Boolean.TRUE.equals(hasRelic.invoke(null, player, id));
        } catch (Throwable failure) {
            return false;
        }
    }

    public static Set<String> completedTasks() {
        return stringSet(completedTasks);
    }

    public static Set<String> unlockedRelics() {
        return stringSet(unlockedRelics);
    }

    private static Set<String> stringSet(Method method) {
        try {
            Object player = player();
            if (player == null) return Collections.emptySet();
            Object result = method.invoke(null, player);
            if (!(result instanceof Iterable)) return Collections.emptySet();
            LinkedHashSet<String> copy = new LinkedHashSet<>();
            for (Object value : (Iterable<?>) result) {
                if (value != null) copy.add(value.toString());
            }
            return Collections.unmodifiableSet(copy);
        } catch (Throwable failure) {
            return Collections.emptySet();
        }
    }

    private static Object player() throws Exception {
        resolve();
        return getPlayerByName.invoke(null, playerName());
    }

    private static synchronized void resolve() throws Exception {
        if (points != null) return;

        playerClass = Class.forName("core.game.node.entity.player.Player");
        Class<?> repository = Class.forName("core.game.world.repository.Repository");
        getPlayerByName = repository.getMethod("getPlayerByName", String.class);

        Class<?> runtime = Class.forName("core.local.LeagueRuntime");
        points = runtime.getMethod("points", playerClass);
        completedTasks = runtime.getMethod("completedTasks", playerClass);
        unlockedRelics = runtime.getMethod("unlockedRelics", playerClass);
        hasCompletedTask = runtime.getMethod("hasCompletedTask", playerClass, String.class);
        hasRelic = runtime.getMethod("hasRelic", playerClass, String.class);
    }

    private static String playerName() {
        String value = System.getProperty("singlePlayerName", "Player").trim();
        return value.isEmpty() ? "Player" : value;
    }
}
