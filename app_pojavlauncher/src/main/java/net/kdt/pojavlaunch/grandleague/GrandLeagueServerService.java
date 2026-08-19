package net.kdt.pojavlaunch.grandleague;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.utils.Architecture;

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

/** Hosts the private 2009Scape server in its own Android process. */
public final class GrandLeagueServerService extends Service {
    public static final int SERVER_PORT = 43595;
    private static final String TAG = "GrandLeagueServer";
    private static final String CHANNEL_ID = "grand_league_server";
    private static final int NOTIFICATION_ID = 53095;
    private static final String RUNTIME_NAME = "GrandLeague17";
    private static final String[] MUTABLE_DATA_DIRS = {"players", "eco", "serverstore"};
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, notification("Starting local Grand League world…"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (STARTED.compareAndSet(false, true)) {
            Thread thread = new Thread(this::runServer, "grand-league-server-bootstrap");
            thread.setDaemon(false);
            thread.start();
        }
        return START_STICKY;
    }

    private void runServer() {
        try {
            Tools.initContextConstants(this);
            ensureRuntime17();
            File serverDir = ensureServerPayload();
            updateNotification("Grand League world running locally");

            File jar = new File(serverDir, "server.jar");
            if (!jar.isFile()) throw new IOException("Missing embedded server.jar");

            List<String> args = new ArrayList<>();
            args.add("-jar");
            args.add(jar.getAbsolutePath());
            args.add(new File(serverDir, "worldprops/mobile.conf").getAbsolutePath());
            int exitCode = HeadlessJavaLauncher.launch(this, RUNTIME_NAME, serverDir, args);
            Log.w(TAG, "Local server JVM exited with code " + exitCode);
            updateNotification("Grand League world stopped (code " + exitCode + ")");
        } catch (Throwable throwable) {
            Log.e(TAG, "Unable to start embedded Grand League server", throwable);
            writeFailure(throwable);
            updateNotification("Grand League world failed to start");
        } finally {
            STARTED.set(false);
        }
    }

    private void ensureRuntime17() throws IOException {
        AssetManager assets = getAssets();
        String base = "components/grandleague-jre17";
        String version;
        try (InputStream input = assets.open(base + "/version")) {
            version = Tools.read(input).trim();
        }

        String installed = MultiRTUtils.__internal__readBinpackVersion(RUNTIME_NAME);
        if (version.equals(installed)) return;

        String arch = Architecture.archAsString(Tools.DEVICE_ARCHITECTURE);
        if (!"arm64".equals(arch)) {
            throw new IOException("This Grand Leagues APK currently requires a 64-bit ARM Android device; detected " + arch);
        }

        Log.i(TAG, "Installing stable embedded JRE17 for " + arch);
        try (InputStream runtime = assets.open(base + "/jre17-arm64.tar.xz")) {
            MultiRTUtils.installRuntimeNamed(Tools.NATIVE_LIB_DIR, runtime, RUNTIME_NAME);
        }

        File runtimeHome = MultiRTUtils.getRuntimeHome(RUNTIME_NAME);
        Tools.write(new File(runtimeHome, "pojav_version").getAbsolutePath(), version);
        MultiRTUtils.postPrepare(RUNTIME_NAME);
        MultiRTUtils.forceReread(RUNTIME_NAME);
    }

    private File ensureServerPayload() throws IOException {
        File root = new File(getFilesDir(), "grandleague-server");
        File stamp = new File(root, ".payload-version");
        String packagedVersion;
        try (InputStream input = getAssets().open("grandleague/version.txt")) {
            packagedVersion = Tools.read(input).trim();
        }

        String installedVersion = stamp.isFile() ? Tools.read(stamp.getAbsolutePath()).trim() : "";
        if (packagedVersion.equals(installedVersion) && new File(root, "server.jar").isFile()) {
            return root;
        }

        File staged = new File(getFilesDir(), "grandleague-server.next");
        File previous = new File(getFilesDir(), "grandleague-server.previous");
        if (staged.exists()) FileUtils.deleteDirectory(staged);
        if (previous.exists()) FileUtils.deleteDirectory(previous);
        if (!staged.mkdirs()) throw new IOException("Unable to create staged Grand League server directory");

        extractPayload(staged);
        carryForwardMutableState(root, staged);
        Tools.write(new File(staged, ".payload-version").getAbsolutePath(), packagedVersion + "\n");

        boolean hadPrevious = root.exists();
        if (hadPrevious && !root.renameTo(previous)) {
            FileUtils.deleteDirectory(staged);
            throw new IOException("Unable to stage previous Grand League server for update");
        }

        if (!staged.renameTo(root)) {
            if (hadPrevious && previous.exists() && !previous.renameTo(root)) {
                throw new IOException("Unable to activate new Grand League server or restore previous version");
            }
            throw new IOException("Unable to activate staged Grand League server");
        }

        if (previous.exists()) FileUtils.deleteDirectory(previous);
        return root;
    }

    private void extractPayload(File root) throws IOException {
        try (InputStream raw = getAssets().open("grandleague/server.zip");
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(raw))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            String rootPath = root.getCanonicalPath() + File.separator;
            while ((entry = zip.getNextEntry()) != null) {
                File out = new File(root, entry.getName());
                String canonical = out.getCanonicalPath();
                if (!canonical.startsWith(rootPath)) throw new IOException("Unsafe server payload entry: " + entry.getName());
                if (entry.isDirectory()) {
                    if (!out.isDirectory() && !out.mkdirs()) throw new IOException("Unable to create " + out);
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("Unable to create " + parent);
                    try (BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(out))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) output.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private void carryForwardMutableState(File current, File staged) throws IOException {
        if (!current.isDirectory()) return;

        for (String name : MUTABLE_DATA_DIRS) {
            File source = new File(new File(current, "data"), name);
            if (!source.exists()) continue;

            File destination = new File(new File(staged, "data"), name);
            if (destination.exists()) {
                if (destination.isDirectory()) FileUtils.deleteDirectory(destination);
                else if (!destination.delete()) throw new IOException("Unable to replace staged state: " + destination);
            }

            File parent = destination.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("Unable to create staged data directory: " + parent);
            }

            if (source.isDirectory()) FileUtils.copyDirectory(source, destination);
            else FileUtils.copyFile(source, destination);
        }
    }

    private void writeFailure(Throwable throwable) {
        try {
            File log = new File(getFilesDir(), "grandleague-server-failure.txt");
            Tools.write(log.getAbsolutePath(), Log.getStackTraceString(throwable));
        } catch (Throwable ignored) {
            Log.e(TAG, "Could not persist server failure", ignored);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Grand League world", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Local 2009Scape Grand League server");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
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
