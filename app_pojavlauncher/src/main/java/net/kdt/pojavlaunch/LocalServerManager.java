package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Coordinates persistent local settings and the isolated single-player server process. */
public final class LocalServerManager {
    public static final int GAME_PORT = 43595;
    public static final int CONTROL_PORT = 43600;
    private static final String PREFS = "singleplayer";
    private static final String PROFILE_KEY = "profile_name";
    private static final String DEFAULT_PROFILE = "Player";

    private LocalServerManager() { }

    public interface Listener {
        void onStatus(String status);
        void onReady();
        void onError(Exception error);
    }

    public static void startAndWait(Context context, Listener listener) {
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                listener.onStatus("Preparing local world...");
                ensureInstalled(app);
                writeLocalClientConfig(app);
                writeProfileFile(app, getProfileName(app));

                if (!isPortOpen(GAME_PORT, 150)) {
                    listener.onStatus("Starting local 2009Scape server...");
                    Intent service = new Intent(app, LocalServerService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        app.startForegroundService(service);
                    } else {
                        app.startService(service);
                    }
                }

                for (int i = 0; i < 480; i++) {
                    if (isPortOpen(GAME_PORT, 250)) {
                        listener.onStatus("Local server ready.");
                        listener.onReady();
                        return;
                    }
                    Thread.sleep(250L);
                }
                throw new IOException("Local server did not open port " + GAME_PORT + ". Check Server Files/server-failure.txt.");
            } catch (Exception e) {
                listener.onError(e);
            }
        }, "singleplayer-startup").start();
    }

    /** Creates only user-editable/persistent scaffolding. The service owns versioned server payload installation. */
    public static synchronized void ensureInstalled(Context context) throws IOException {
        Tools.initContextConstants(context);
        File root = ServerFilesProvider.ensureServerRoot(context);
        File worldprops = new File(root, "worldprops");
        File data = new File(root, "data");
        new File(data, "snapshots").mkdirs();
        new File(data, "logs").mkdirs();
        new File(data, "players").mkdirs();
        worldprops.mkdirs();

        File localConf = new File(worldprops, "local.conf");
        if (!localConf.isFile()) {
            copyAsset(context, "singleplayer/server-default.conf", localConf);
        }
    }

    public static String getProfileName(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String name = prefs.getString(PROFILE_KEY, DEFAULT_PROFILE);
        return sanitizeProfileName(name);
    }

    public static void setProfileName(Context context, String name) {
        String clean = sanitizeProfileName(name);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PROFILE_KEY, clean)
                .apply();
        try {
            writeProfileFile(context, clean);
        } catch (IOException ignored) { }
    }

    private static String sanitizeProfileName(String value) {
        if (value == null) return DEFAULT_PROFILE;
        String clean = value.trim().replaceAll("[^A-Za-z0-9 _-]", "");
        if (clean.length() == 0) clean = DEFAULT_PROFILE;
        if (clean.length() > 12) clean = clean.substring(0, 12);
        return clean;
    }

    private static void writeProfileFile(Context context, String name) throws IOException {
        Tools.initContextConstants(context);
        File file = new File(Tools.DIR_DATA, "singleplayer-profile.txt");
        writeFileText(file, sanitizeProfileName(name) + "\n");
    }

    private static void writeLocalClientConfig(Context context) throws IOException {
        Tools.initContextConstants(context);
        String json = "{\n"
                + "  \"ip_management\": \"127.0.0.1\",\n"
                + "  \"ip_address\": \"127.0.0.1\",\n"
                + "  \"world\": 1,\n"
                + "  \"server_port\": 43594,\n"
                + "  \"wl_port\": 43595,\n"
                + "  \"js5_port\": 43595,\n"
                + "  \"mouseWheelZoom\": true,\n"
                + "  \"pluginsFolder\": \"plugins\"\n"
                + "}\n";
        writeFileText(new File(Tools.DIR_DATA, "config.json"), json);
    }

    public static boolean isGameServerReady() {
        return isPortOpen(GAME_PORT, 100);
    }

    public static boolean isControlApiReady() {
        return isPortOpen(CONTROL_PORT, 100);
    }

    private static boolean isPortOpen(int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), timeoutMs);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private static void copyAsset(Context context, String asset, File destination) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null) parent.mkdirs();
        File temp = new File(destination.getAbsolutePath() + ".tmp");
        try (InputStream in = context.getAssets().open(asset);
             OutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Could not replace " + destination);
        }
        if (!temp.renameTo(destination)) {
            throw new IOException("Could not install " + destination);
        }
    }

    private static String readStreamText(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void writeFileText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
