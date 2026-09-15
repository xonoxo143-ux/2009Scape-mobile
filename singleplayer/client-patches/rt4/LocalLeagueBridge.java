package rt4;

import singleplayer.InProcessBootstrap;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** RT4-facing view of the authoritative single-player League runtime. */
public final class LocalLeagueBridge {
    private static volatile Class<?> playerClass;
    private static volatile Method getPlayerByName;
    private static volatile Method points;
    private static volatile Method completedTasks;
    private static volatile Method hasCompletedTask;
    private static volatile Method hasRelic;
    private static volatile Method relicDefinitions;
    private static volatile Method selectedRelicIds;
    private static volatile Method canSelectRelic;
    private static volatile Method relicId;
    private static volatile Method relicName;
    private static volatile Method relicDescription;
    private static volatile Method relicTier;

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

    public static boolean canSelectRelic(String id) {
        try {
            Object player = player();
            if (player == null || id == null) return false;
            return Boolean.TRUE.equals(canSelectRelic.invoke(null, player, id));
        } catch (Throwable failure) {
            return false;
        }
    }

    public static Set<String> completedTasks() {
        try {
            Object player = player();
            if (player == null) return Collections.emptySet();
            return iterableStringSet(completedTasks.invoke(null, player));
        } catch (Throwable failure) {
            return Collections.emptySet();
        }
    }

    /** Only currently registered Demonic Pacts relic IDs are exposed to the UI. */
    public static Set<String> unlockedRelics() {
        try {
            Object player = player();
            if (player == null) return Collections.emptySet();
            return iterableStringSet(selectedRelicIds.invoke(null, player));
        } catch (Throwable failure) {
            return Collections.emptySet();
        }
    }

    public static List<RelicDefinition> relicDefinitions() {
        try {
            resolve();
            Object result = relicDefinitions.invoke(null);
            if (!(result instanceof Iterable)) return Collections.emptyList();

            ArrayList<RelicDefinition> copy = new ArrayList<RelicDefinition>();
            for (Object effect : (Iterable<?>) result) {
                if (effect == null) continue;
                Object idValue = relicId.invoke(effect);
                Object nameValue = relicName.invoke(effect);
                Object descriptionValue = relicDescription.invoke(effect);
                Object tierValue = relicTier.invoke(effect);
                if (idValue == null || nameValue == null || tierValue == null) continue;
                copy.add(new RelicDefinition(
                        idValue.toString(),
                        nameValue.toString(),
                        descriptionValue == null ? "" : descriptionValue.toString(),
                        ((Number) tierValue).intValue()));
            }
            Collections.sort(copy, new Comparator<RelicDefinition>() {
                @Override
                public int compare(RelicDefinition a, RelicDefinition b) {
                    int tierCompare = Integer.compare(a.tier, b.tier);
                    if (tierCompare != 0) return tierCompare;
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
            return Collections.unmodifiableList(copy);
        } catch (Throwable failure) {
            return Collections.emptyList();
        }
    }

    public static boolean selectRelic(String id) {
        if (!safeRelicId(id)) return false;
        boolean queued = InProcessBootstrap.LocalCommands.commandLine("relic " + id);
        if (queued) System.out.println("SINGLEPLAYER_LEAGUE_UI: RELIC_SELECTION_QUEUED id=" + id);
        return queued;
    }

    public static boolean resetRelics() {
        boolean queued = InProcessBootstrap.LocalCommands.commandLine("resetrelics");
        if (queued) System.out.println("SINGLEPLAYER_LEAGUE_UI: RELIC_RESET_QUEUED");
        return queued;
    }

    private static boolean safeRelicId(String id) {
        if (id == null || id.isEmpty()) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c == '_' || c == '-' || Character.isLetterOrDigit(c))) return false;
        }
        return true;
    }

    private static Set<String> iterableStringSet(Object result) {
        if (!(result instanceof Iterable)) return Collections.emptySet();
        LinkedHashSet<String> copy = new LinkedHashSet<String>();
        for (Object value : (Iterable<?>) result) {
            if (value != null) copy.add(value.toString());
        }
        return Collections.unmodifiableSet(copy);
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
        hasCompletedTask = runtime.getMethod("hasCompletedTask", playerClass, String.class);
        hasRelic = runtime.getMethod("hasRelic", playerClass, String.class);

        Class<?> relicRegistry = Class.forName("core.local.league.LeagueRelics");
        relicDefinitions = relicRegistry.getMethod("definitions");
        selectedRelicIds = relicRegistry.getMethod("selectedIds", playerClass);
        canSelectRelic = relicRegistry.getMethod("canSelect", playerClass, String.class);

        Class<?> effect = Class.forName("core.local.league.LeagueRelicEffect");
        relicId = effect.getMethod("getId");
        relicName = effect.getMethod("getName");
        relicDescription = effect.getMethod("getDescription");
        relicTier = effect.getMethod("getTier");
    }

    private static String playerName() {
        String value = System.getProperty("singlePlayerName", "Player").trim();
        return value.isEmpty() ? "Player" : value;
    }

    public static final class RelicDefinition {
        public final String id;
        public final String name;
        public final String description;
        public final int tier;

        RelicDefinition(String id, String name, String description, int tier) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.tier = tier;
        }
    }
}
