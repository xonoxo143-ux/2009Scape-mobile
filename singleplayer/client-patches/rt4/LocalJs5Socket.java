package rt4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;

/**
 * In-memory Socket facade for RT4's retained JS5 client.
 *
 * Js5NetQueue is intentionally left unchanged. It still writes the historical
 * five-byte JS5 handshake followed by four-byte cache requests and reads the
 * exact response framing it always did. This facade replaces only the kernel
 * socket: archive bytes are obtained from the already-loaded 2009Scape Cache
 * implementation in the same JVM.
 */
public final class LocalJs5Socket extends Socket {
    private static final int CLIENT_REVISION = 530;
    private static final AtomicInteger NEXT_SOCKET_ID = new AtomicInteger();

    private final int socketId = NEXT_SOCKET_ID.incrementAndGet();
    private final QueueInputStream input = new QueueInputStream();
    private final Js5OutputStream output = new Js5OutputStream();
    private volatile boolean closed;
    private volatile String lastRequest = "none";
    private int requestSequence;

    private static volatile Method archiveDataMethod;
    private static volatile Method referenceDataMethod;

    public LocalJs5Socket() {
        super();
        System.out.println("SINGLEPLAYER_LOCAL_JS5: SOCKET_CREATED socket=" + socketId);
    }

    @Override
    public InputStream getInputStream() {
        return input;
    }

