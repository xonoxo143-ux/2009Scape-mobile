package rt4;

import singleplayer.MobileLifecycleBridge;

/** Mobile-aware RT4 audio worker. */
public final class AudioThread implements Runnable {
    public SignLink signLink;
    public final AudioChannel[] audioChannels = new AudioChannel[2];
    public volatile boolean shouldStop = false;
    public volatile boolean isRunning = false;
    public static volatile Thread workerThread;

    @Override
    public void run() {
        workerThread = Thread.currentThread();
        workerThread.setName("RT4 Audio");
        isRunning = true;
        try {
            while (!shouldStop) {
                if (MobileLifecycleBridge.isAppPaused()) {
                    ThreadUtils.sleep(250L);
                    continue;
                }
                for (int i = 0; i < audioChannels.length; i++) {
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
            workerThread = null;
            isRunning = false;
        }
    }
}
