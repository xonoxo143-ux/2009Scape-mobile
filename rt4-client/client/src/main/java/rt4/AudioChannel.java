package rt4;

import javax.sound.sampled.LineUnavailableException;
import java.awt.Component;
import java.util.Arrays;

public class AudioChannel {
	public static boolean stereo;
	public static int threadPriority;
	public static AudioThread audioThread;
	public static int sampleRate;
	private PcmStream audioStream;
	public int[] samples;
	private int bufferSizeAdjustment;
	public int channelSampleRate;
	public int bufferCapacity;
	private long currentClockTime = MonotonicClock.currentTimeMillis();
	private final PcmStream[] pcmStreamsArrayOne = new PcmStream[8];
	private int consumedSamples = 0;
	private long calculateConsumptionAt = 0L;
	private int bufferPosition = 0;
	private boolean skipConsumptionCheck = true;
	private final PcmStream[] pcmStreamsArrayTwo = new PcmStream[8];
	private long closeUntil = 0L;
	private int prevConsumedSamples = 0;
	private int prevBufferSize = 0;
	public static void init(boolean stereo) {
		threadPriority = 2;
		AudioChannel.stereo = stereo;
		sampleRate = GlobalConfig.AUDIO_SAMPLE_RATE;
	}

	public static AudioChannel create(int sampleRate, SignLink signLink, Component component, int channelIndex) {
		try {
			AudioChannel audioChannel;
			audioChannel = initializeAudioChannel(new OpenALAudioChannel(), sampleRate, component, signLink, channelIndex);
			return audioChannel;
		} catch (Throwable ex1) {
			return new AudioChannel();
		}
	}

	private static AudioChannel initializeAudioChannel(AudioChannel audioChannel, int channelSampleRate, Component component, SignLink signLink, int channelIndex) throws Exception {
		audioChannel.channelSampleRate = channelSampleRate;
		audioChannel.samples = new int[(stereo ? 2 : 1) * 256];
		audioChannel.init(component);
		audioChannel.bufferCapacity = (channelSampleRate & -1024) + 1024;
		audioChannel.bufferCapacity = Math.min(audioChannel.bufferCapacity, 16384);
		audioChannel.open(audioChannel.bufferCapacity);
		initializeAudioThread(signLink, channelIndex, audioChannel);
		return audioChannel;
	}

	private static void initializeAudioThread(SignLink signLink, int channelIndex, AudioChannel audioChannel) throws IllegalArgumentException {
		if (threadPriority > 0 && audioThread == null) {
			audioThread = new AudioThread();
			audioThread.signLink = signLink;
			signLink.startThread(threadPriority, audioThread);
		}
		if (audioThread != null) {
			audioThread.audioChannels[channelIndex] = audioChannel;
		}
	}

	public static void setInactive(PcmStream pcmStream) {
		if (pcmStream.sound != null) {
			pcmStream.sound.position = 0;
		}
		pcmStream.active = false;
		for (PcmStream subStream = pcmStream.firstSubStream(); subStream != null; subStream = pcmStream.nextSubStream()) {
			setInactive(subStream);
		}
	}

	private void readAudioData(int[] audioBuffer) {
		resetBuffer(audioBuffer);
		processAudioStream();
		resetPcmStreamArrays();
		validateBufferPosition();
		readIntoAudioBuffer(audioBuffer);
		currentClockTime = MonotonicClock.currentTimeMillis();
	}

	private void resetBuffer(int[] audioBuffer) {
		int dataToProcess = stereo ? 512 : 256;
		Arrays.fill(audioBuffer, 0, dataToProcess, 0);
		bufferPosition -= 256;
	}

	private void processAudioStream() {
		if (audioStream != null && bufferPosition <= 0) {
			bufferPosition += sampleRate >> 4;
			setInactive(audioStream);
			updatePcmStreamArray(audioStream, 255);
			processPcmStreams();
		}
	}

	private void processPcmStreams() {
		int sumProcessed = 0;
		int bitMask = 255;
		int streamIndex = 7;

		while (bitMask != 0) {
			int[] bitOffset = getBitOffset(streamIndex);
			int bitIndex = bitOffset[0];
			int offset = bitOffset[1];

			bitMask = handleMask(sumProcessed, bitMask, bitIndex, offset);
			streamIndex--;
		}
	}

