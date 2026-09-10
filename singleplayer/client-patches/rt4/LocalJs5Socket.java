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

    private final QueueInputStream input = new QueueInputStream();
    private final Js5OutputStream output = new Js5OutputStream();
    private volatile boolean closed;

    private static volatile Method archiveDataMethod;
    private static volatile Method referenceDataMethod;

    public LocalJs5Socket() {
        super();
        System.out.println("SINGLEPLAYER_LOCAL_JS5: SOCKET_CREATED");
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
            System.out.println("SINGLEPLAYER_LOCAL_JS5: HANDSHAKE_READY");
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
                    enqueueArchive(index, archive, opcode == 1, encryptionKey);
                    return;
                case 2: // logged in
                case 3: // logged out
                case 6: // connection initialized
                    return;
                case 4: // XOR/rekey
                    encryptionKey = frame[1] & 0xFF;
                    return;
                case 7: // client is dropping this JS5 connection
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
            int encryptionKey) throws IOException {
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
            if (response == null) return;

            ByteBuffer copy = response.duplicate();
            byte[] bytes = new byte[copy.remaining()];
            copy.get(bytes);
            input.enqueue(bytes);
        } catch (ReflectiveOperationException failure) {
            throw new IOException("Unable to read local 2009Scape JS5 cache", failure);
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
