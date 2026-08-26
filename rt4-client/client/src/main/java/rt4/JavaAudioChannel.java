package rt4;

import javax.sound.sampled.*;
import java.awt.Component;

public final class JavaAudioChannel extends AudioChannel {

	private int bufferSize;
	private SourceDataLine line;
	private AudioFormat audioFormat;
	private byte[] audioData;
	private final int stereoMultiplier = AudioChannel.stereo ? 2 : 1;

	@Override
	public void init(Component component) {
		audioFormat = new AudioFormat((float) AudioChannel.sampleRate, 16, AudioChannel.stereo ? 2 : 1, true, false);
		audioData = new byte[0x100 << stereoMultiplier];
	}

	@Override
	public void open(int size) throws LineUnavailableException {
		line = getAudioLine(size << stereoMultiplier);
		line.open();
		line.start();
		bufferSize = size;
	}

	private SourceDataLine getAudioLine(int lineSize) throws LineUnavailableException {
		DataLine.Info lineInfo = new DataLine.Info(SourceDataLine.class, audioFormat, lineSize);
		return (SourceDataLine) AudioSystem.getLine(lineInfo);
	}

	@Override
	protected void close() { line.flush(); }
	@Override
	protected int getBufferSize() {
		return bufferSize - (line.available() >> stereoMultiplier);
	}

	protected void write() {
		int audioSamplesPerBatch = AudioChannel.stereo ? 512 : 256;
		for (int i = 0; i < audioSamplesPerBatch; i++) {
			int sampleData = samples[i];
			if ((sampleData + 8388608 & 0xFF000000) != 0) {
				sampleData = sampleData >> 31 ^ 0x7FFFFF;
			}
			audioData[i * 2] = (byte) (sampleData >> 8);
			audioData[i * 2 + 1] = (byte) (sampleData >> 16);
		}
		line.write(audioData, 0, audioSamplesPerBatch << 1);
	}
}