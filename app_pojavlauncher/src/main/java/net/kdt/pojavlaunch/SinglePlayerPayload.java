package net.kdt.pojavlaunch;

import android.content.Context;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;

import org.json.JSONArray;
import org.json.JSONObject;

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
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

/** Applies a verified, content-addressed game payload over the APK fallback. */
public final class SinglePlayerPayload {
    public static final int MANIFEST_SCHEMA = 1;
    public static final String ACTIVE_MANIFEST = "active-manifest.json";
    public static final String OBJECTS_DIRECTORY = "objects";
    public static final String RELEASE_PREFIX =
            "https://github.com/xonoxo143-ux/2009Scape-mobile/releases/download/" +
            "singleplayer-payload/";

    private static final String[] REQUIRED_FILES = new String[] {
            "rt4.jar",
            "singleplayer-bootstrap.jar",
            "engine.jar",
            "world-default.conf",
            "world-data.zip",
            "runtime-jre17.tar.xz",
            "runtime-jre17-version.txt",
            "LocalSinglePlayerLogin.zip",
            "MobileTouchControls.zip"
    };

    private static final String[] PRESERVED_WORLD_KEYS = new String[] {
            "enable_bots",
            "bots_influence_ge_price",
            "wild_pvp_enabled",
            "enable_castle_wars"
    };

    private SinglePlayerPayload() {}

    public static String[] requiredFiles() {
        return REQUIRED_FILES.clone();
    }