	private int[] getBitOffset(int streamIndex) {
		int bitIndex;
		int offset;

		if (streamIndex < 0) {
			bitIndex = streamIndex & 0x3;
			offset = -(streamIndex >> 2);
		} else {
			bitIndex = streamIndex;
			offset = 0;
		}

		return new int[]{bitIndex, offset};
	}

	private int handleMask(int sumProcessed, int bitMask, int bitIndex, int offset) {
		for (int mask = bitMask >>> bitIndex & 0x11111111; mask != 0; mask >>>= 0x4, bitIndex += 4, offset++) {
			if ((mask & 0x1) != 0) {
				bitMask = processMask(sumProcessed, bitMask, bitIndex, offset);
			}
		}
		return bitMask;
	}

	private int processMask(int sumProcessed, int bitMask, int bitIndex, int offset) {
		bitMask &= ~(0x1 << bitIndex);
		PcmStream lastActiveStream = null;
		PcmStream currentStream = pcmStreamsArrayOne[bitIndex];

		while (currentStream != null) {
			if (isReadyToProcess(currentStream, offset)) {
				sumProcessed = processStream(currentStream, sumProcessed);
				swapBuffers(bitIndex, lastActiveStream, currentStream);
				currentStream = lastActiveStream != null ? lastActiveStream.nextPcmStream : pcmStreamsArrayOne[bitIndex];
			} else {
				bitMask |= 0x1 << bitIndex;
				lastActiveStream = currentStream;
				currentStream = currentStream.nextPcmStream;
			}
		}
		return bitMask;
	}

	private boolean isReadyToProcess(PcmStream currentStream, int offset) {
		Sound currentSound = currentStream.sound;
		return currentSound == null || currentSound.position <= offset;
	}

	private int processStream(PcmStream currentStream, int sumProcessed) {
		currentStream.active = true;
		int processed = currentStream.shouldPlay();
		sumProcessed += processed;
		Sound currentSound = currentStream.sound;
		if (currentSound != null) {
			currentSound.position += processed;
		}

		PcmStream subStream = currentStream.firstSubStream();
		if (subStream != null) {
			handleSubStream(subStream, currentStream);
		}
		return sumProcessed;
	}

	private void handleSubStream(PcmStream subStream, PcmStream currentStream) {
		int position = currentStream.index;
		while (subStream != null) {
			updatePcmStreamArray(subStream, position * 255 >> 8);
			subStream = currentStream.nextSubStream();
		}
	}

	private void swapBuffers(int bitIndex, PcmStream lastActiveStream, PcmStream currentStream) {
		PcmStream nextStream = currentStream.nextPcmStream;
		currentStream.nextPcmStream = null;
		if (lastActiveStream == null) {
			pcmStreamsArrayOne[bitIndex] = nextStream;
		} else {
			lastActiveStream.nextPcmStream = nextStream;
		}
		if (nextStream == null) {
			pcmStreamsArrayTwo[bitIndex] = lastActiveStream;
		}
	}

	private void resetPcmStreamArrays() {
		for (int streamIndex = 0; streamIndex < 8; streamIndex++) {
			resetPcmStreamArray(pcmStreamsArrayOne, streamIndex);
			resetPcmStreamArray(pcmStreamsArrayTwo, streamIndex);
		}
	}

	private void resetPcmStreamArray(PcmStream[] pcmStreamArray, int index) {
		PcmStream pcmStream = pcmStreamArray[index];
		pcmStreamArray[index] = null;
		while (pcmStream != null) {
			PcmStream nextPcmStream = pcmStream.nextPcmStream;
			pcmStream.nextPcmStream = null;
			pcmStream = nextPcmStream;
		}
	}

	private void validateBufferPosition() {
		bufferPosition = Math.max(0, bufferPosition);
	}

	private void readIntoAudioBuffer(int[] audioBuffer) {
		if (audioStream != null) {
			audioStream.read(audioBuffer, 0, 256);
		}
	}

	public final synchronized void loop() {
		if (samples == null) {
			return;
		}
		long currentTime = MonotonicClock.currentTimeMillis();

		try {
			handleCloseUntilState(currentTime);
			int currentBufferSize = getBufferSize();
			updateConsumedSamples(currentBufferSize);
			int desiredBufferSize = adjustDesiredBufferSize();
			handleBufferCapacity(desiredBufferSize);
			processAudioData(desiredBufferSize, currentBufferSize);
			handleConsumptionCalculation(currentTime);
			prevBufferSize = currentBufferSize;
		} catch (Exception ex) { }
		handleClockTimeUpdate(currentTime);
	}

