package rt4;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.ALCCapabilities;

import java.awt.Component;
import java.nio.ByteBuffer;

/**
 * Android/mobile OpenAL backend.
 *
 * RT4 has separate logical music and effects channels. The old backend opened
 * a separate OpenAL device/context for each and estimated queue depth with a
 * timer. On Android the effects channel consequently started "full" and never
 * wrote samples. Both channels now share one context, keep separate sources,
 * and use the actual OpenAL queue depth with a small low-latency target.
 */
public final class OpenALAudioChannel extends AudioChannel {
    private static final Object OPENAL_LOCK = new Object();
    private static final int TARGET_QUEUED_FRAMES = 1024;

    private static long sharedDevice;
    private static long sharedContext;
    private static ALCCapabilities sharedCapabilities;

    private int bufferSize;
    private int source;
    private boolean announcedFirstWrite;
    private boolean lifecyclePaused;

    @Override
    public void init(Component component) {
        synchronized (OPENAL_LOCK) {
            ensureContext();
            makeContextCurrent();
            ensureSource();
        }
    }

    @Override
    public void open(int size) {
        synchronized (OPENAL_LOCK) {
            bufferSize = size;
            ensureContext();
            makeContextCurrent();
            ensureSource();
        }
    }

    @Override
    protected int getBufferSize() {
        synchronized (OPENAL_LOCK) {
            if (source == 0) {
                return desiredBufferSize();
            }
            makeContextCurrent();
            unqueueProcessedBuffers();

            int queuedBuffers = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
            int queuedFrames = Math.max(0, queuedBuffers) * 256;
            int desired = desiredBufferSize();
            int missing = Math.max(0, TARGET_QUEUED_FRAMES - queuedFrames);
            return Math.max(0, desired - missing);
        }
    }

    @Override
    protected void write() {
        synchronized (OPENAL_LOCK) {
            if (source == 0 || samples == null) {
                return;
            }

            makeContextCurrent();
            unqueueProcessedBuffers();

            int sampleCount = AudioChannel.stereo ? 512 : 256;
            if (samples.length < sampleCount) {
                return;
            }

            ByteBuffer pcm = BufferUtils.createByteBuffer(sampleCount * 2);
            for (int i = 0; i < sampleCount; i++) {
                int sample = samples[i];
                if ((sample + 8388608 & 0xFF000000) != 0) {
                    sample = sample >> 31 ^ 0x7FFFFF;
                }
                pcm.put((byte) (sample >> 8));
                pcm.put((byte) (sample >> 16));
            }
            pcm.flip();

            int buffer = AL10.alGenBuffers();
            checkError("alGenBuffers");
            AL10.alBufferData(
                    buffer,
                    AudioChannel.stereo
                            ? AL10.AL_FORMAT_STEREO16
                            : AL10.AL_FORMAT_MONO16,
                    pcm,
                    AudioChannel.sampleRate);
            checkError("alBufferData");

            AL10.alSourceQueueBuffers(source, buffer);
            checkError("alSourceQueueBuffers");

            if (!lifecyclePaused
                    && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AL10.alSourcePlay(source);
                checkError("alSourcePlay");
            }

            if (!announcedFirstWrite) {
                announcedFirstWrite = true;
                System.out.println(
                        channelSampleRate == 2048
                                ? "SINGLEPLAYER_AUDIO: EFFECTS_PCM_ACTIVE"
                                : "SINGLEPLAYER_AUDIO: MUSIC_PCM_ACTIVE");
            }
        }
    }

    @Override
    protected void flush() {
        synchronized (OPENAL_LOCK) {
            clearQueuedAudio(false);
        }
    }

    @Override
    protected void close() {
        synchronized (OPENAL_LOCK) {
            // RT4 uses close() as a channel stop/reset, not as final process
            // teardown. Keep the OpenAL source alive so later music/jingles or
            // effects can resume without requiring a separate reopen path.
            clearQueuedAudio(false);
        }
    }

    /** Pause/resume already-queued OpenAL audio with the Android lifecycle. */
    public void setLifecyclePaused(boolean paused) {
        synchronized (OPENAL_LOCK) {
            lifecyclePaused = paused;
            if (source == 0 || sharedContext == 0L) return;
            makeContextCurrent();
            int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
            if (paused) {
                if (state == AL10.AL_PLAYING) {
                    AL10.alSourcePause(source);
                    checkError("alSourcePause");
                }
            } else if (state == AL10.AL_PAUSED
                    && AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED) > 0) {
                AL10.alSourcePlay(source);
                checkError("alSourcePlay(resume)");
            }
        }
    }

    private int desiredBufferSize() {
        int desired = channelSampleRate;
        if (desired + 256 > 16384) {
            desired = 16128;
        }
        return Math.max(256, desired);
    }

    private void ensureSource() {
        if (source == 0) {
            source = AL10.alGenSources();
            checkError("alGenSources");
        }
    }

    private void clearQueuedAudio(boolean deleteSource) {
        if (source == 0 || sharedContext == 0L) {
            return;
        }

        makeContextCurrent();
        AL10.alSourceStop(source);

        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        for (int i = 0; i < queued; i++) {
            int buffer = AL10.alSourceUnqueueBuffers(source);
            if (buffer != 0) {
                AL10.alDeleteBuffers(buffer);
            }
        }
        AL10.alSourcei(source, AL10.AL_BUFFER, 0);

        if (deleteSource) {
            AL10.alDeleteSources(source);
            source = 0;
        }
        AL10.alGetError();
    }

    private void unqueueProcessedBuffers() {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        while (processed-- > 0) {
            int buffer = AL10.alSourceUnqueueBuffers(source);
            if (buffer != 0) {
                AL10.alDeleteBuffers(buffer);
            }
        }
    }

    private static void ensureContext() {
        if (sharedContext != 0L) {
            return;
        }

        String defaultDeviceName =
                ALC10.alcGetString(0, ALC11.ALC_DEFAULT_DEVICE_SPECIFIER);
        sharedDevice = ALC10.alcOpenDevice(defaultDeviceName);
        if (sharedDevice == 0L) {
            throw new IllegalStateException(
                    "OpenAL could not open the Android audio device");
        }

        sharedCapabilities = ALC.createCapabilities(sharedDevice);
        sharedContext = ALC10.alcCreateContext(sharedDevice, new int[]{0});
        if (sharedContext == 0L) {
            ALC10.alcCloseDevice(sharedDevice);
            sharedDevice = 0L;
            throw new IllegalStateException(
                    "OpenAL could not create an Android audio context");
        }

        makeContextCurrent();
        AL.createCapabilities(sharedCapabilities);
        System.out.println(
                "SINGLEPLAYER_AUDIO: OPENAL_SHARED_CONTEXT_READY");
    }

    private static void makeContextCurrent() {
        if (sharedContext == 0L
                || !ALC10.alcMakeContextCurrent(sharedContext)) {
            throw new IllegalStateException(
                    "OpenAL could not activate the shared audio context");
        }
    }

    private static void checkError(String operation) {
        int error = AL10.alGetError();
        if (error != AL10.AL_NO_ERROR) {
            throw new IllegalStateException(
                    operation + " failed with OpenAL error " + error);
        }
    }
}
