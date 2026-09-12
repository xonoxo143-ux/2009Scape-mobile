package rt4;

import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.util.Arrays;

@OriginalClass("client!i")
public final class Packet extends Buffer {

    @OriginalMember(owner = "client!bh", name = "G", descriptor = "[I")
    public static final int[] BIT_MASKS = new int[]{0, 1, 3, 7, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191, 16383, 32767, 65535, 131071, 262143, 524287, 1048575, 2097151, 4194303, 8388607, 16777215, 33554431, 67108863, 134217727, 268435455, 536870911, 1073741823, Integer.MAX_VALUE, -1};

    @OriginalMember(owner = "client!i", name = "Xb", descriptor = "Lclient!ij;")
    private IsaacRandom isaac;

    @OriginalMember(owner = "client!i", name = "fc", descriptor = "I")
    private int bitOffset;

    /*
     * Compatibility boundary for the handful of retained call sites that still
     * describe their semantic action with the old 530 payload layout. The bytes
     * are consumed in-process and are never permitted to reach BufferedSocket.
     * New/ordinary interactions should use LocalClientCommands directly.
     */
    private int localPacketStart = -1;
    private int localPayloadStart = -1;
    private int localOpcode = -1;

    /*
     * Revision-530 transport/server-hosting ceremony that has no useful local
     * gameplay meaning. Keep retained RT4 call sites compatible during the
     * migration, but consume these packets before the compatibility decoder.
     *
     * 20/110  map rebuild acknowledgements: server NoProcess
     * 21      camera telemetry
     * 22      focus telemetry
     * 75/123  mouse telemetry
     * 93      remote keepalive
     * 98      player preference telemetry
     * 99      hosted abuse-report/moderation transport
     * 177     remote packet-count verification
     * 245     remote AFK logout
     */
    private static final boolean[] localDiscardAnnounced = new boolean[256];

    @OriginalMember(owner = "client!i", name = "<init>", descriptor = "(I)V")
    public Packet(@OriginalArg(0) int arg0) {
        super(arg0);
    }

    @OriginalMember(owner = "client!i", name = "q", descriptor = "(B)V")
    public final void accessBits() {
        this.bitOffset = this.offset * 8;
    }

    @OriginalMember(owner = "client!i", name = "a", descriptor = "(BI[BI)V")
    public final void gBytesIsaac(@OriginalArg(2) byte[] arg0, @OriginalArg(3) int arg1) {
        for (@Pc(17) int local17 = 0; local17 < arg1; local17++) {
            arg0[local17] = (byte) (this.data[this.offset++] - this.isaac.getNextKey());
        }
    }

    @OriginalMember(owner = "client!i", name = "f", descriptor = "(BI)I")
    public final int gBits(@OriginalArg(1) int arg0) {
        @Pc(6) int local6 = this.bitOffset >> 3;
        @Pc(14) int local14 = 8 - (this.bitOffset & 0x7);
        @Pc(16) int local16 = 0;
        this.bitOffset += arg0;
        while (local14 < arg0) {
            local16 += (BIT_MASKS[local14] & this.data[local6++]) << arg0 - local14;
            arg0 -= local14;
            local14 = 8;
        }
        if (local14 == arg0) {
            local16 += this.data[local6] & BIT_MASKS[local14];
        } else {
            local16 += this.data[local6] >> local14 - arg0 & BIT_MASKS[arg0];
        }
        return local16;
    }

    @OriginalMember(owner = "client!i", name = "a", descriptor = "([IZ)V")
    public final void setKey(@OriginalArg(0) int[] arg0) {
        this.isaac = new IsaacRandom(arg0);
    }

    @OriginalMember(owner = "client!i", name = "q", descriptor = "(II)I")
    public final int method2241(@OriginalArg(0) int arg0) {
        return arg0 * 8 - this.bitOffset;
    }

    private static boolean localGameplayReady() {
        return Boolean.getBoolean("singleplayer")
                && (client.gameState == 25 || client.gameState == 30);
    }

