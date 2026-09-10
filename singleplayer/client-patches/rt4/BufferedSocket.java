package rt4;

import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;

@OriginalClass("client!ma")
public final class BufferedSocket implements Runnable {

    private static final long LOCAL_PRESENTATION_QUIET_NANOS = 150_000_000L;

    @OriginalMember(owner = "client!ma", name = "h", descriptor = "[B")
    private byte[] buffer;

    @OriginalMember(owner = "client!ma", name = "n", descriptor = "Lsignlink!im;")
    private PrivilegedRequest thread;

    @OriginalMember(owner = "client!ma", name = "l", descriptor = "I")
    private int readPointer = 0;

    @OriginalMember(owner = "client!ma", name = "b", descriptor = "I")
    private int writePointer = 0;

    @OriginalMember(owner = "client!ma", name = "v", descriptor = "Z")
    private boolean closed = false;

    @OriginalMember(owner = "client!ma", name = "y", descriptor = "Z")
    private boolean error = false;

    @OriginalMember(owner = "client!ma", name = "r", descriptor = "Lsignlink!ll;")
    private final SignLink signLink;

    @OriginalMember(owner = "client!ma", name = "k", descriptor = "Ljava/net/Socket;")
    private final Socket socket;

    @OriginalMember(owner = "client!ma", name = "e", descriptor = "Ljava/io/InputStream;")
    private InputStream in;

    @OriginalMember(owner = "client!ma", name = "c", descriptor = "Ljava/io/OutputStream;")
    private OutputStream out;

    private static volatile Method singlePlayerFlushBoundary;

    private boolean localPresentationRequested;
    private boolean localPresentationReading;
    private long localPresentationQuietSince;
    private long localPresentationBytesRead;

    @OriginalMember(owner = "client!ma", name = "<init>", descriptor = "(Ljava/net/Socket;Lsignlink!ll;)V")
    public BufferedSocket(@OriginalArg(0) Socket socket, @OriginalArg(1) SignLink signLink) throws IOException {
        this.signLink = signLink;
        this.socket = socket;
        this.socket.setSoTimeout(30000);
        this.socket.setTcpNoDelay(true);
        this.in = this.socket.getInputStream();
        this.out = this.socket.getOutputStream();
    }

    @OriginalMember(owner = "client!ma", name = "run", descriptor = "()V")
    @Override
    public final void run() {
        try {
            while (true) {
                @Pc(39) int len;
                @Pc(24) int off;
                ready:
                {
                    synchronized (this) {
                        close:
                        {
                            if (this.writePointer == this.readPointer) {
                                if (this.closed) {
                                    break close;
                                }
                                try {
                                    this.wait();
                                } catch (@Pc(21) InterruptedException ex) {
                                }
                            }
                            off = this.readPointer;
                            if (this.readPointer > this.writePointer) {
                                len = 5000 - this.readPointer;
                            } else {
                                len = this.writePointer - this.readPointer;
                            }
                            break ready;
                        }
                    }
                    try {
                        if (this.in != null) {
                            this.in.close();
                        }
                        if (this.out != null) {
                            this.out.close();
                        }
                        if (this.socket != null) {
                            this.socket.close();
                        }
                    } catch (@Pc(119) IOException ex) {
                    }
                    this.buffer = null;
                    break;
                }
                if (len > 0) {
                    try {
                        this.out.write(this.buffer, off, len);
                    } catch (@Pc(67) IOException ex) {
                        this.error = true;
                    }
                    this.readPointer = (len + this.readPointer) % 5000;
                    try {
                        if (this.writePointer == this.readPointer) {
                            this.out.flush();
                        }
                    } catch (@Pc(92) IOException ex) {
                        this.error = true;
                    }
                }
            }
        } catch (@Pc(124) Exception ex) {
            TracingException.report(null, ex);
        }
    }

    /**
     * Keep login/bootstrap on the physical loopback socket. Once RT4 is fully in
     * game, request the world->client local stream only at a clean RT4 packet
     * boundary and after the physical receive buffer is empty. After the world
     * accepts that request, require a short additional quiet period before
     * consuming newer local bytes so already-written loopback bytes cannot be
     * overtaken in practice.
     */
    private boolean useLocalPresentationStream() throws IOException {
        if (!Boolean.getBoolean("singleplayer") || client.gameState != 30) {
            return false;
        }

        int legacyAvailable = this.in.available();

        if (!this.localPresentationRequested) {
            if (Protocol.opcode == -1 && legacyAvailable == 0) {
                LocalPresentationBridge.requestServerToClientCutover();
                this.localPresentationRequested = true;
            }
            return false;
        }

        if (!LocalPresentationBridge.isServerToClientEnabled()) {
            return false;
        }

        if (this.localPresentationReading) {
            if (legacyAvailable > 0) {
                if (this.localPresentationBytesRead == 0L) {
                    // A final pre-cutover localhost write arrived during the
                    // quiet window. Drain it first and establish a fresh gap.
                    this.localPresentationReading = false;
                    this.localPresentationQuietSince = 0L;
                    return false;
                }
                throw new IOException(
                        "Legacy server bytes arrived after local presentation cutover");
            }
            return true;
        }

        if (Protocol.opcode != -1 || legacyAvailable > 0) {
            this.localPresentationQuietSince = 0L;
            return false;
        }

        long now = System.nanoTime();
        if (this.localPresentationQuietSince == 0L) {
            this.localPresentationQuietSince = now;
            return false;
        }
        if (now - this.localPresentationQuietSince < LOCAL_PRESENTATION_QUIET_NANOS) {
            return false;
        }

        this.localPresentationReading = true;
        System.out.println("SINGLEPLAYER_LOCAL_PRESENTATION: RT4_READER_ACTIVE");
        return true;
    }

