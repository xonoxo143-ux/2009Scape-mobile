package rt4;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.*;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;

import java.awt.Component;
import java.nio.ByteBuffer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@OriginalClass("client!qa")
public final class OpenALAudioChannel extends AudioChannel {
	private int bufferSize;
	private long audioContext;
	private int source;
	int[] buffers = new int[2];
	int nextBuffer = 0;
	private AtomicInteger sampleCounter = new AtomicInteger(0);
	private ScheduledExecutorService sampleScheduler;

	@OriginalMember(owner = "client!qa", name = "d", descriptor = "()V")
	@Override
	protected final void flush() {

	}

	public void initOpenAL() {
		String defaultDeviceName = ALC10.alcGetString(0, ALC11.ALC_DEFAULT_DEVICE_SPECIFIER);
		long audioDevice = ALC10.alcOpenDevice(defaultDeviceName);
		ALCCapabilities deviceCaps = ALC.createCapabilities(audioDevice);
		audioContext = ALC10.alcCreateContext(audioDevice, new int[]{0});
		ALC10.alcMakeContextCurrent(audioContext);
		AL.createCapabilities(deviceCaps);
		source = AL10.alGenSources();
		for (int i = 0; i < buffers.length; i++) {
			buffers[i] = AL10.alGenBuffers();
		}

		startSampleScheduler();
	}

	@Override
	public final void init(Component arg0) {
		initOpenAL();
	}

	@OriginalMember(owner = "client!qa", name = "a", descriptor = "(I)V")
	@Override
	public final void open(@OriginalArg(0) int size) {
		this.bufferSize = size;
	}

	private void unqueueProcessedBuffers() {
		int buffersProcessed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
		while (buffersProcessed-- > 0) {
			int buffer = AL10.alSourceUnqueueBuffers(source);
			AL10.alDeleteBuffers(buffer);
		}
	}

	@OriginalMember(owner = "client!qa", name = "b", descriptor = "()V")
	@Override
	protected final void close() {
		if (sampleScheduler != null) {
			sampleScheduler.shutdown();
		}
	}

	@Override
	protected final int getBufferSize() {
		if (sampleCounter.get() >= bufferSize * 2.66) {
			return bufferSize; // Returning the bufferSize means we don't have space for a sample
		}
		return bufferSize - 512; // Request one sample
	}
	protected final void write() {
		short sampleBatchSize = 256;
		if (AudioChannel.stereo) {
			sampleBatchSize = 512;
		}

		if (this.samples == null || this.samples.length < sampleBatchSize) {
			System.out.println("Sample array is not properly initialized or too small.");
			return;
		}

		unqueueProcessedBuffers();

		ByteBuffer bufferData = BufferUtils.createByteBuffer(sampleBatchSize * 2);
		for (int i = 0; i < sampleBatchSize; i++) {
			int sampleData = this.samples[i];
			if ((sampleData + 8388608 & 0xFF000000) != 0) {
				sampleData = sampleData >> 31 ^ 0x7FFFFF;
			}
			byte byte1 = (byte) (sampleData >> 8);
			byte byte2 = (byte) (sampleData >> 16);

			bufferData.put(byte1);
			bufferData.put(byte2);
		}
		bufferData.flip();

		int error = AL10.alGetError();
		if (error != AL10.AL_NO_ERROR) {
			System.out.println("OpenAL (alGenBuffers) error detected! Error code: " + error);
		}

		int buffer = AL10.alGenBuffers();
		AL10.alBufferData(buffer, AudioChannel.stereo ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16, bufferData, sampleRate);

		error = AL10.alGetError();
		if (error != AL10.AL_NO_ERROR) {
			System.out.println("OpenAL (alBufferData) error detected! Error code: " + error);
		} else {
			AL10.alSourceQueueBuffers(source, buffer);
			error = AL10.alGetError();
			if (error != AL10.AL_NO_ERROR) {
				System.out.println("OpenAL (alSourceQueueBuffers) error detected! Error code: " + error);
			}

			int sourceState = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
			if (sourceState != AL10.AL_PLAYING) {
				AL10.alSourcePlay(source);
				error = AL10.alGetError();
				if (error != AL10.AL_NO_ERROR) {
					System.out.println("OpenAL (alSourcePlay) error detected! Error code: " + error);
				}
			}

			nextBuffer = (nextBuffer + 1) % buffers.length;

			// Update the sample counter
			sampleCounter.addAndGet(sampleBatchSize);
		}
	}

	private void startSampleScheduler() {
		sampleScheduler = Executors.newScheduledThreadPool(1);
		sampleScheduler.scheduleAtFixedRate(() -> sampleCounter.set(0), 2, 1, TimeUnit.SECONDS); // Reset every second
	}
}
