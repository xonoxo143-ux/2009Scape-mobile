package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.Architecture;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Coordinates the packaged local server, its dedicated Java runtime, and localhost startup. */
public final class LocalServerManager {
    public static final int GAME_PORT = 43595;
    public static final int CONTROL_PORT = 43600;
    public static final String SERVER_RUNTIME_NAME = "SinglePlayerServer17";

    private static final String ASSET_ROOT = "singleplayer/";
    private static final String SERVER_RUNTIME_ASSET = ASSET_ROOT + "server-jre17-arm64.tar.xz";
    private static final String SERVER_RUNTIME_VERSION_ASSET = ASSET_ROOT + "server-jre17-version.txt";
    private static final String SERVER_RUNTIME_VERSION_MARKER = ".singleplayer-runtime-version";
    private static final String VERSION_MARKER = ".server-data-version";
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
                listener.onStatus("Preparing Java 17 server runtime...");
                ensureServerRuntimeInstalled(app);

                listener.onStatus("Preparing local server files...");
                ensureInstalled(app);
                writeLocalClientConfig(app);
                writeProfileFile(app, getProfileName(app));

                if (!isPortOpen(GAME_PORT, 150)) {
                    listener.onStatus("Starting local 2009Scape server...");
                    Intent service = new Intent(app, LocalServerService.class);
                    app.startService(service);
                }

                for (int i = 0; i < 240; i++) {
                    if (isPortOpen(GAME_PORT, 250)) {
                        listener.onStatus("Local server ready.");
                        listener.onReady();
                        return;
                    }
                    Thread.sleep(250L);
                }
                throw new IOException("Local server did not open port " + GAME_PORT + ".");
            } catch (Exception e) {
                listener.onError(e);
            }
        }, "singleplayer-startup").start();
    }

    /**
     * Installs the packaged ARM64 Android Java 17 runtime into a runtime namespace used only by
     * the local server. The existing client runtime remains untouched.
     */
    public static synchronized void ensureServerRuntimeInstalled(Context context) throws IOException {
        Tools.initContextConstants(context.getApplicationContext());

        if (Architecture.getDeviceArchitecture() != Architecture.ARCH_ARM64) {
            throw new IOException("This single-player build currently packages an ARM64-only server runtime.");
        }

        String packagedVersion = readAssetText(context, SERVER_RUNTIME_VERSION_ASSET).trim();
        File runtimeHome = new File(Tools.MULTIRT_HOME, SERVER_RUNTIME_NAME);
        File versionMarker = new File(runtimeHome, SERVER_RUNTIME_VERSION_MARKER);
        String installedVersion = versionMarker.isFile() ? readFileText(versionMarker).trim() : "";

        if (packagedVersion.equals(installedVersion) && isServerRuntimeUsable()) {
            return;
        }

        try (InputStream runtime = context.getAssets().open(SERVER_RUNTIME_ASSET)) {
            MultiRTUtils.installRuntimeNamed(Tools.NATIVE_LIB_DIR, runtime, SERVER_RUNTIME_NAME);
        }

        Runtime installed = MultiRTUtils.forceReread(SERVER_RUNTIME_NAME);
        validateServerRuntime(installed);
        writeFileText(versionMarker, packagedVersion + "\n");
    }

    public static Runtime getServerRuntime() throws IOException {
        Runtime runtime = MultiRTUtils.forceReread(SERVER_RUNTIME_NAME);
        validateServerRuntime(runtime);
        return runtime;
    }

    public static File getServerRuntimeHome() throws IOException {
        Runtime runtime = getServerRuntime();
        return MultiRTUtils.getRuntimeHome(runtime.name);
    }

    private static boolean isServerRuntimeUsable() {
        try {
            validateServerRuntime(MultiRTUtils.forceReread(SERVER_RUNTIME_NAME));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void validateServerRuntime(Runtime runtime) throws IOException {
        if (runtime == null || runtime.versionString == null || runtime.javaVersion < 17) {
            throw new IOException("Dedicated server Java 17 runtime is missing or invalid.");
        }
        if (Architecture.archAsInt(runtime.arch) != Architecture.ARCH_ARM64) {
            throw new IOException("Dedicated server runtime is not ARM64: " + runtime.arch);
        }

        File runtimeHome;
        try {
            runtimeHome = MultiRTUtils.getRuntimeHome(runtime.name);
        } catch (RuntimeException e) {
            throw new IOException("Dedicated server runtime home is broken.", e);
        }

        File release = new File(runtimeHome, "release");
        File jvm = new File(runtimeHome, "lib/server/libjvm.so");
        if (!release.isFile() || !jvm.isFile()) {
            throw new IOException("Dedicated server runtime is incomplete: " + runtimeHome);
        }
    }

    public static synchronized void ensureInstalled(Context context) throws IOException {
        File root = ServerFilesProvider.ensureServerRoot(context);
        File worldprops = new File(root, "worldprops");
        File data = new File(root, "data");
        File snapshots = new File(data, "snapshots");
        File logs = new File(data, "logs");
        File players = new File(data, "players");
        worldprops.mkdirs();
        snapshots.mkdirs();
        logs.mkdirs();
        players.mkdirs();

        String packagedVersion = readAssetText(context, ASSET_ROOT + "server-version.txt").trim();
        File marker = new File(root, VERSION_MARKER);
        String installedVersion = marker.isFile() ? readFileText(marker).trim() : "";
        boolean firstInstall = installedVersion.length() == 0;
        boolean versionChanged = !packagedVersion.equals(installedVersion);

        if (!new File(root, "server.jar").isFile() || versionChanged) {
            copyAsset(context, ASSET_ROOT + "server.jar", new File(root, "server.jar"));
        }

        File localConf = new File(worldprops, "local.conf");
        if (!localConf.isFile()) {
            copyAsset(context, ASSET_ROOT + "server-default.conf", localConf);
        }

        if (firstInstall || versionChanged) {
            try (InputStream in = context.getAssets().open(ASSET_ROOT + "server-data.zip")) {
                extractServerData(in, root, !firstInstall);
            }
            writeFileText(marker, packagedVersion + "\n");
        }
    }

    /** On upgrades, preserve the player-owned mutable portions of server data. */
    private static void extractServerData(InputStream input, File root, boolean updating) throws IOException {
        String rootPath = root.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (updating && isPersistentPath(name)) {
                    zip.closeEntry();
                    continue;
                }
                File out = new File(root, name);
                String canonical = out.getCanonicalPath();
                if (!canonical.startsWith(rootPath)) {
                    throw new IOException("Unsafe path in server data archive: " + name);
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            os.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static boolean isPersistentPath(String name) {
        return name.startsWith("data/players/")
                || name.startsWith("data/serverstore/")
                || name.startsWith("data/logs/")
                || name.startsWith("data/snapshots/")
                || name.startsWith("data/eco/");
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
        File file = new File(Tools.DIR_DATA, "singleplayer-profile.txt");
        writeFileText(file, sanitizeProfileName(name) + "\n");
    }

    private static void writeLocalClientConfig(Context context) throws IOException {
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
             OutputStream out = new BufferedOutputStream(new FileOutputStream(temp))) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Could not replace " + destination);
        }
        if (!temp.renameTo(destination)) {
            throw new IOException("Could not install " + destination);
        }
    }

    private static String readAssetText(Context context, String asset) throws IOException {
        try (InputStream in = context.getAssets().open(asset)) {
            return readStreamText(in);
        }
    }

    private static String readFileText(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            return readStreamText(in);
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