    @Override
    public OutputStream getOutputStream() {
        return output;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        System.out.println(
                "SINGLEPLAYER_LOCAL_JS5: CLOSE socket=" + socketId
                        + " requests=" + requestSequence
                        + " last=" + lastRequest
                        + " queuedBytes=" + input.available());
        input.finish();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void setSoTimeout(int timeout) throws SocketException {
        // Reads are driven by available(), exactly as Js5NetQueue already does.
    }

    @Override
    public void setTcpNoDelay(boolean on) throws SocketException {
        // No TCP transport exists.
    }

    private final class Js5OutputStream extends OutputStream {
        private final ByteArrayOutputStream pending = new ByteArrayOutputStream(32);
        private boolean handshakeComplete;
        private int encryptionKey;

        @Override
        public synchronized void write(int value) throws IOException {
            pending.write(value);
            processFrames();
        }

        @Override
        public synchronized void write(byte[] bytes, int off, int len) throws IOException {
            if (closed) throw new IOException("Local JS5 socket closed");
            if (bytes == null) throw new NullPointerException("bytes");
            if (off < 0 || len < 0 || off > bytes.length - len) {
                throw new IndexOutOfBoundsException();
            }
            pending.write(bytes, off, len);
            processFrames();
        }

        private void processFrames() throws IOException {
            while (true) {
                byte[] bytes = pending.toByteArray();
                int frameLength = handshakeComplete ? 4 : 5;
                if (bytes.length < frameLength) return;

                byte[] frame = new byte[frameLength];
                System.arraycopy(bytes, 0, frame, 0, frameLength);
                pending.reset();
                if (bytes.length > frameLength) {
                    pending.write(bytes, frameLength, bytes.length - frameLength);
                }

                if (!handshakeComplete) {
                    handleHandshake(frame);
                } else {
                    handleRequest(frame);
                }
            }
        }

        private void handleHandshake(byte[] frame) throws IOException {
            int opcode = frame[0] & 0xFF;
            int revision = ((frame[1] & 0xFF) << 24)
                    | ((frame[2] & 0xFF) << 16)
                    | ((frame[3] & 0xFF) << 8)
                    | (frame[4] & 0xFF);
            if (opcode != 15 || revision != CLIENT_REVISION) {
                throw new IOException(
                        "Invalid local JS5 handshake opcode=" + opcode + " revision=" + revision);
            }
            handshakeComplete = true;
            input.enqueue(new byte[]{0});
            System.out.println(
                    "SINGLEPLAYER_LOCAL_JS5: HANDSHAKE_READY socket=" + socketId
                            + " revision=" + revision);
        }

        private void handleRequest(byte[] frame) throws IOException {
            int opcode = frame[0] & 0xFF;
            switch (opcode) {
                case 0:
                case 1:
                    int key = ((frame[1] & 0xFF) << 16)
                            | ((frame[2] & 0xFF) << 8)
                            | (frame[3] & 0xFF);
                    int index = key >>> 16;
                    int archive = key & 0xFFFF;
                    boolean priority = opcode == 1;
                    int sequence = ++requestSequence;
                    lastRequest = "seq=" + sequence
                            + ",index=" + index
                            + ",archive=" + archive
                            + ",priority=" + priority
                            + ",xor=" + encryptionKey;
                    System.out.println(
                            "SINGLEPLAYER_LOCAL_JS5: REQUEST socket=" + socketId
                                    + " " + lastRequest);
                    enqueueArchive(index, archive, priority, encryptionKey, sequence);
                    return;
                case 2: // logged in
                case 3: // logged out
                case 6: // connection initialized
                    System.out.println(
                            "SINGLEPLAYER_LOCAL_JS5: CONTROL socket=" + socketId
                                    + " opcode=" + opcode
                                    + " xor=" + encryptionKey);
                    return;
                case 4: // XOR/rekey
                    encryptionKey = frame[1] & 0xFF;
                    System.out.println(
                            "SINGLEPLAYER_LOCAL_JS5: XOR socket=" + socketId
                                    + " key=" + encryptionKey);
                    return;
                case 7: // client is dropping this JS5 connection
                    System.out.println(
                            "SINGLEPLAYER_LOCAL_JS5: DROP socket=" + socketId);
                    LocalJs5Socket.this.close();
                    return;
                default:
                    throw new IOException("Unsupported local JS5 opcode " + opcode);
            }
        }
    }

    private void enqueueArchive(
            int index,
            int archive,
            boolean priority,
            int encryptionKey,
            int sequence) throws IOException {
        try {
            ByteBuffer response;
            if (index == 255 && archive == 255) {
                Method method = referenceDataMethod();
                response = (ByteBuffer) method.invoke(null);
            } else {
                Method method = archiveDataMethod();
                response = (ByteBuffer) method.invoke(
                        null, index, archive, priority, encryptionKey);
            }
            if (response == null) {
                System.out.println(
                        "SINGLEPLAYER_LOCAL_JS5: RESPONSE_NULL socket=" + socketId
                                + " seq=" + sequence
                                + " index=" + index
                                + " archive=" + archive
                                + " xor=" + encryptionKey);
                return;
            }

            ByteBuffer copy = response.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            System.out.println(
                    "SINGLEPLAYER_LOCAL_JS5: RESPONSE socket=" + socketId
                            + " seq=" + sequence
                            + " requestIndex=" + index
                            + " requestArchive=" + archive
                            + " xor=" + encryptionKey
                            + " " + summarizeResponse(bytes, encryptionKey));
            input.enqueue(bytes);
        } catch (ReflectiveOperationException failure) {
            throw new IOException("Unable to read local 2009Scape JS5 cache", failure);
        }
    }

    private static String summarizeResponse(byte[] wireBytes, int encryptionKey) {
        long wireCrc = crc32(wireBytes);
        byte[] decoded = wireBytes.clone();
        if (encryptionKey != 0) {
            for (int i = 0; i < decoded.length; i++) {
                decoded[i] = (byte) (decoded[i] ^ encryptionKey);
            }
        }
        if (decoded.length < 8) {
            return "bytes=" + decoded.length
                    + " wireCrc=" + Long.toUnsignedString(wireCrc)
                    + " malformed=short_header";
        }

        int responseIndex = decoded[0] & 0xFF;
        int responseArchive = ((decoded[1] & 0xFF) << 8) | (decoded[2] & 0xFF);
        int settings = decoded[3] & 0xFF;
        int compression = settings & 0x7F;
        boolean prefetch = (settings & 0x80) != 0;
        int length = ((decoded[4] & 0xFF) << 24)
                | ((decoded[5] & 0xFF) << 16)
                | ((decoded[6] & 0xFF) << 8)
                | (decoded[7] & 0xFF);

        ContainerSummary container = reconstructContainer(decoded, compression, length);
        if (container == null) {
            return "bytes=" + decoded.length
                    + " wireCrc=" + Long.toUnsignedString(wireCrc)
                    + " headerIndex=" + responseIndex
                    + " headerArchive=" + responseArchive
                    + " compression=" + compression
                    + " prefetch=" + prefetch
                    + " length=" + length
                    + " malformed=container_framing";
        }
        return "bytes=" + decoded.length
                + " wireCrc=" + Long.toUnsignedString(wireCrc)
                + " headerIndex=" + responseIndex
                + " headerArchive=" + responseArchive
                + " compression=" + compression
                + " prefetch=" + prefetch
                + " length=" + length
                + " containerBytes=" + container.bytes.length
                + " containerCrc=" + Long.toUnsignedString(crc32(container.bytes))
                + " trailingWireBytes=" + container.trailingWireBytes;
    }

    /** Reconstruct exactly the cache-container bytes Js5NetQueue builds. */
    private static ContainerSummary reconstructContainer(
            byte[] decodedWire, int compression, int length) {
        if (length < 0) return null;
        long bodyLengthLong = (long) length + (compression == 0 ? 0L : 4L);
        long containerLengthLong = bodyLengthLong + 5L;
        if (bodyLengthLong > Integer.MAX_VALUE || containerLengthLong > Integer.MAX_VALUE) {
            return null;
        }
        int bodyLength = (int) bodyLengthLong;
        byte[] container = new byte[(int) containerLengthLong];
        container[0] = (byte) compression;
        container[1] = (byte) (length >>> 24);
        container[2] = (byte) (length >>> 16);
        container[3] = (byte) (length >>> 8);
        container[4] = (byte) length;

        int src = 8;
        int dst = 5;
        int remaining = bodyLength;
        int blockPosition = 8;
        while (remaining > 0) {
            int inBlock = 512 - blockPosition;
            int count = Math.min(inBlock, remaining);
            if (count < 0 || src > decodedWire.length - count) return null;
            System.arraycopy(decodedWire, src, container, dst, count);
            src += count;
            dst += count;
            remaining -= count;
            blockPosition += count;
            if (remaining > 0 && blockPosition == 512) {
                if (src >= decodedWire.length || (decodedWire[src] & 0xFF) != 0xFF) {
                    return null;
                }
                src++;
                blockPosition = 1;
            }
        }
        return new ContainerSummary(container, decodedWire.length - src);
    }

    private static long crc32(byte[] bytes) {
        CRC32 crc = new CRC32();
        crc.update(bytes, 0, bytes.length);
        return crc.getValue();
    }

    private static final class ContainerSummary {
        final byte[] bytes;
        final int trailingWireBytes;

        ContainerSummary(byte[] bytes, int trailingWireBytes) {
            this.bytes = bytes;
            this.trailingWireBytes = trailingWireBytes;
        }
    }

    private static synchronized Method archiveDataMethod() throws ReflectiveOperationException {
        Method method = archiveDataMethod;
        if (method != null) return method;
        Class<?> cache = Class.forName("core.cache.Cache");
        method = cache.getMethod(
                "getArchiveData",
                int.class,
                int.class,
                boolean.class,
                int.class);
        archiveDataMethod = method;
        return method;
    }

    private static synchronized Method referenceDataMethod() throws ReflectiveOperationException {
        Method method = referenceDataMethod;
        if (method != null) return method;
        Class<?> writer = Class.forName("core.net.event.JS5WriteEvent");
        method = writer.getDeclaredMethod("getReferenceData");
        method.setAccessible(true);
        referenceDataMethod = method;
        return method;
    }

    /** Small non-blocking byte queue matching Js5NetQueue's available/read usage. */
    private static final class QueueInputStream extends InputStream {
        private final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
        private int headOffset;
        private int available;
        private boolean finished;

        synchronized void enqueue(byte[] bytes) {
            if (bytes == null || bytes.length == 0 || finished) return;
            chunks.addLast(bytes);
            available += bytes.length;
            notifyAll();
        }

        synchronized void finish() {
            finished = true;
            notifyAll();
        }

        @Override
        public synchronized int available() {
            return available;
        }

        @Override
        public synchronized int read() {
            if (available == 0) return -1;
            byte[] head = chunks.peekFirst();
            int value = head[headOffset++] & 0xFF;
            available--;
            if (headOffset == head.length) {
                chunks.removeFirst();
                headOffset = 0;
            }
            return value;
        }

        @Override
        public synchronized int read(byte[] dst, int off, int len) {
            if (dst == null) throw new NullPointerException("dst");
            if (off < 0 || len < 0 || off > dst.length - len) {
                throw new IndexOutOfBoundsException();
            }
            if (len == 0) return 0;
            if (available == 0) return -1;

            int wanted = Math.min(len, available);
            int remaining = wanted;
            int target = off;
            while (remaining > 0) {
                byte[] head = chunks.peekFirst();
                int copy = Math.min(remaining, head.length - headOffset);
                System.arraycopy(head, headOffset, dst, target, copy);
                headOffset += copy;
                target += copy;
                remaining -= copy;
                available -= copy;
                if (headOffset == head.length) {
                    chunks.removeFirst();
                    headOffset = 0;
                }
            }
            return wanted;
        }
    }
}
