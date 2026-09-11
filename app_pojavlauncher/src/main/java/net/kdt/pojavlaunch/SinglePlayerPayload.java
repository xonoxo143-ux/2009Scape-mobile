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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Applies the updateable single-player payload over the APK's bundled fallback.
 *
 * The APK remains the stable Android shell. Game/client/world/runtime files may be
 * replaced from the verified GitHub payload directory without reinstalling the APK.
 */
public final class SinglePlayerPayload {
    public static final String BUNDLED_MANIFEST_ASSET = "singleplayer/payload-manifest.json";
    public static final String ACTIVE_MANIFEST = "active-manifest.json";

    private static final String[] PRESERVED_WORLD_KEYS = new String[] {
            "enable_bots",
            "bots_influence_ge_price",
            "wild_pvp_enabled",
            "enable_castle_wars"
    };

    private SinglePlayerPayload() {}

    public static File getPayloadDirectory(Context context) {
        Tools.initContextConstants(context.getApplicationContext());
        File dir = new File(Tools.DIR_DATA, "singleplayer-payload");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getActiveManifestFile(Context context) {
        return new File(getPayloadDirectory(context), ACTIVE_MANIFEST);
    }

    public static JSONObject getInstalledManifest(Context context) throws Exception {
        File active = getActiveManifestFile(context);
        if (active.isFile()) {
            return new JSONObject(readFileText(active));
        }
        return new JSONObject(readAssetText(context, BUNDLED_MANIFEST_ASSET));
    }

    public static String getBundledManifestText(Context context) throws IOException {
        return readAssetText(context, BUNDLED_MANIFEST_ASSET);
    }

    public static synchronized void apply(Context context) throws IOException {
        Context app = context.getApplicationContext();
        Tools.initContextConstants(app);

        JSONObject manifest;
        boolean externalActive = getActiveManifestFile(app).isFile();
        try {
            manifest = getInstalledManifest(app);
        } catch (Exception e) {
            throw new IOException("Single-player payload manifest is invalid.", e);
        }

        syncRegularFile(app, manifest, "rt4.jar", new File(Tools.DIR_DATA, "rt4.jar"));
        syncRegularFile(app, manifest, "singleplayer-bootstrap.jar",
                new File(Tools.DIR_DATA, "singleplayer-bootstrap.jar"));
        syncRegularFile(app, manifest, "engine.jar",
                new File(SinglePlayerManager.getWorldRoot(app), "engine.jar"));

        applyWorldConfig(app, manifest, externalActive);
        applyWorldData(app, manifest, externalActive);
        applyRuntime(app, manifest, externalActive);
        applyPlugin(app, manifest, "LocalSinglePlayerLogin.zip", "LocalSinglePlayerLogin");
        applyPlugin(app, manifest, "MobileTouchControls.zip", "MobileTouchControls");
    }

    private static void syncRegularFile(Context context, JSONObject manifest,
                                        String name, File target) throws IOException {
        String expected = shaFor(manifest, name);
        if (expected.length() == 0) return;
        if (target.isFile() && expected.equalsIgnoreCase(sha256(target))) return;

        try (InputStream input = openSource(context, name)) {
            copyStreamAtomically(input, target);
        }
        String installed = sha256(target);
        if (!expected.equalsIgnoreCase(installed)) {
            throw new IOException("Installed " + name + " did not match the payload manifest.");
        }
    }

    private static void applyWorldConfig(Context context, JSONObject manifest,
                                         boolean externalActive) throws IOException {
        String expected = shaFor(manifest, "world-default.conf");
        if (expected.length() == 0) return;
        File marker = new File(getPayloadDirectory(context), ".world-config-sha256");
        if (!externalActive && !marker.isFile()) {
            writeFileText(marker, expected + "\n");
            return;
        }
        String applied = marker.isFile() ? readFileText(marker).trim() : "";
        if (expected.equalsIgnoreCase(applied)) return;

        File localConf = new File(SinglePlayerManager.getWorldRoot(context), "worldprops/local.conf");
        String previous = localConf.isFile() ? readFileText(localConf) : "";
        String refreshed;
        try (InputStream input = openSource(context, "world-default.conf")) {
            refreshed = readStreamText(input);
        }
        if (previous.length() > 0) {
            for (String key : PRESERVED_WORLD_KEYS) {
                Boolean value = readBooleanSetting(previous, key);
                if (value != null) refreshed = writeBooleanSetting(refreshed, key, value);
            }
            writeFileText(new File(localConf.getParentFile(), "local.conf.previous"), previous);
        }
        writeFileText(localConf, refreshed);
        writeFileText(marker, expected + "\n");
    }

    private static void applyWorldData(Context context, JSONObject manifest,
                                       boolean externalActive) throws IOException {
        String expected = shaFor(manifest, "world-data.zip");
        if (expected.length() == 0) return;
        File marker = new File(getPayloadDirectory(context), ".world-data-sha256");
        if (!externalActive && !marker.isFile()) {
            writeFileText(marker, expected + "\n");
            return;
        }
        String applied = marker.isFile() ? readFileText(marker).trim() : "";
        if (expected.equalsIgnoreCase(applied)) return;

        try (InputStream input = openSource(context, "world-data.zip")) {
            extractWorldData(input, SinglePlayerManager.getWorldRoot(context));
        }
        writeFileText(marker, expected + "\n");
    }

    private static void applyRuntime(Context context, JSONObject manifest,
                                     boolean externalActive) throws IOException {
        String archiveSha = shaFor(manifest, "runtime-jre17.tar.xz");
        String versionSha = shaFor(manifest, "runtime-jre17-version.txt");
        if (archiveSha.length() == 0 || versionSha.length() == 0) return;
        String fingerprint = archiveSha + ":" + versionSha;
        File marker = new File(getPayloadDirectory(context), ".runtime-sha256");
        if (!externalActive && !marker.isFile()) {
            writeFileText(marker, fingerprint + "\n");
            return;
        }
        String applied = marker.isFile() ? readFileText(marker).trim() : "";
        if (fingerprint.equalsIgnoreCase(applied)) return;

        try (InputStream runtime = openSource(context, "runtime-jre17.tar.xz")) {
            MultiRTUtils.installRuntimeNamed(Tools.NATIVE_LIB_DIR, runtime,
                    SinglePlayerManager.RUNTIME_NAME);
            MultiRTUtils.postPrepare(SinglePlayerManager.RUNTIME_NAME);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
        }

        Runtime installed = MultiRTUtils.forceReread(SinglePlayerManager.RUNTIME_NAME);
        if (installed == null || installed.javaVersion < 17) {
            throw new IOException("Updated Java runtime is invalid.");
        }
        writeFileText(marker, fingerprint + "\n");
    }

    private static void applyPlugin(Context context, JSONObject manifest,
                                    String zipName, String directoryName) throws IOException {
        String expected = shaFor(manifest, zipName);
        if (expected.length() == 0) return;
        File payloadDir = getPayloadDirectory(context);
        File marker = new File(payloadDir, ".plugin-" + directoryName + ".sha256");
        File pluginDir = new File(Tools.DIR_DATA + "/plugins/", directoryName);
        String applied = marker.isFile() ? readFileText(marker).trim() : "";
        if (expected.equalsIgnoreCase(applied) && pluginDir.isDirectory()) return;

        File materialized = new File(payloadDir, ".apply-" + zipName);
        try (InputStream input = openSource(context, zipName);
             OutputStream output = new BufferedOutputStream(new FileOutputStream(materialized))) {
            copy(input, output);
        }
        if (!expected.equalsIgnoreCase(sha256(materialized))) {
            materialized.delete();
            throw new IOException("Plugin payload failed validation: " + zipName);
        }

        deleteRecursively(pluginDir);
        deleteRecursively(new File(Tools.DIR_DATA + "/disabledPlugins/", directoryName));
        File plugins = new File(Tools.DIR_DATA, "plugins");
        plugins.mkdirs();
        Tools.ZipTool.unzip(materialized, plugins);
        materialized.delete();
        writeFileText(marker, expected + "\n");
    }

    private static InputStream openSource(Context context, String name) throws IOException {
        File external = new File(getPayloadDirectory(context), name);
        if (external.isFile()) return new BufferedInputStream(new FileInputStream(external));
        return new BufferedInputStream(context.getAssets().open(assetPath(name)));
    }

    private static String assetPath(String name) {
        if ("rt4.jar".equals(name) || "singleplayer-bootstrap.jar".equals(name)) return name;
        if (name.endsWith(".zip") && (name.startsWith("LocalSinglePlayer")
                || name.startsWith("MobileTouchControls"))) return "plugins/" + name;
        return "singleplayer/" + name;
    }

    private static String shaFor(JSONObject manifest, String name) throws IOException {
        try {
            JSONArray files = manifest.getJSONArray("files");
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                if (name.equals(file.getString("name"))) {
                    return file.getString("sha256").toLowerCase(Locale.US);
                }
            }
            return "";
        } catch (Exception e) {
            throw new IOException("Payload manifest entry is invalid: " + name, e);
        }
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

    private static void copyStreamAtomically(InputStream input, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        File temp = new File(target.getAbsolutePath() + ".payload-tmp");
        try (OutputStream output = new BufferedOutputStream(new FileOutputStream(temp))) {
            copy(input, output);
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

    private static String sha256(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            StringBuilder out = new StringBuilder(64);
            for (byte value : digest.digest()) out.append(String.format(Locale.US, "%02x", value & 0xff));
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

    private static String readFileText(File file) throws IOException {
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

    private static void writeFileText(File file, String text) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }
}
