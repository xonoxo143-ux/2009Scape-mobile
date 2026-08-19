package net.kdt.pojavlaunch.grandleague;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/** Starts the embedded world and opens the client only when localhost is ready. */
public final class GrandLeagueServerController {
    private static final AtomicBoolean LAUNCH_PENDING = new AtomicBoolean(false);

    private GrandLeagueServerController() { }

    public static void ensureStarted(Context context) {
        Intent service = new Intent(context, GrandLeagueServerService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(service);
        else context.startService(service);
    }

    public static void launchWhenReady(Activity activity, Class<?> targetActivity) {
        ensureStarted(activity);
        if (!LAUNCH_PENDING.compareAndSet(false, true)) return;

        Toast.makeText(activity, "Starting local Grand League world…", Toast.LENGTH_SHORT).show();
        Thread probe = new Thread(() -> {
            boolean ready = false;
            for (int i = 0; i < 1500 && !activity.isFinishing(); i++) {
                if (isReady()) {
                    ready = true;
                    break;
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            final boolean serverReady = ready;
            activity.runOnUiThread(() -> {
                LAUNCH_PENDING.set(false);
                if (serverReady) {
                    activity.startActivity(new Intent(activity, targetActivity));
                } else {
                    Toast.makeText(activity, "Local Grand League world failed to start.", Toast.LENGTH_LONG).show();
                }
            });
        }, "grand-league-port-probe");
        probe.setDaemon(true);
        probe.start();
    }

    private static boolean isReady() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", GrandLeagueServerService.SERVER_PORT), 150);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }
}
