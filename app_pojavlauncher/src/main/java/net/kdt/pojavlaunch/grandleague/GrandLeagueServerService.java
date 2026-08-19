package net.kdt.pojavlaunch.grandleague;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;

import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Runs the bundled 2009Scape Grand League world in a separate Android process. */
public final class GrandLeagueServerService extends Service {
    public static final int SERVER_PORT = 43595;

    private static final String TAG = "GrandLeagueServer";
    private static final String CHANNEL_ID = "grand_league_server";
    private static final int NOTIFICATION_ID = 43595;
    private static final String RUNTIME_NAME = "GrandLeague17";
    private static final String RUNTIME_ASSET = "components/grandleague-jre17/jre17-arm64.tar.xz";
    private static final String[] MUTABLE_DATA_DIRS = {"players", "eco", "serverstore"};
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, notification("Starting local world…"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (STARTED.compareAndSet(false, true)) {
            Thread thread = new Thread(this::runServer, "grand-league-server");
            thread.setDaemon(false);
            thread.start();
        }
        return START_NOT_STICKY;
    }

    private void runServer() {
        try {
            Tools.initContextConstants(this);
            updateNotification("Preparing Java runtime…");
            ensureRuntime17();
            updateNotification("Preparing local world…");
            File serverDir = ensureServerPayload();

            File jar = new File(serverDir, "server.jar");
            File config = new File(serverDir, "worldprops/mobile.conf");
            if (!jar.isFile()) throw new IOException("Bundled server.jar is missing");
            if (!config.isFile()) throw new IOException("Bundled mobile.conf is missing");

            updateNotification("Grand League world is running locally");
            List<String> args = new ArrayList<>();
            args.add("-jar");
            args.add(jar.getAbsolutePath());
            args.add(config.getAbsolutePath());
            int exitCode = HeadlessJavaLauncher.launch(this, RUNTIME_NAME, serverDir, args);
            Log.w(TAG, "Embedded server JVM exited with code " + exitCode);
            updateNotification("Local world stopped (code " + exitCode + ")");
        } catch (Throwable throwable) {
            Log.e(TAG, "Embedded Grand League server failed", throwable);
            persistFailure(throwable);
            updateNotification("Local world failed to start");
        } finally {
            STARTED.set(false);
        }
    }

    private void ensureRuntime17() throws IOException {
        Runtime installed = MultiRTUtils.forceReread(RUNTIME_NAME);
        if (installed.versionString != null && installed.javaVersion >= 17) return;

        if (!"arm64-v8a".equals(Build.SUPPORTED_ABIS[0]) && !containsAbi("arm64-v8a")) {
            throw new IOException("This build currently requires a 64-bit ARM Android device");
        }

        try (InputStream runtime = getAssets().open(RUNTIME_ASSET)) {
            MultiRTUtils.installRuntimeNamed(Tools.NATIVE_LIB_DIR, runtime, RUNTIME_NAME);
        }
        MultiRTUtils.postPrepare(RUNTIME_NAME);

        Runtime ready = MultiRTUtils.forceReread(RUNTIME_NAME);
        if (ready.versionString == null || ready.javaVersion < 17) {
            throw new IOException("Embedded Java 17 runtime did not install correctly");
        }
    }

    private boolean containsAbi(String wanted) {
        for (String abi : Build.SUPPORTED_ABIS) if (wanted.equals(abi)) return true;
        return false;
    }

    private File ensureServerPayload() throws IOException {
        File root = new File(getFilesDir(), "grandleague-server");
        File stamp = new File(root, ".payload-version");
        String packagedVersion;
        try (InputStream input = getAssets().open("grandleague/version.txt")) {
            packagedVersion = Tools.read(input).trim();
        }

        String installedVersion = stamp.isFile() ? Tools.read(stamp.getAbsolutePath()).trim() : "";
        if (packagedVersion.equals(installedVersion) && new File(root, "server.jar").isFile()) return root;

        File staged = new File(getFilesDir(), "grandleague-server.next");
        File previous = new File(getFilesDir(), "grandleague-server.previous");
        if (staged.exists()) FileUtils.deleteDirectory(staged);
        if (previous.exists()) FileUtils.deleteDirectory(previous);
        if (!staged.mkdirs()) throw new IOException("Unable to create staged local-world directory");

        extractServerZip(staged);
        carryForwardMutableState(root, staged);
        Tools.write(new File(staged, ".payload-version").getAbsolutePath(), packagedVersion + "\n");

        boolean hadPrevious = root.exists();
        if (hadPrevious && !root.renameTo(previous)) {
            FileUtils.deleteDirectory(staged);
            throw new IOException("Unable to stage previous local world");
        }
        if (!staged.renameTo(root)) {
            if (hadPrevious && previous.exists()) previous.renameTo(root);
            throw new IOException("Unable to activate new local world payload");
        }
        if (previous.exists()) FileUtils.deleteDirectory(previous);
        return root;
    }

    private void extractServerZip(File root) throws IOException {
        try (InputStream raw = getAssets().open("grandleague/server.zip");
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            byte[] buffer = new byte[64 * 1024];
            String rootPath = root.getCanonicalPath() + File.separator;
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                File output = new File(root, entry.getName());
                String canonical = output.getCanonicalPath();
                if (!canonical.startsWith(rootPath)) {
                    throw new IOException("Unsafe bundled server path: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    if (!output.isDirectory() && !output.mkdirs()) throw new IOException("Unable to create " + output);
                } else {
                    File parent = output.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Unable to create " + parent);
                    try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(output))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) out.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private void carryForwardMutableState(File current, File staged) throws IOException {
        if (!current.isDirectory()) return;
        File currentData = new File(current, "data");
        File stagedData = new File(staged, "data");
        for (String name : MUTABLE_DATA_DIRS) {
            File source = new File(currentData, name);
            if (!source.exists()) continue;
            File destination = new File(stagedData, name);
            if (destination.exists()) {
                if (destination.isDirectory()) FileUtils.deleteDirectory(destination);
                else if (!destination.delete()) throw new IOException("Unable to replace " + destination);
            }
            if (source.isDirectory()) FileUtils.copyDirectory(source, destination);
            else FileUtils.copyFile(source, destination);
        }
    }

    private void persistFailure(Throwable throwable) {
        try {
            Tools.write(new File(getFilesDir(), "grandleague-server-failure.txt").getAbsolutePath(), Log.getStackTraceString(throwable));
        } catch (Throwable ignored) {
            Log.e(TAG, "Could not save server failure log", ignored);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Grand League local world", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("The 2009Scape server running inside this app");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification notification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("2009Scape Grand Leagues")
                .setContentText(text)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification(text));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
