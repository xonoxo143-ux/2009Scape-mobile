package rt4;

/**
 * Small semantic adapter between retained RT4 call sites and the single-player
 * local world authority.
 *
 * RT4 classes keep their original UI, pathfinding, crosshair and selection
 * behavior. Only the final protocol-write portion is conditional: when the
 * existing 2009Scape typed command can be delivered in-process, no legacy
 * packet is serialized. Returning false deliberately preserves the original
 * wire path as a migration fallback.
 */
public final class LocalClientCommands {
    private LocalClientCommands() {}

    private static boolean enabled() {
        return Boolean.getBoolean("singleplayer");
    }

    private static boolean result(String label, boolean direct) {
        if (direct) {
            System.out.println("SINGLEPLAYER_LOCAL_COMMAND: " + label + "_DIRECT");
        } else {
            System.err.println("SINGLEPLAYER_LOCAL_COMMAND: " + label + "_FALLBACK");
        }
        return direct;
    }

    public static boolean npcAction(int option, int npcIndex) {
        if (!enabled()) return false;
        return result(
                "NPC_ACTION option=" + option + " index=" + npcIndex,
                singleplayer.InProcessBootstrap.LocalCommands.npcAction(option, npcIndex));
    }

    public static boolean playerAction(int option, int playerIndex) {
        if (!enabled()) return false;
        return result(
                "PLAYER_ACTION option=" + option + " index=" + playerIndex,
                singleplayer.InProcessBootstrap.LocalCommands.playerAction(option, playerIndex));
    }

    public static boolean sceneryAction(int option, int id, int x, int y) {
        if (!enabled()) return false;
        return result(
                "SCENERY_ACTION option=" + option + " id=" + id + " at=" + x + "," + y,
                singleplayer.InProcessBootstrap.LocalCommands.sceneryAction(option, id, x, y));
    }

    public static boolean groundItemAction(int option, int id, int x, int y) {
        if (!enabled()) return false;
        return result(
                "GROUND_ITEM_ACTION option=" + option + " id=" + id + " at=" + x + "," + y,
                singleplayer.InProcessBootstrap.LocalCommands.groundItemAction(option, id, x, y));
    }

    public static boolean itemAction(
            int option,
            int itemId,
            int slot,
            int componentHash) {
        if (!enabled()) return false;
        int iface = componentHash >>> 16;
        int child = componentHash & 0xFFFF;
        return result(
                "ITEM_ACTION option=" + option + " id=" + itemId
                        + " iface=" + iface + " child=" + child + " slot=" + slot,
                singleplayer.InProcessBootstrap.LocalCommands.itemAction(
                        option, itemId, slot, iface, child));
    }

    public static boolean interfaceItemAction(
            int opcode,
            int option,
            int itemId,
            int slot,
            int componentHash) {
        if (!enabled()) return false;
        int iface = componentHash >>> 16;
        int child = componentHash & 0xFFFF;
        return result(
                "IF_ITEM_ACTION option=" + option + " id=" + itemId
                        + " iface=" + iface + " child=" + child + " slot=" + slot,
                singleplayer.InProcessBootstrap.LocalCommands.interfaceAction(
                        opcode, option, iface, child, slot, itemId));
    }

    public static boolean itemExamine(int id) {
        if (!enabled()) return false;
        int decodedId = id & 0xFFFF;
        return result(
                "ITEM_EXAMINE id=" + decodedId,
                singleplayer.InProcessBootstrap.LocalCommands.itemExamine(decodedId));
    }

    public static boolean sceneryExamine(int id) {
        if (!enabled()) return false;
        int decodedId = id & 0xFFFF;
        return result(
                "SCENERY_EXAMINE id=" + decodedId,
                singleplayer.InProcessBootstrap.LocalCommands.sceneryExamine(decodedId));
    }

    public static boolean npcExamine(int id) {
        if (!enabled()) return false;
        int decodedId = id & 0xFFFF;
        return result(
                "NPC_EXAMINE id=" + decodedId,
                singleplayer.InProcessBootstrap.LocalCommands.npcExamine(decodedId));
    }

    public static boolean trackingDisplay(
            int windowMode,
            int width,
            int height,
            int displayMode) {
        if (!enabled()) return false;
        return result(
                "DISPLAY_UPDATE " + width + "x" + height + " mode=" + windowMode,
                singleplayer.InProcessBootstrap.LocalCommands.trackingDisplay(
                        windowMode, width, height, displayMode));
    }

    public static boolean trackingFocus(boolean focused) {
        if (!enabled()) return false;
        return result(
                "FOCUS focused=" + focused,
                singleplayer.InProcessBootstrap.LocalCommands.trackingFocus(focused));
    }

    public static boolean trackingCamera(int x, int y) {
        if (!enabled()) return false;
        return result(
                "CAMERA x=" + x + " y=" + y,
                singleplayer.InProcessBootstrap.LocalCommands.trackingCamera(x, y));
    }

    public static boolean trackingMouseClick(
            int x,
            int y,
            boolean rightClick,
            int delay) {
        if (!enabled()) return false;
        return result(
                "MOUSE_CLICK x=" + x + " y=" + y + " right=" + rightClick,
                singleplayer.InProcessBootstrap.LocalCommands.trackingMouseClick(
                        x, y, rightClick, delay));
    }
}