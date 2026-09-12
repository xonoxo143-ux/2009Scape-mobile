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
        boolean wasPaused = false;
        try {
            while (!shouldStop) {
                boolean paused = MobileLifecycleBridge.isAppPaused();
                if (paused != wasPaused) {
                    applyLifecyclePause(paused);
                    wasPaused = paused;
                    System.out.println(
                            "SINGLEPLAYER_AUDIO: " + (paused ? "PAUSED" : "RESUMED"));
                }
                if (paused) {
                    ThreadUtils.sleep(100L);
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
            // Never leave native audio sources paused if RT4 tears the worker
            // down during a foreground transition.
            if (wasPaused) applyLifecyclePause(false);
            workerThread = null;
            isRunning = false;
        }
    }

    private void applyLifecyclePause(boolean paused) {
        for (int i = 0; i < audioChannels.length; i++) {
            AudioChannel channel = audioChannels[i];
            if (channel instanceof OpenALAudioChannel) {
                ((OpenALAudioChannel) channel).setLifecyclePaused(paused);
            }
        }
    }
}
