package singleplayer;

/**
 * Authoritative configuration for the local single-player runtime.
 *
 * Values that describe one world must live here rather than being duplicated as
 * client/server settings. Legacy subsystems may temporarily read the mirrored
 * system properties during migration, but those properties are published only
 * from this class.
 */
public final class GameConfig {
    public static final String VIEW_DISTANCE_PROPERTY = "singleplayer.viewDistance";
    public static final int DEFAULT_VIEW_DISTANCE = 28;
    public static final int MIN_VIEW_DISTANCE = 15;
    public static final int MAX_LEGACY_SCENE_VIEW_DISTANCE = 48;

    private final int viewDistance;

    private GameConfig(int viewDistance) {
        this.viewDistance = clamp(
                viewDistance,
                MIN_VIEW_DISTANCE,
                MAX_LEGACY_SCENE_VIEW_DISTANCE);
    }

    public static GameConfig load() {
        int requested = Integer.getInteger(
                VIEW_DISTANCE_PROPERTY,
                DEFAULT_VIEW_DISTANCE);
        return new GameConfig(requested);
    }

    /** Publish temporary compatibility properties before legacy classes load. */
    public void publishLegacyProperties() {
        System.setProperty(VIEW_DISTANCE_PROPERTY, Integer.toString(viewDistance));
    }

    public int viewDistance() {
        return viewDistance;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return "GameConfig{viewDistance=" + viewDistance + '}';
    }
}
