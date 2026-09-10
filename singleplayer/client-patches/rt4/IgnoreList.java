package rt4;

import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

/** Retained RT4 ignore UI with single-player commands routed in-process. */
public class IgnoreList {
    @OriginalMember(owner = "client!pf", name = "h", descriptor = "[J")
    public static final long[] encodedUsernames = new long[100];
    @OriginalMember(owner = "client!pi", name = "V", descriptor = "[Lclient!na;")
    public static final JagString[] usernames = new JagString[100];
    @OriginalMember(owner = "client!cl", name = "Z", descriptor = "I")
    public static int size = 0;

    private static String decodeName(long encoded) {
        JagString value = Base37.decode37(encoded);
        return value == null ? "" : value.toString();
    }

    @OriginalMember(owner = "client!te", name = "b", descriptor = "(Lclient!na;I)Z")
    public static boolean contains(@OriginalArg(0) JagString arg0) {
        if (arg0 == null) return false;
        for (@Pc(11) int i = 0; i < size; i++) {
            if (arg0.equalsIgnoreCase(usernames[i])) return true;
        }
        return false;
    }

    @OriginalMember(owner = "client!la", name = "a", descriptor = "(IJ)V")
    public static void add(@OriginalArg(1) long encoded) {
        if (encoded == 0L) return;
        if (size >= 100) {
            Chat.add(JagString.EMPTY, 0, LocalizedText.IGNORELISTFULL);
            return;
        }
        @Pc(34) JagString displayName = Base37.decode37(encoded).toTitleCase();
        @Pc(36) int i;
        for (i = 0; i < size; i++) {
            if (encodedUsernames[i] == encoded) {
                Chat.add(JagString.EMPTY, 0, JagString.concatenate(new JagString[]{displayName, LocalizedText.IGNORELISTDUPE}));
                return;
            }
        }
        for (i = 0; i < FriendsList.size; i++) {
            if (FriendsList.encodedUsernames[i] == encoded) {
                Chat.add(JagString.EMPTY, 0, JagString.concatenate(new JagString[]{LocalizedText.REMOVESOCIAL2, displayName, LocalizedText.REMOVEFRIEND}));
                return;
            }
        }
        if (displayName.strEquals(PlayerList.self.username)) {
            Chat.add(JagString.EMPTY, 0, LocalizedText.IGNORECANTADDSELF);
            return;
        }
        encodedUsernames[size] = encoded;
        usernames[size++] = Base37.decode37(encoded);
        FriendsList.transmitAt = InterfaceList.transmitTimer;

        if (Boolean.getBoolean("singleplayer")
                && singleplayer.InProcessBootstrap.LocalCommands.addIgnore(decodeName(encoded))) {
            System.out.println("SINGLEPLAYER_LOCAL_COMMAND: ADD_IGNORE_DIRECT");
            return;
        }
        Protocol.outboundBuffer.p1isaac(ClientProt.IGNORELIST_ADD);
        Protocol.outboundBuffer.p8(encoded);
    }

    @OriginalMember(owner = "client!fh", name = "a", descriptor = "(JI)V")
    public static void remove(@OriginalArg(0) long encoded) {
        if (encoded == 0L) return;
        for (@Pc(12) int i = 0; i < size; i++) {
            if (encodedUsernames[i] == encoded) {
                size--;
                for (@Pc(36) int j = i; j < size; j++) {
                    encodedUsernames[j] = encodedUsernames[j + 1];
                    usernames[j] = usernames[j + 1];
                }
                FriendsList.transmitAt = InterfaceList.transmitTimer;
                if (Boolean.getBoolean("singleplayer")
                        && singleplayer.InProcessBootstrap.LocalCommands.removeIgnore(decodeName(encoded))) {
                    System.out.println("SINGLEPLAYER_LOCAL_COMMAND: REMOVE_IGNORE_DIRECT");
                    break;
                }
                Protocol.outboundBuffer.p1isaac(ClientProt.IGNORELIST_DEL);
                Protocol.outboundBuffer.p8(encoded);
                break;
            }
        }
    }
}
