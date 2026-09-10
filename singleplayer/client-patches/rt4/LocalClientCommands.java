package rt4;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Semantic adapter between retained RT4 call sites and the single-player local
 * world authority.
 *
 * There are two migration levels here:
 *
 * 1. Preferred direct calls use the existing typed 2009Scape Packet subclasses
 *    without serializing anything.
 * 2. Retained RT4 call sites that have not been converted yet may still build
 *    their historical packet payload. Packet.java captures that completed
 *    payload before the socket boundary and this class feeds it directly into
 *    the existing 2009Scape Decoders530 decoder in memory. That preserves the
 *    proven decoder and game logic while removing the kernel/TCP hop.
 *
 * Returning false always means "leave the original wire packet untouched" so
 * unsupported or not-yet-ready operations retain the legacy fallback during
 * the transition.
 */
public final class LocalClientCommands {
    private static volatile Method getPlayerByName;
    private static volatile Method decodePacket;
    private static volatile Method enqueuePacket;
    private static volatile Constructor<?> ioBufferConstructor;
    private static volatile int[] packetSizes;
    private static volatile Class<?> noProcessClass;
    private static volatile Class<?> unhandledClass;
    private static volatile Class<?> decodingErrorClass;

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

    /**
     * Route one completed historical RT4 gameplay packet through the retained
     * server decoder without a socket. wirePayload starts immediately after the
     * ISAAC opcode byte and therefore still includes a one/two-byte variable
     * packet length prefix when the protocol uses one.
     */
    public static boolean routeEncodedPacket(int opcode, byte[] wirePayload) {
        if (!enabled() || opcode < 0 || opcode > 255 || wirePayload == null) {
            return false;
        }

        try {
            resolveLegacyDecoder();
            Object player = getPlayerByName.invoke(null, playerName());
            if (player == null) {
                return false;
            }

            byte[] payload = stripAndValidateLengthHeader(opcode, wirePayload);
            if (payload == null) {
                return false;
            }

            Object ioBuffer = ioBufferConstructor.newInstance(
                    opcode,
                    null,
                    ByteBuffer.wrap(payload));
            Object decoded = decodePacket.invoke(null, player, opcode, ioBuffer);
            if (decoded == null || unhandledClass.isInstance(decoded)) {
                return false;
            }
            if (decodingErrorClass.isInstance(decoded)) {
                System.err.println(
                        "SINGLEPLAYER_LOCAL_PACKET: decoder rejected opcode=" + opcode
                                + " payloadBytes=" + payload.length + " result=" + decoded);
                return false;
            }

            if (!noProcessClass.isInstance(decoded)) {
                enqueuePacket.invoke(null, decoded);
            }

            System.out.println(
                    "SINGLEPLAYER_LOCAL_PACKET: ROUTED opcode=" + opcode
                            + " payloadBytes=" + payload.length
                            + " type=" + decoded.getClass().getSimpleName());
            return true;
        } catch (Throwable failure) {
            System.err.println(
                    "SINGLEPLAYER_LOCAL_PACKET: fallback opcode=" + opcode
                            + " because " + failure);
            return false;
        }
    }

    private static byte[] stripAndValidateLengthHeader(int opcode, byte[] wirePayload) {
        int header = packetSizes[opcode];
        if (header >= 0) {
            if (wirePayload.length != header) {
                return null;
            }
            return wirePayload;
        }

        if (header == -1) {
            if (wirePayload.length < 1) {
                return null;
            }
            int declared = wirePayload[0] & 0xFF;
            if (declared != wirePayload.length - 1) {
                return null;
            }
            return Arrays.copyOfRange(wirePayload, 1, wirePayload.length);
        }

        if (header == -2) {
            if (wirePayload.length < 2) {
                return null;
            }
            int declared = ((wirePayload[0] & 0xFF) << 8) | (wirePayload[1] & 0xFF);
            if (declared != wirePayload.length - 2) {
                return null;
            }
            return Arrays.copyOfRange(wirePayload, 2, wirePayload.length);
        }

        // -3 means the retained server has no packet shape for this opcode.
        return null;
    }

    private static synchronized void resolveLegacyDecoder() throws Exception {
        if (decodePacket != null) {
            return;
        }

        Class<?> playerClass = Class.forName("core.game.node.entity.player.Player");
        Class<?> repository = Class.forName("core.game.world.repository.Repository");
        getPlayerByName = repository.getMethod("getPlayerByName", String.class);

        Class<?> ioBufferClass = Class.forName("core.net.packet.IoBuffer");
        Class<?> packetHeaderClass = Class.forName("core.net.packet.PacketHeader");
        ioBufferConstructor = ioBufferClass.getConstructor(
                int.class,
                packetHeaderClass,
                ByteBuffer.class);

        Class<?> packetClass = Class.forName("core.net.packet.in.Packet");
        Class<?> decodersClass = Class.forName("core.net.packet.in.Decoders530");
        decodePacket = decodersClass.getMethod(
                "process",
                playerClass,
                int.class,
                ioBufferClass);

        Class<?> processorClass = Class.forName("core.net.packet.PacketProcessor");
        enqueuePacket = processorClass.getMethod("enqueue", packetClass);

        Class<?> readEventClass = Class.forName("core.net.event.GameReadEvent");
        Field packetSizesField = readEventClass.getField("PACKET_SIZES");
        packetSizes = (int[]) packetSizesField.get(null);

        noProcessClass = Class.forName("core.net.packet.in.Packet$NoProcess");
        unhandledClass = Class.forName("core.net.packet.in.Packet$UnhandledOp");
        decodingErrorClass = Class.forName("core.net.packet.in.Packet$DecodingError");
    }

    private static String playerName() {
        return System.getProperty("singlePlayerName", "Player");
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