package rt4;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Publishes whether the ordinary public-chat entry line is currently safe for
 * Android to own as a soft-keyboard trigger.
 *
 * The bottom-left chat area is reused by RT4 for dialogue and scripted chatbox
 * interfaces. Android cannot distinguish those states from coordinates alone,
 * so expose the retained client state through a tiny transition-only flag in
 * the shared single-player data directory.
 */
public final class LocalChatInputBridge {
    private static final int CHAT_TOP_INTERFACE = 752;
    private static final int DEFAULT_CHATBOX_INTERFACE = 137;
    private static final int CS_CHATBOX_CHILD = 6;
    private static final int CHATBOX_CHILD = 8;
    private static final int DIALOGUE_CHILD = 12;
    private static final String FLAG_NAME = "singleplayer-chat-input-enabled.flag";

    private static Boolean lastEnabled;
    private static File flagFile;

    private LocalChatInputBridge() {}

    public static void tick() {
        if (!Boolean.getBoolean("singleplayer")) return;

        int csChatbox = mountedInterfaceId(CS_CHATBOX_CHILD);
        int chatbox = mountedInterfaceId(CHATBOX_CHILD);
        int dialogue = mountedInterfaceId(DIALOGUE_CHILD);

        // Android may own the soft keyboard only while the stock public-chat
        // interface is actually mounted. Dialogue/options and client-script
        // chatboxes occupy the same screen space and must remain RT4-owned.
        boolean enabled = client.gameState == 30
                && chatbox == DEFAULT_CHATBOX_INTERFACE
                && csChatbox == -1
                && dialogue == -1;

        if (lastEnabled != null && lastEnabled.booleanValue() == enabled) return;
        lastEnabled = enabled;

        try {
            File flag = getFlagFile();
            if (enabled) {
                Files.write(
                        flag.toPath(),
                        "normal-chat\n".getBytes(StandardCharsets.UTF_8));
                System.out.println(
                        "SINGLEPLAYER_INPUT: CHAT_ENTRY_AVAILABLE chatbox=" + chatbox);
            } else {
                Files.deleteIfExists(flag.toPath());
                System.out.println(
                        "SINGLEPLAYER_INPUT: CHAT_ENTRY_BLOCKED state=" + client.gameState
                                + " chatbox=" + chatbox
                                + " dialogue=" + dialogue
                                + " cs=" + csChatbox);
            }
        } catch (IOException failure) {
            System.err.println("SINGLEPLAYER_INPUT: CHAT_ENTRY_FLAG_FAILED " + failure);
        }
    }

    private static int mountedInterfaceId(int child) {
        int hostId = (CHAT_TOP_INTERFACE << 16) | child;
        ComponentPointer pointer = (ComponentPointer) InterfaceList.openInterfaces.get(hostId);
        return pointer == null ? -1 : pointer.interfaceId;
    }

    private static File getFlagFile() {
        if (flagFile == null) {
            String home = System.getProperty("clientHomeOverride", ".");
            flagFile = new File(home, FLAG_NAME);
        }
        return flagFile;
    }
}
