package net.kdt.pojavlaunch;

import android.content.Context;
import android.content.SharedPreferences;

import com.kdt.mcgui.ProgressLayout;

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
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Installs and prepares the self-contained single-player runtime.
 *
 * RT4 and the 2009Scape world engine share one Java 17 VM. The world engine
 * still speaks the stock loopback protocol so the RT4 client does not need a
 * networking rewrite, but there is no separate Android service or server JVM.
 */
public final class SinglePlayerManager {
    public static final String RUNTIME_NAME = "SinglePlayer17";

    private static final String ASSET_ROOT = "singleplayer/";
    private static final String RUNTIME_ASSET = ASSET_ROOT + "runtime-jre17.tar.xz";
    private static final String RUNTIME_VERSION_ASSET = ASSET_ROOT + "runtime-jre17-version.txt";
    private static final String RUNTIME_VERSION_MARKER = ".singleplayer-runtime-version";
    private static final String WORLD_VERSION_MARKER = ".singleplayer-world-version";

    private static final String PREFS = "singleplayer";
    private static final String PROFILE_KEY = "profile_name";
    private static final String DEFAULT_PROFILE = "Player";

    private SinglePlayerManager() {}

    public static synchronized void prepare(Context context) throws IOException {
        Context app = context.getApplicationContext();
        ensureRuntimeInstalled(app);
        ensureWorldInstalled(app);
        writeLocalClientConfig(app);
        writeProfileFile(app, getProfileName(app));
    }

    public static synchronized void ensureRuntimeInstalled(Context context) throws IOException {
        Tools.initContextConstants(context.getApplicationContext());

        String packagedVersion = readAssetText(context, RUNTIME_VERSION_ASSET).trim();
        File runtimeHome = new File(Tools.MULTIRT_HOME, RUNTIME_NAME);
        File versionMarker = new File(runtimeHome, RUNTIME_VERSION_MARKER);
        String installedVersion = versionMarker.isFile() ? readFileText(versionMarker).trim() : "";

        if (packagedVersion.equals(installedVersion) && isRuntimeUsable()) {
            return;
        }

        try (InputStream runtime = context.getAssets().open(RUNTIME_ASSET)) {
            MultiRTUtils.installRuntimeNamed(Tools.NATIVE_LIB_DIR, runtime, RUNTIME_NAME);
            // The old dedicated server runtime was headless. The shared runtime now
            // also hosts RT4/Caciocavallo, so finish Pojav's AWT/freetype preparation.
            MultiRTUtils.postPrepare(RUNTIME_NAME);
        } finally {
            // MultiRTUtils clears this on success, but guarantee release on any extraction error.
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
        }

        Runtime installed = MultiRTUtils.forceReread(RUNTIME_NAME);
        validateRuntime(installed);
        writeFileText(versionMarker, packagedVersion + "\n");
    }

    public static Runtime getRuntime() throws IOException {
        Runtime runtime = MultiRTUtils.forceReread(RUNTIME_NAME);
        validateRuntime(runtime);
        return runtime;
    }

    public static File getRuntimeHome() throws IOException {
        Runtime runtime = getRuntime();
        return MultiRTUtils.getRuntimeHome(runtime.name);
    }

    private static boolean isRuntimeUsable() {
        try {
            validateRuntime(MultiRTUtils.forceReread(RUNTIME_NAME));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static void validateRuntime(Runtime runtime) throws IOException {
        if (runtime == null || runtime.versionString == null || runtime.javaVersion < 17) {
            throw new IOException("Single-player Java 17 runtime is missing or invalid.");
        }

        int expectedArch = Architecture.getDeviceArchitecture();
        int runtimeArch = Architecture.archAsInt(runtime.arch);
        if (runtimeArch != expectedArch) {
            throw new IOException("Single-player runtime architecture " + runtime.arch
                    + " does not match this device.");
        }

        File runtimeHome;
        try {
            runtimeHome = MultiRTUtils.getRuntimeHome(runtime.name);
        } catch (RuntimeException e) {
            throw new IOException("Single-player runtime home is broken.", e);
        }

        File release = new File(runtimeHome, "release");
        File jvm = new File(runtimeHome, "lib/server/libjvm.so");
        if (!release.isFile() || !jvm.isFile()) {
            throw new IOException("Single-player runtime is incomplete: " + runtimeHome);
        }
    }

    public static synchronized void ensureWorldInstalled(Context context) throws IOException {
        File root = getWorldRoot(context);
        File worldprops = new File(root, "worldprops");
        File data = new File(root, "data");
        new File(data, "snapshots").mkdirs();
        new File(data, "logs").mkdirs();
        new File(data, "players").mkdirs();
        worldprops.mkdirs();

        String packagedVersion = readAssetText(context, ASSET_ROOT + "world-version.txt").trim();
        File marker = new File(root, WORLD_VERSION_MARKER);
        String installedVersion = marker.isFile() ? readFileText(marker).trim() : "";
        boolean firstInstall = installedVersion.length() == 0;
        boolean versionChanged = !packagedVersion.equals(installedVersion);

        if (!new File(root, "engine.jar").isFile() || versionChanged) {
            copyAsset(context, ASSET_ROOT + "engine.jar", new File(root, "engine.jar"));
        }

        File localConf = new File(worldprops, "local.conf");
        if (!localConf.isFile()) {
            copyAsset(context, ASSET_ROOT + "world-default.conf", localConf);
        } else if (versionChanged) {
            String previous = readFileText(localConf);
            String refreshed = readAssetText(context, ASSET_ROOT + "world-default.conf");
            String[] preservedKeys = new String[] {
                    "enable_bots",
                    "bots_influence_ge_price",
                    "wild_pvp_enabled",
                    "enable_castle_wars"
            };
            for (String key : preservedKeys) {
                Boolean value = readBooleanSetting(previous, key);
                if (value != null) refreshed = writeBooleanSetting(refreshed, key, value);
            }
            writeFileText(new File(worldprops, "local.conf.previous"), previous);
            writeFileText(localConf, refreshed);
        }

        if (firstInstall || versionChanged) {
            try (InputStream in = context.getAssets().open(ASSET_ROOT + "world-data.zip")) {
                extractWorldData(in, root, !firstInstall);
            }
            writeFileText(marker, packagedVersion + "\n");
        }
    }

    public static File getWorldRoot(Context context) {
        return ServerFilesProvider.ensureServerRoot(context);
    }

    private static void extractWorldData(InputStream input, File root, boolean updating) throws IOException {
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
                    throw new IOException("Unsafe path in world data archive: " + name);
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
        } catch (IOException ignored) {}
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

    private static Boolean readBooleanSetting(String text, String key) {
        Pattern pattern = Pattern.compile("(?m)^\\s*" + Pattern.quote(key)
                + "\\s*=\\s*(true|false)\\s*(?:#.*)?$");
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Boolean.valueOf(matcher.group(1)) : null;
    }

    private static String writeBooleanSetting(String text, String key, boolean value) {
        Pattern pattern = Pattern.compile("(?m)^(\\s*" + Pattern.quote(key)
                + "\\s*=\\s*)(true|false)(.*)$");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return text;
        return matcher.replaceFirst(Matcher.quoteReplacement(
                matcher.group(1) + value + matcher.group(3)));
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
