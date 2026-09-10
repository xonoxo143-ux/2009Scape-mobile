package rt4;

import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

/** Retained RT4 friends UI with single-player commands routed in-process. */
public class FriendsList {
    @OriginalMember(owner = "client!hd", name = "g", descriptor = "[J")
    public static final long[] encodedUsernames = new long[200];
    @OriginalMember(owner = "client!rg", name = "r", descriptor = "[Lclient!na;")
    public static final JagString[] worldNames = new JagString[200];
    @OriginalMember(owner = "client!ic", name = "l", descriptor = "[I")
    public static final int[] ranks = new int[200];
    @OriginalMember(owner = "client!ia", name = "d", descriptor = "[I")
    public static final int[] worlds = new int[200];
    @OriginalMember(owner = "client!jh", name = "b", descriptor = "[Lclient!na;")
    public static final JagString[] usernames = new JagString[200];
    @OriginalMember(owner = "client!ab", name = "c", descriptor = "[Z")
    public static final boolean[] sameGame = new boolean[200];
    @OriginalMember(owner = "client!p", name = "d", descriptor = "I")
    public static int transmitAt = 0;
    @OriginalMember(owner = "client!nc", name = "m", descriptor = "I")
    public static int state = 0;
    @OriginalMember(owner = "client!al", name = "m", descriptor = "I")
    public static int size = 0;

    private static String decodeName(long encoded) {
        JagString value = Base37.decode37(encoded);
        return value == null ? "" : value.toString();
    }

    @OriginalMember(owner = "client!hj", name = "a", descriptor = "(Lclient!na;B)Z")
    public static boolean contains(@OriginalArg(0) JagString arg0) {
        if (arg0 == null) return false;
        for (@Pc(12) int i = 0; i < size; i++) {
            if (arg0.equalsIgnoreCase(usernames[i])) return true;
        }
        return arg0.equalsIgnoreCase(PlayerList.self.username);
    }

    @OriginalMember(owner = "client!fb", name = "a", descriptor = "(JB)V")
    public static void add(@OriginalArg(0) long encoded) {
        if (encoded == 0L) return;
        if (size >= 100 && !LoginManager.playerMember || size >= 200) {
            Chat.add(JagString.EMPTY, 0, LocalizedText.FRIENDLISTFULL);
            return;
        }
        @Pc(35) JagString displayName = Base37.decode37(encoded).toTitleCase();
        @Pc(42) int i;
        for (i = 0; i < size; i++) {
            if (encodedUsernames[i] == encoded) {
                Chat.add(JagString.EMPTY, 0, JagString.concatenate(new JagString[]{displayName, LocalizedText.FRIENDLISTDUPE}));
                return;
            }
        }
        for (i = 0; i < IgnoreList.size; i++) {
            if (encoded == IgnoreList.encodedUsernames[i]) {
                Chat.add(JagString.EMPTY, 0, JagString.concatenate(new JagString[]{LocalizedText.REMOVESOCIAL1, displayName, LocalizedText.REMOVEIGNORE}));
                return;
            }
        }
        if (displayName.strEquals(PlayerList.self.username)) {
            Chat.add(JagString.EMPTY, 0, LocalizedText.FRIENDCANTADDSELF);
            return;
        }
        usernames[size] = displayName;
        encodedUsernames[size] = encoded;
        worlds[size] = 0;
        worldNames[size] = JagString.EMPTY;
        ranks[size] = 0;
        sameGame[size] = false;
        size++;
        transmitAt = InterfaceList.transmitTimer;

        if (Boolean.getBoolean("singleplayer")
                && singleplayer.InProcessBootstrap.LocalCommands.addFriend(decodeName(encoded))) {
            System.out.println("SINGLEPLAYER_LOCAL_COMMAND: ADD_FRIEND_DIRECT");
            return;
        }
        Protocol.outboundBuffer.p1isaac(ClientProt.FRIENDLIST_ADD);
        Protocol.outboundBuffer.p8(encoded);
    }

    @OriginalMember(owner = "client!pi", name = "a", descriptor = "(JI)V")
    public static void remove(@OriginalArg(0) long encoded) {
        if (encoded == 0L) return;
        for (@Pc(13) int i = 0; i < size; i++) {
            if (encodedUsernames[i] == encoded) {
                size--;
                for (@Pc(41) int j = i; j < size; j++) {
                    usernames[j] = usernames[j + 1];
                    worlds[j] = worlds[j + 1];
                    worldNames[j] = worldNames[j + 1];
                    encodedUsernames[j] = encodedUsernames[j + 1];
                    ranks[j] = ranks[j + 1];
                    sameGame[j] = sameGame[j + 1];
                }
                transmitAt = InterfaceList.transmitTimer;
                if (Boolean.getBoolean("singleplayer")
                        && singleplayer.InProcessBootstrap.LocalCommands.removeFriend(decodeName(encoded))) {
                    System.out.println("SINGLEPLAYER_LOCAL_COMMAND: REMOVE_FRIEND_DIRECT");
                    break;
                }
                Protocol.outboundBuffer.p1isaac(ClientProt.FRIENDLIST_DEL);
                Protocol.outboundBuffer.p8(encoded);
                break;
            }
        }
    }

    @OriginalMember(owner = "client!ni", name = "a", descriptor = "(ILclient!na;I)V")
    public static void setRank(@OriginalArg(1) JagString username, @OriginalArg(2) int rank) {
        long encoded = username.encode37();
        if (Boolean.getBoolean("singleplayer")
                && singleplayer.InProcessBootstrap.LocalCommands.setClanRank(decodeName(encoded), rank)) {
            System.out.println("SINGLEPLAYER_LOCAL_COMMAND: SET_CLAN_RANK_DIRECT");
            return;
        }
        Protocol.outboundBuffer.p1isaac(ClientProt.FRIEND_SETRANK);
        Protocol.outboundBuffer.p1add(rank);
        Protocol.outboundBuffer.p8(encoded);
    }

    @OriginalMember(owner = "client!ac", name = "a", descriptor = "(Lclient!na;I)I")
    public static int indexOf(@OriginalArg(0) JagString arg0) {
        if (arg0 == null) return -1;
        for (@Pc(20) int i = 0; i < size; i++) {
            if (arg0.equalsIgnoreCase(usernames[i])) return i;
        }
        return -1;
    }
}
