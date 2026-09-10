package rt4;

import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

/** Retained RT4 clan-chat UI with local single-player command routing. */
public class ClanChat {
    @OriginalMember(owner = "client!wj", name = "l", descriptor = "I")
    public static int transmitAt = 0;
    @OriginalMember(owner = "client!mj", name = "u", descriptor = "B")
    public static byte rank;
    @OriginalMember(owner = "client!e", name = "rc", descriptor = "B")
    public static byte minKick;
    @OriginalMember(owner = "client!wb", name = "m", descriptor = "Lclient!na;")
    public static JagString owner = null;
    @OriginalMember(owner = "client!be", name = "ac", descriptor = "Lclient!na;")
    public static JagString name = null;
    @OriginalMember(owner = "client!rg", name = "y", descriptor = "I")
    public static int size;
    @OriginalMember(owner = "client!qc", name = "bb", descriptor = "[Lclient!kl;")
    public static ClanMember[] members;

    private static String decodeName(long encoded) {
        if (encoded == 0L) return "";
        JagString value = Base37.decode37(encoded);
        return value == null ? "" : value.toString();
    }

    @OriginalMember(owner = "client!kh", name = "b", descriptor = "(I)V")
    public static void leave() {
        if (Boolean.getBoolean("singleplayer")
                && singleplayer.InProcessBootstrap.LocalCommands.joinClan("")) {
            System.out.println("SINGLEPLAYER_LOCAL_COMMAND: CLAN_LEAVE_DIRECT");
            return;
        }
        Protocol.outboundBuffer.p1isaac(ClientProt.CLAN_JOINCHAT_LEAVECHAT);
        Protocol.outboundBuffer.p8(0L);
    }

    @OriginalMember(owner = "client!mf", name = "a", descriptor = "(JI)V")
    public static void join(@OriginalArg(0) long encoded) {
        if (encoded == 0L) return;
        if (Boolean.getBoolean("singleplayer")
                && singleplayer.InProcessBootstrap.LocalCommands.joinClan(decodeName(encoded))) {
            System.out.println("SINGLEPLAYER_LOCAL_COMMAND: CLAN_JOIN_DIRECT");
            return;
        }
        Protocol.outboundBuffer.p1isaac(ClientProt.CLAN_JOINCHAT_LEAVECHAT);
        Protocol.outboundBuffer.p8(encoded);
    }

    @OriginalMember(owner = "client!od", name = "a", descriptor = "(ILclient!na;)V")
    public static void kick(@OriginalArg(1) JagString username) {
        if (members == null) return;
        @Pc(22) long encoded = username.encode37();
        @Pc(24) int i = 0;
        if (encoded == 0L) return;
        while (members.length > i && members[i].key != encoded) i++;
        if (i < members.length && members[i] != null) {
            long memberKey = members[i].key;
            if (Boolean.getBoolean("singleplayer")
                    && singleplayer.InProcessBootstrap.LocalCommands.kickFromClan(decodeName(memberKey))) {
                System.out.println("SINGLEPLAYER_LOCAL_COMMAND: CLAN_KICK_DIRECT");
                return;
            }
            Protocol.outboundBuffer.p1isaac(ClientProt.CLAN_KICKUSER);
            Protocol.outboundBuffer.p8(memberKey);
        }
    }
}
