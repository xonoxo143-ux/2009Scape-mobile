package rt4;

import java.util.ArrayDeque;

/**
 * In-process byte stream for the retained world -> RT4 presentation protocol.
 *
 * This is deliberately a migration bridge, not a second networking stack. The
 * existing 2009Scape encoders and RT4 decoders keep speaking their exact
 * protocol while we remove the kernel TCP hop between objects already living in
 * one JVM. Individual presentation messages can later become typed/direct where
 * doing so is useful.
 */
public final class LocalPresentationBridge {
    private static final Object LOCK = new Object();
    private static final ArrayDeque<byte[]> SERVER_TO_CLIENT = new ArrayDeque<>();

    private static boolean cutoverRequested;
    private static boolean serverToClientEnabled;
    private static int headOffset;
    private static int bufferedBytes;
    private static long transferredBytes;

    private LocalPresentationBridge() {}

    /** Called by RT4 only after the normal login/bootstrap stream has settled. */
    public static void requestServerToClientCutover() {
        synchronized (LOCK) {
            if (!cutoverRequested) {
                cutoverRequested = true;
                System.out.println("SINGLEPLAYER_LOCAL_PRESENTATION: CUTOVER_REQUESTED");
            }
        }
    }

    public static boolean isCutoverRequested() {
        synchronized (LOCK) {
            return cutoverRequested;
        }
    }

    /**
     * Called reflectively from the retained world engine at its final encoded
     * byte-write boundary. canActivate is supplied only when its Java-side
     * legacy write queue is empty, so no older queued buffers are overtaken by
     * newer local data.
     */
    public static boolean offerServerBytes(byte[] bytes, boolean canActivate) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }
        synchronized (LOCK) {
            if (!serverToClientEnabled) {
                if (!cutoverRequested || !canActivate) {
                    return false;
                }
                serverToClientEnabled = true;
                System.out.println("SINGLEPLAYER_LOCAL_PRESENTATION: SERVER_TO_CLIENT_ACTIVE");
            }
            SERVER_TO_CLIENT.addLast(bytes.clone());
            bufferedBytes += bytes.length;
            transferredBytes += bytes.length;
            LOCK.notifyAll();
            return true;
        }
    }

    public static boolean isServerToClientEnabled() {
        synchronized (LOCK) {
            return serverToClientEnabled;
        }
    }

    public static int availableServerBytes() {
        synchronized (LOCK) {
            return bufferedBytes;
        }
    }

    /** Non-blocking stream read. Returns the number of bytes copied. */
    public static int readServerBytes(byte[] dst, int off, int len) {
        if (dst == null || len <= 0) return 0;
        if (off < 0 || off > dst.length || len > dst.length - off) {
            throw new IndexOutOfBoundsException();
        }
        synchronized (LOCK) {
            int wanted = Math.min(len, bufferedBytes);
            int remaining = wanted;
            int dest = off;
            while (remaining > 0) {
                byte[] head = SERVER_TO_CLIENT.peekFirst();
                if (head == null) break;
                int copy = Math.min(remaining, head.length - headOffset);
                System.arraycopy(head, headOffset, dst, dest, copy);
                headOffset += copy;
                dest += copy;
                remaining -= copy;
                bufferedBytes -= copy;
                if (headOffset == head.length) {
                    SERVER_TO_CLIENT.removeFirst();
                    headOffset = 0;
                }
            }
            return wanted - remaining;
        }
    }

    public static int readServerByte() {
        byte[] one = new byte[1];
        return readServerBytes(one, 0, 1) == 1 ? one[0] & 0xFF : -1;
    }

    public static long transferredServerBytes() {
        synchronized (LOCK) {
            return transferredBytes;
        }
    }

    /** Restart/development hook, not an ordinary gameplay operation. */
    public static void reset() {
        synchronized (LOCK) {
            SERVER_TO_CLIENT.clear();
            cutoverRequested = false;
            serverToClientEnabled = false;
            headOffset = 0;
            bufferedBytes = 0;
            transferredBytes = 0L;
        }
    }
}