    public static File getPayloadDirectory(Context context) {
        Tools.initContextConstants(context.getApplicationContext());
        File dir = new File(Tools.DIR_DATA, "singleplayer-payload");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getObjectsDirectory(Context context) {
        File dir = new File(getPayloadDirectory(context), OBJECTS_DIRECTORY);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getActiveManifestFile(Context context) {
        return new File(getPayloadDirectory(context), ACTIVE_MANIFEST);
    }

    public static File getObjectFile(Context context, String sha256) {
        return new File(getObjectsDirectory(context), sha256.toLowerCase(Locale.US));
    }

    public static boolean hasActivePayload(Context context) {
        File manifestFile = getActiveManifestFile(context);
        if (!manifestFile.isFile()) return false;
        try {
            JSONObject manifest = new JSONObject(readFileText(manifestFile));
            validateManifest(manifest, false);
            for (String name : REQUIRED_FILES) {
                JSONObject entry = requireEntry(manifest, name);
                File object = getObjectFile(context, entry.getString("sha256"));
                if (!object.isFile() || object.length() != entry.getLong("size")) return false;
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static JSONObject getActiveManifest(Context context) throws IOException {
        File active = getActiveManifestFile(context);
        if (!active.isFile()) return null;
        try {
            JSONObject manifest = new JSONObject(readFileText(active));
            validateManifest(manifest, false);
            return manifest;
        } catch (Exception e) {
            throw new IOException("Active single-player payload manifest is invalid.", e);
        }
    }

    public static String activeVersion(Context context) {
        try {
            JSONObject manifest = getActiveManifest(context);
            return manifest == null ? "" : manifest.optString("version", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void validateManifest(JSONObject manifest, boolean requireRemoteUrls)
            throws IOException {
        try {
            if (manifest.getInt("schema") != MANIFEST_SCHEMA) {
                throw new IOException("Unsupported payload manifest schema.");
            }
            if (manifest.getString("version").trim().isEmpty()) {
                throw new IOException("Payload version is missing.");
            }
            JSONArray files = manifest.getJSONArray("files");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                String name = file.getString("name");
                if (!seen.add(name)) throw new IOException("Duplicate payload entry: " + name);
                String sha = file.getString("sha256").toLowerCase(Locale.US);
                if (!sha.matches("[0-9a-f]{64}")) {
                    throw new IOException("Invalid SHA-256 for " + name);
                }
                if (file.getLong("size") <= 0) {
                    throw new IOException("Invalid payload size for " + name);
                }
                if (requireRemoteUrls) {
                    String url = file.getString("url");
                    if (!url.startsWith(RELEASE_PREFIX)) {
                        throw new IOException("Untrusted payload URL for " + name);
                    }
                }
            }
            for (String required : REQUIRED_FILES) {
                if (!seen.contains(required)) {
                    throw new IOException("Payload manifest is missing " + required);
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Payload manifest is malformed.", e);
        }
    }

    public static JSONObject requireEntry(JSONObject manifest, String name) throws IOException {
        try {
            JSONArray files = manifest.getJSONArray("files");
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                if (name.equals(file.getString("name"))) return file;
            }
        } catch (Exception e) {
            throw new IOException("Payload manifest entry is invalid: " + name, e);
        }
        throw new IOException("Payload manifest is missing " + name);
    }

    public static void validatePayloadObject(String name, File file) throws IOException {
        if (!file.isFile() || file.length() <= 0) {
            throw new IOException("Payload object is missing: " + name);
        }
        if ("runtime-jre17.tar.xz".equals(name)) return;
        if ("runtime-jre17-version.txt".equals(name)
                || "world-default.conf".equals(name)) {
            if (readFileText(file).trim().isEmpty()) {
                throw new IOException("Payload text file is empty: " + name);
            }
            return;
        }
        try (ZipFile zip = new ZipFile(file)) {
            if ("rt4.jar".equals(name) && zip.getEntry("rt4/client.class") == null) {
                throw new IOException("Payload RT4 JAR is invalid.");
            }
            if ("singleplayer-bootstrap.jar".equals(name)
                    && zip.getEntry("singleplayer/InProcessBootstrap.class") == null) {
                throw new IOException("Payload bootstrap JAR is invalid.");
            }
            if ("world-data.zip".equals(name)
                    && zip.getEntry("data/cache/main_file_cache.dat2") == null) {
                throw new IOException("Payload world data archive is invalid.");
            }
        }
    }

    public static synchronized void writeActiveManifestAtomically(
            Context context, String manifestText) throws IOException {
        File target = getActiveManifestFile(context);
        File temp = new File(target.getAbsolutePath() + ".new");
        writeFileText(temp, manifestText);
        try {
            JSONObject parsed = new JSONObject(readFileText(temp));
            validateManifest(parsed, false);
        } catch (Exception e) {
            temp.delete();
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Could not validate staged payload manifest.", e);
        }
        if (target.exists() && !target.delete()) {
            temp.delete();
            throw new IOException("Could not replace active payload manifest.");
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("Could not activate payload manifest.");
        }
    }

    public static synchronized void apply(Context context) throws IOException {
        Context app = context.getApplicationContext();
        Tools.initContextConstants(app);
        JSONObject manifest = getActiveManifest(app);
        if (manifest == null) return;

        for (String name : REQUIRED_FILES) ensureObjectPresent(app, manifest, name);

        syncRegularFile(app, manifest, "rt4.jar", new File(Tools.DIR_DATA, "rt4.jar"));
        syncRegularFile(app, manifest, "singleplayer-bootstrap.jar",
                new File(Tools.DIR_DATA, "singleplayer-bootstrap.jar"));
        syncRegularFile(app, manifest, "engine.jar",
                new File(SinglePlayerManager.getWorldRoot(app), "engine.jar"));
        applyWorldConfig(app, manifest);
        applyWorldData(app, manifest);
        applyRuntime(app, manifest);
        applyPlugin(app, manifest, "LocalSinglePlayerLogin.zip", "LocalSinglePlayerLogin");
        applyPlugin(app, manifest, "MobileTouchControls.zip", "MobileTouchControls");
    }

    public static synchronized void restoreBundledBaseline(Context context) throws IOException {
        Context app = context.getApplicationContext();
        Tools.initContextConstants(app);
        copyAssetAtomically(app, "rt4.jar", new File(Tools.DIR_DATA, "rt4.jar"));
        copyAssetAtomically(app, "singleplayer-bootstrap.jar",
                new File(Tools.DIR_DATA, "singleplayer-bootstrap.jar"));

        // Force the baseline installers to run, but tell the world installer this
        // is an update so player/account/economy data remains untouched.
        try {
            File runtimeHome = MultiRTUtils.getRuntimeHome(SinglePlayerManager.RUNTIME_NAME);
            writeFileText(new File(runtimeHome, ".singleplayer-runtime-version"),
                    "payload-rollback\n");
        } catch (Exception ignored) {}
        File worldRoot = SinglePlayerManager.getWorldRoot(app);
        writeFileText(new File(worldRoot, ".singleplayer-world-version"),
                "payload-rollback\n");
        SinglePlayerManager.ensureRuntimeInstalled(app);
        SinglePlayerManager.ensureWorldInstalled(app);
        restoreBundledPlugin(app, "LocalSinglePlayerLogin.zip", "LocalSinglePlayerLogin");
        restoreBundledPlugin(app, "MobileTouchControls.zip", "MobileTouchControls");
        clearApplyMarkers(app);
    }

    private static void ensureObjectPresent(Context context, JSONObject manifest, String name)
            throws IOException {
        JSONObject entry = requireEntry(manifest, name);
        File object = getObjectFile(context, entry.optString("sha256"));
        if (!object.isFile() || object.length() != entry.optLong("size", -1L)) {
            throw new IOException("Active payload object is unavailable: " + name);
        }
    }

    private static void syncRegularFile(Context context, JSONObject manifest,
                                        String name, File target) throws IOException {
        JSONObject entry = requireEntry(manifest, name);
        String expected = entry.optString("sha256").toLowerCase(Locale.US);
        if (target.isFile() && expected.equalsIgnoreCase(sha256(target))) return;
        copyFileAtomically(getObjectFile(context, expected), target);
        if (!expected.equalsIgnoreCase(sha256(target))) {
            throw new IOException("Installed " + name + " did not match the payload manifest.");
        }
    }

    private static void applyWorldConfig(Context context, JSONObject manifest) throws IOException {
        JSONObject entry = requireEntry(manifest, "world-default.conf");
        String expected = entry.optString("sha256");
        File marker = new File(getPayloadDirectory(context), ".world-config-sha256");
        File localConf = new File(SinglePlayerManager.getWorldRoot(context), "worldprops/local.conf");
        String applied = marker.isFile() ? readFileText(marker).trim() : "";
        if (expected.equalsIgnoreCase(applied) && localConf.isFile()) return;

        String previous = localConf.isFile() ? readFileText(localConf) : "";
        String refreshed = readFileText(getObjectFile(context, expected));
        if (!previous.isEmpty()) {
            for (String key : PRESERVED_WORLD_KEYS) {
                Boolean value = readBooleanSetting(previous, key);
                if (value != null) refreshed = writeBooleanSetting(refreshed, key, value);
            }
            writeFileText(new File(localConf.getParentFile(), "local.conf.previous"), previous);
        }
        writeFileText(localConf, refreshed);
        writeFileText(marker, expected + "\n");
    }

    private static void applyWorldData(Context context, JSONObject manifest) throws IOException {
        JSONObject entry = requireEntry(manifest, "world-data.zip");
        String expected = entry.optString("sha256");
        File marker = new File(getPayloadDirectory(context), ".world-data-sha256");
        File root = SinglePlayerManager.getWorldRoot(context);
        File cache = new File(root, "data/cache/main_file_cache.dat2");
        String applied = marker.isFile() ? readFileText(marker).trim() : "";
        if (expected.equalsIgnoreCase(applied) && cache.isFile() && cache.length() > 0) return;

        try (InputStream input = new BufferedInputStream(
                new FileInputStream(getObjectFile(context, expected)))) {
            extractWorldData(input, root);
        }
        writeFileText(marker, expected + "\n");
    }

    private static void applyRuntime(Context context, JSONObject manifest) throws IOException {
        String archiveSha = requireEntry(manifest, "runtime-jre17.tar.xz").optString("sha256");
        String versionSha = requireEntry(manifest, "runtime-jre17-version.txt").optString("sha256");
        String fingerprint = archiveSha + ":" + versionSha;
        File marker = new File(getPayloadDirectory(context), ".runtime-sha256");
        String applied = marker.isFile() ? readFileText(marker).trim() : "";
        if (fingerprint.equalsIgnoreCase(applied)) {
            try {
                SinglePlayerManager.getRuntime();
                return;
            } catch (Exception ignored) {}
        }

        try (InputStream runtime = new BufferedInputStream(
                new FileInputStream(getObjectFile(context, archiveSha)))) {
            MultiRTUtils.installRuntimeNamed(Tools.NATIVE_LIB_DIR, runtime,
                    SinglePlayerManager.RUNTIME_NAME);
            MultiRTUtils.postPrepare(SinglePlayerManager.RUNTIME_NAME);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
        }

        Runtime installed = SinglePlayerManager.getRuntime();
        if (installed == null || installed.javaVersion < 17) {
            throw new IOException("Updated Java runtime is invalid.");
        }
        writeFileText(marker, fingerprint + "\n");

        // Keep the APK baseline guard satisfied. The external payload remains the
        // final authority and is re-applied by SinglePlayerManager.prepare().
        File runtimeHome = MultiRTUtils.getRuntimeHome(SinglePlayerManager.RUNTIME_NAME);
        String bundledVersion = readAssetText(context,
                "singleplayer/runtime-jre17-version.txt").trim();
        writeFileText(new File(runtimeHome, ".singleplayer-runtime-version"),
                bundledVersion + "\n");
    }

    private static void applyPlugin(Context context, JSONObject manifest,
                                    String zipName, String directoryName) throws IOException {
        String expected = requireEntry(manifest, zipName).optString("sha256");
        File marker = new File(getPayloadDirectory(context),
                ".plugin-" + directoryName + ".sha256");
        File pluginDir = new File(Tools.DIR_DATA + "/plugins/", directoryName);
        String applied = marker.isFile() ? readFileText(marker).trim() : "";
        if (expected.equalsIgnoreCase(applied) && pluginDir.isDirectory()) return;

        deleteRecursively(pluginDir);
        deleteRecursively(new File(Tools.DIR_DATA + "/disabledPlugins/", directoryName));
        File plugins = new File(Tools.DIR_DATA, "plugins");
        plugins.mkdirs();
        Tools.ZipTool.unzip(getObjectFile(context, expected), plugins);
        writeFileText(marker, expected + "\n");
    }

    private static void clearApplyMarkers(Context context) {
        File dir = getPayloadDirectory(context);
        String[] names = new String[] {
                ".world-config-sha256",
                ".world-data-sha256",
                ".runtime-sha256",
                ".plugin-LocalSinglePlayerLogin.sha256",
                ".plugin-MobileTouchControls.sha256"
        };
        for (String name : names) {
            File marker = new File(dir, name);
            if (marker.exists()) marker.delete();
        }
    }

    private static void restoreBundledPlugin(Context context, String zipName, String directoryName)
            throws IOException {
        File pluginDir = new File(Tools.DIR_DATA + "/plugins/", directoryName);
        deleteRecursively(pluginDir);
        deleteRecursively(new File(Tools.DIR_DATA + "/disabledPlugins/", directoryName));
        File temp = new File(getPayloadDirectory(context), ".baseline-" + zipName);
        copyAssetAtomically(context, "plugins/" + zipName, temp);
        File plugins = new File(Tools.DIR_DATA, "plugins");
        plugins.mkdirs();
        Tools.ZipTool.unzip(temp, plugins);
        temp.delete();
    }

    private static void extractWorldData(InputStream input, File root) throws IOException {
        String rootPath = root.getCanonicalPath() + File.separator;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (isPersistentPath(name)) {
                    zip.closeEntry();
                    continue;
                }
                File out = new File(root, name);
                if (!out.getCanonicalPath().startsWith(rootPath)) {
                    throw new IOException("Unsafe path in world data archive: " + name);
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                } else {
                    File parent = out.getParentFile();
                    if (parent != null) parent.mkdirs();
                    try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) os.write(buffer, 0, read);
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

    private static Boolean readBooleanSetting(String text, String key) {
        Matcher matcher = Pattern.compile("(?m)^\\s*" + Pattern.quote(key)
                + "\\s*=\\s*(true|false)\\s*(?:#.*)?$").matcher(text);
        return matcher.find() ? Boolean.valueOf(matcher.group(1)) : null;
    }

    private static String writeBooleanSetting(String text, String key, boolean value) {
        Matcher matcher = Pattern.compile("(?m)^(\\s*" + Pattern.quote(key)
                + "\\s*=\\s*)(true|false)(.*)$").matcher(text);
        if (!matcher.find()) return text;
        return matcher.replaceFirst(Matcher.quoteReplacement(
                matcher.group(1) + value + matcher.group(3)));
    }

    private static void copyAssetAtomically(Context context, String asset, File target)
            throws IOException {
        try (InputStream input = new BufferedInputStream(context.getAssets().open(asset))) {
            copyStreamAtomically(input, target);
        }
    }

    private static void copyFileAtomically(File source, File target) throws IOException {
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            copyStreamAtomically(input, target);
        }
    }

    private static void copyStreamAtomically(InputStream input, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File temp = new File(target.getAbsolutePath() + ".payload-tmp");
        try (FileOutputStream raw = new FileOutputStream(temp);
             OutputStream output = new BufferedOutputStream(raw)) {
            copy(input, output);
            output.flush();
            raw.getFD().sync();
        }
        if (target.exists() && !target.delete()) {
            temp.delete();
            throw new IOException("Could not replace " + target);
        }
        if (!temp.renameTo(target)) {
            temp.delete();
            throw new IOException("Could not install " + target);
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
    }

    public static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder out = new StringBuilder(64);
            for (byte value : digest.digest()) {
                out.append(String.format(Locale.US, "%02x", value & 0xff));
            }
            return out.toString();
        } catch (Exception e) {
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Could not hash payload file.", e);
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        if (!file.delete()) throw new IOException("Could not delete " + file);
    }

    private static String readAssetText(Context context, String asset) throws IOException {
        try (InputStream input = context.getAssets().open(asset)) {
            return readStreamText(input);
        }
    }

    public static String readFileText(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return readStreamText(input);
        }
    }

    private static String readStreamText(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    public static void writeFileText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream raw = new FileOutputStream(file);
             OutputStream output = new BufferedOutputStream(raw)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
            output.flush();
            raw.getFD().sync();
        }
    }
}
