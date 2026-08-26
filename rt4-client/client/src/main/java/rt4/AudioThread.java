package rt4;

import org.openrs2.deob.annotation.OriginalClass;

@OriginalClass("client!cj")
public final class AudioThread implements Runnable {
	public SignLink signLink;
	public final AudioChannel[] audioChannels = new AudioChannel[2];
	public volatile boolean shouldStop = false;
	public volatile boolean isRunning = false;

	@Override
	public final void run() {
		isRunning = true;
		try {
			while (!shouldStop) {
				for (int i = 0; i < 2; i++) {
					AudioChannel audioChannel = audioChannels[i];
					if (audioChannel != null) {
						audioChannel.loop();
					}
				}
				ThreadUtils.sleep(10L);
				GameShell.flush(signLink, null);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			TracingException.report(null, ex);
		} finally {
			isRunning = false;
		}
	}
}