    @OriginalMember(owner = "client!ma", name = "a", descriptor = "(III[B)V")
    public final void read(@OriginalArg(0) int off, @OriginalArg(1) int len, @OriginalArg(3) byte[] b) throws IOException {
        if (this.closed) {
            return;
        }

        if (this.localPresentationReading) {
            int copied = LocalPresentationBridge.readServerBytes(b, off, len);
            if (copied != len) {
                throw new EOFException(
                        "Local presentation underflow: " + copied + " of " + len);
            }
            this.localPresentationBytesRead += copied;
            return;
        }

        while (len > 0) {
            @Pc(23) int n = this.in.read(b, off, len);
            if (n <= 0) {
                throw new EOFException();
            }
            off += n;
            len -= n;
        }
    }

    @OriginalMember(owner = "client!ma", name = "a", descriptor = "(I)I")
    public final int read() throws IOException {
        if (this.closed) {
            return 0;
        }
        if (this.localPresentationReading) {
            int value = LocalPresentationBridge.readServerByte();
            if (value < 0) {
                throw new EOFException("Local presentation byte unavailable");
            }
            this.localPresentationBytesRead++;
            return value;
        }
        return this.in.read();
    }

    /**
     * Last boundary before retained RT4 bytes enter the asynchronous kernel
     * socket queue. Gameplay packets are offered to the local authority here;
     * only bytes that genuinely still require compatibility transport continue.
     */
    private static int finalizeSinglePlayerGameplayWrite(byte[] src, int requestedLen) {
        if (!Boolean.getBoolean("singleplayer") || src != Protocol.outboundBuffer.data) {
            return requestedLen;
        }

        try {
            Method method = singlePlayerFlushBoundary;
            if (method == null) {
                method = ClientProt.class.getDeclaredMethod("stripDirectMinimapTrailers");
                method.setAccessible(true);
                singlePlayerFlushBoundary = method;
            }
            method.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException failure) {
            // The packet finalizer is still safe on its own. If the temporary
            // minimap compatibility hook ever changes, do not risk losing real
            // packet bytes merely because the reflective cleanup was unavailable.
            Protocol.outboundBuffer.finishLocalPacket();
            System.err.println(
                    "SINGLEPLAYER_LOCAL_PACKET: flush-boundary fallback: " + failure);
        }

        return Protocol.outboundBuffer.offset;
    }

    @OriginalMember(owner = "client!ma", name = "a", descriptor = "(ZI[BI)V")
    public final void write(@OriginalArg(2) byte[] src, @OriginalArg(3) int len) throws IOException {
        len = finalizeSinglePlayerGameplayWrite(src, len);
        if (len == 0) {
            return;
        }
        if (this.closed) {
            return;
        }
        if (this.error) {
            this.error = false;
            throw new IOException();
        }
        if (this.buffer == null) {
            this.buffer = new byte[5000];
        }
        synchronized (this) {
            for (@Pc(34) int i = 0; i < len; i++) {
                this.buffer[this.writePointer] = src[i];
                this.writePointer = (this.writePointer + 1) % 5000;
                if (this.writePointer == (this.readPointer + 4900) % 5000) {
                    throw new IOException();
                }
            }
            if (this.thread == null) {
                this.thread = this.signLink.startThread(3, this);
            }
            this.notifyAll();
        }
    }

    @OriginalMember(owner = "client!ma", name = "finalize", descriptor = "()V")
    @Override
    public final void finalize() {
        this.close();
    }

    @OriginalMember(owner = "client!ma", name = "c", descriptor = "(I)I")
    public final int available() throws IOException {
        if (this.closed) {
            return 0;
        }
        if (useLocalPresentationStream()) {
            return LocalPresentationBridge.availableServerBytes();
        }
        return this.in.available();
    }

    @OriginalMember(owner = "client!ma", name = "d", descriptor = "(I)V")
    public final void checkError() throws IOException {
        if (!this.closed && this.error) {
            this.error = false;
            throw new IOException();
        }
    }

    @OriginalMember(owner = "client!ma", name = "a", descriptor = "(Z)V")
    public final void breakConnection() {
        if (!this.closed) {
            this.in = new BrokenInputStream();
            this.out = new BrokenOutputStream();
        }
    }

    @OriginalMember(owner = "client!ma", name = "e", descriptor = "(I)V")
    public final void close() {
        if (this.closed) {
            return;
        }
        synchronized (this) {
            this.closed = true;
            this.notifyAll();
        }
        if (this.thread != null) {
            while (this.thread.status == 0) {
                ThreadUtils.sleep(1L);
            }
            if (this.thread.status == 1) {
                try {
                    ((Thread) this.thread.result).join();
                } catch (@Pc(59) InterruptedException ex) {
                }
            }
        }
        this.thread = null;
    }
}