	private void handleCloseUntilState(long currentTime) throws Exception {
		if (closeUntil != 0L) {
			if (currentTime < closeUntil) {
				return;
			}
			open(bufferCapacity);
			skipConsumptionCheck = true;
			closeUntil = 0L;
		}
	}

	private void updateConsumedSamples(int currentBufferSize) {
		if (consumedSamples < prevBufferSize - currentBufferSize) {
			consumedSamples = prevBufferSize - currentBufferSize;
		}
	}

	private int adjustDesiredBufferSize() {
		int desiredBufferSize = channelSampleRate + bufferSizeAdjustment;
		if (desiredBufferSize + 256 > 16384) {
			desiredBufferSize = 16128;
		}
		return desiredBufferSize;
	}

	private void handleBufferCapacity(int desiredBufferSize) throws Exception {
		if (bufferCapacity < desiredBufferSize + 256) {
			bufferCapacity += 1024;
			if (bufferCapacity > 16384) {
				bufferCapacity = 16384;
			}
			flush();
			open(bufferCapacity);
			if (bufferCapacity < desiredBufferSize + 256) {
				desiredBufferSize = bufferCapacity - 256;
				bufferSizeAdjustment = desiredBufferSize - channelSampleRate;
			}
			skipConsumptionCheck = true;
		}
	}

	private void processAudioData(int desiredBufferSize, int currentBufferSize) throws Exception {
		while (desiredBufferSize > currentBufferSize) {
			currentBufferSize += 256;
			readAudioData(samples);
			write();
		}
	}

	private void handleConsumptionCalculation(long currentTime) {
		if (currentTime > calculateConsumptionAt) {
			if (skipConsumptionCheck) {
				skipConsumptionCheck = false;
			} else if (consumedSamples == 0 && prevConsumedSamples == 0) {
				flush();
				closeUntil = currentTime + 2000L;
				return;
			} else {
				bufferSizeAdjustment = Math.min(prevConsumedSamples, consumedSamples);
				prevConsumedSamples = consumedSamples;
			}
			calculateConsumptionAt = currentTime + 2000L;
			consumedSamples = 0;
		}
	}

	private void handleClockTimeUpdate(long currentTime) {
		if (currentTime > currentClockTime + 500000L) {
			currentTime = currentClockTime;
		}
		while (currentTime > currentClockTime + 5000L) {
			skip();
			currentClockTime += 256000 / sampleRate;
		}
	}

	private void updatePcmStreamArray(PcmStream pcmStream, int index) {
		int adjustedIndex = index >> 5;
		PcmStream existingPcmStream = pcmStreamsArrayTwo[adjustedIndex];
		if (existingPcmStream == null) {
			pcmStreamsArrayOne[adjustedIndex] = pcmStream;
		} else {
			existingPcmStream.nextPcmStream = pcmStream;
		}
		pcmStreamsArrayTwo[adjustedIndex] = pcmStream;
		pcmStream.index = index;
	}

	public final synchronized void stopAudio() {
		skipConsumptionCheck = true;
		close();
	}

	private void skip() {
		bufferPosition = Math.max(0, bufferPosition - 256);
		if (audioStream != null) { audioStream.skip(256); }
	}
	public final synchronized void quit() {
		if (audioThread != null) {
			boolean isCurrentChannel = true;
			for (int i = 0; i < 2; i++) {
				if (audioThread.audioChannels[i] == this) {
					audioThread.audioChannels[i] = null;
				}
				isCurrentChannel &= (audioThread.audioChannels[i] == null);
			}
			if (isCurrentChannel) {
				audioThread.shouldStop = true;
				while (audioThread.isRunning) {
					ThreadUtils.sleep(50L);
				}
				audioThread = null;
			}
		}
		flush();
		samples = null;
	}

	public final void skipConsumptionCheck() { skipConsumptionCheck = true; }
	protected int getBufferSize() { return bufferCapacity; }
	public final synchronized void setAudioStream(PcmStream pcmStream) { audioStream = pcmStream; }
	protected void flush() { }
	public void init(Component arg0) throws Exception { }
	protected void write() throws Exception { }
	public void open(int arg0) throws LineUnavailableException { }
	protected void close() { }
}