    private static boolean isTransportOnlySinglePlayerSignal(int opcode) {
        switch (opcode) {
            case 20:
            case 21:
            case 22:
            case 75:
            case 93:
            case 98:
            case 99:
            case 110:
            case 123:
            case 177:
            case 245:
                return true;
            default:
                return false;
        }
    }

    private static void announceDiscard(int opcode, int payloadBytes) {
        if (opcode < 0 || opcode >= localDiscardAnnounced.length) return;
        synchronized (localDiscardAnnounced) {
            if (localDiscardAnnounced[opcode]) return;
            localDiscardAnnounced[opcode] = true;
        }
        System.out.println(
                "SINGLEPLAYER_LOCAL_PACKET: TRANSPORT_ONLY_REMOVED opcode=" + opcode
                        + " payloadBytes=" + payloadBytes);
    }

    /**
     * Finish the current retained outbound action. Every in-world action is
     * consumed here: either by the typed local command path, by the in-memory
     * compatibility decoder, or as obsolete transport-only bookkeeping. The
     * packet is always rewound, so the local RT4 presentation stream can never
     * become a client->server gameplay transport again.
     */
    public final void finishLocalPacket() {
        if (this.localOpcode < 0) {
            return;
        }

        int packetStart = this.localPacketStart;
        int payloadStart = this.localPayloadStart;
        int opcode = this.localOpcode;
        int end = this.offset;

        this.localPacketStart = -1;
        this.localPayloadStart = -1;
        this.localOpcode = -1;

        // Login/reconnect/world-list protocol remains byte-for-byte legacy until
        // that state machine is intentionally removed. Only in-world gameplay is
        // subject to the no-transport invariant.
        if (!localGameplayReady() || this != Protocol.outboundBuffer) {
            return;
        }
        if (packetStart < 0 || payloadStart != packetStart + 1
                || payloadStart > end || end > this.data.length) {
            System.err.println(
                    "SINGLEPLAYER_LOCAL_PACKET: INVALID_BOUNDARY opcode=" + opcode);
            if (packetStart >= 0 && packetStart <= this.offset) this.offset = packetStart;
            return;
        }

        byte[] wirePayload = Arrays.copyOfRange(this.data, payloadStart, end);
        boolean consumed;
        if (isTransportOnlySinglePlayerSignal(opcode)) {
            announceDiscard(opcode, wirePayload.length);
            consumed = true;
        } else {
            consumed = LocalClientCommands.routeEncodedPacket(opcode, wirePayload);
            if (!consumed) {
                System.err.println(
                        "SINGLEPLAYER_LOCAL_PACKET: UNMIGRATED_DROPPED opcode=" + opcode
                                + " payloadBytes=" + wirePayload.length);
            }
        }

        // Critical invariant: regardless of semantic outcome, no in-world client
        // packet survives to BufferedSocket.write().
        this.offset = packetStart;
    }

    @OriginalMember(owner = "client!i", name = "r", descriptor = "(II)V")
    public final void p1isaac(@OriginalArg(1) int arg0) {
        if (Boolean.getBoolean("singleplayer") && this == Protocol.outboundBuffer) {
            // Opening a new opcode is an unambiguous boundary for the previous
            // packet, including variable-length packets whose size byte has
            // already been backfilled by the original RT4 call site.
            finishLocalPacket();
            this.localPacketStart = this.offset;
            this.localOpcode = arg0;
        }

        this.data[this.offset++] = (byte) (arg0 + this.isaac.getNextKey());

        if (this.localOpcode >= 0) {
            this.localPayloadStart = this.offset;
        }
    }

    @OriginalMember(owner = "client!i", name = "s", descriptor = "(I)I")
    public final int g1isaac() {
        return this.data[this.offset++] - this.isaac.getNextKey() & 0xFF;
    }

    @OriginalMember(owner = "client!i", name = "h", descriptor = "(Z)V")
    public final void accessBytes() {
        this.offset = (this.bitOffset + 7) / 8;
    }
}
