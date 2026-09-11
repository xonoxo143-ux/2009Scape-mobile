package net.kdt.pojavlaunch;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/** Downloads and atomically activates the complete single-player game payload. */
public final class GitHubClientUpdater {
    private static final String MANIFEST_URL =
            SinglePlayerPayload.RELEASE_PREFIX + "payload-manifest.json";
    private static final String USER_AGENT = "2009Scape-Mobile-Payload-Updater";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 120000;
    private static final int MAX_MANIFEST_BYTES = 256 * 1024;

    private GitHubClientUpdater() {}

    public interface Listener {
        void onStatus(String status);
        void onFinished(boolean updated, String remoteSha);
        void onError(Exception error);
    }

    public static void checkAndUpdate(Context context, Listener listener) {
        final Context appContext = context.getApplicationContext();
        new Thread(() -> {
            try {
                performUpdate(appContext, listener);
            } catch (Exception e) {
                listener.onError(e);
            }
        }, "singleplayer-github-updater").start();
    }

    private static void performUpdate(Context context, Listener listener) throws Exception {
        listener.onStatus("Checking GitHub...");
        String remoteText = fetchText(MANIFEST_URL, MAX_MANIFEST_BYTES);
        JSONObject remote = new JSONObject(remoteText);
        SinglePlayerPayload.validateManifest(remote, true);
        String remoteVersion = remote.getString("version").trim();
        String previousText = null;
        File activeManifest = SinglePlayerPayload.getActiveManifestFile(context);
        if (activeManifest.isFile()) {
            previousText = SinglePlayerPayload.readFileText(activeManifest);
        }
        String previousVersion = SinglePlayerPayload.activeVersion(context);

        long totalBytes = 0L;
        for (String name : SinglePlayerPayload.requiredFiles()) {
            JSONObject entry = SinglePlayerPayload.requireEntry(remote, name);
            File object = SinglePlayerPayload.getObjectFile(context, entry.getString("sha256"));
            if (!isVerifiedObject(name, object, entry)) totalBytes += entry.getLong("size");
        }

        long completedBytes = 0L;
        boolean downloadedAny = false;
        for (String name : SinglePlayerPayload.requiredFiles()) {
            JSONObject entry = SinglePlayerPayload.requireEntry(remote, name);
            File object = SinglePlayerPayload.getObjectFile(context, entry.getString("sha256"));
            if (isVerifiedObject(name, object, entry)) continue;
            downloadedAny = true;
            completedBytes = downloadObject(name, entry, object,
                    completedBytes, totalBytes, listener);
        }

        // One final validation pass before the tiny active-manifest pointer moves.
        for (String name : SinglePlayerPayload.requiredFiles()) {
            JSONObject entry = SinglePlayerPayload.requireEntry(remote, name);
            File object = SinglePlayerPayload.getObjectFile(context, entry.getString("sha256"));
            if (!isVerifiedObject(name, object, entry)) {
                throw new IOException("Payload verification failed before activation: " + name);
            }
        }

        boolean versionChanged = !remoteVersion.equals(previousVersion);
        listener.onStatus("Installing game update...");
        try {
            SinglePlayerPayload.writeActiveManifestAtomically(context, remote.toString(2) + "\n");
            SinglePlayerPayload.apply(context);
        } catch (Exception applyFailure) {
            rollback(context, previousText);
            throw new IOException("Game update could not be activated; previous files were restored.",
                    applyFailure);
        }

        listener.onFinished(versionChanged || downloadedAny, remoteVersion);
    }

    private static void rollback(Context context, String previousText) {
        try {
            if (previousText != null) {
                SinglePlayerPayload.writeActiveManifestAtomically(context, previousText);
                SinglePlayerPayload.apply(context);
            } else {
                File active = SinglePlayerPayload.getActiveManifestFile(context);
                if (active.exists()) active.delete();
                SinglePlayerPayload.restoreBundledBaseline(context);
            }
        } catch (Exception ignored) {
            // Original activation error is more useful to the user. The APK still
            // contains a complete fallback and startup preparation can repair it.
        }
    }

    private static boolean isVerifiedObject(String name, File object, JSONObject entry) {
        try {
            if (!object.isFile() || object.length() != entry.getLong("size")) return false;
            if (!entry.getString("sha256").equalsIgnoreCase(SinglePlayerPayload.sha256(object))) {
                return false;
            }
            SinglePlayerPayload.validatePayloadObject(name, object);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static long downloadObject(String name, JSONObject entry, File destination,
                                       long completedBefore, long totalBytes, Listener listener)
            throws Exception {
        long expectedSize = entry.getLong("size");
        String expectedSha = entry.getString("sha256").toLowerCase(Locale.US);
        String url = entry.getString("url");
        File parent = destination.getParentFile();
        if (parent != null) parent.mkdirs();
        File temp = new File(parent, ".download-" + expectedSha);
        if (temp.exists() && !temp.delete()) {
            throw new IOException("Could not clear an old partial payload download.");
        }

        HttpURLConnection connection = openConnection(url);
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String detail = readResponseMessage(connection);
            connection.disconnect();
            throw new IOException("Payload download failed for " + name + " (HTTP "
                    + responseCode + ")" + detail);
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long copied = 0L;
        long nextProgress = 1024L * 1024L;
        try (InputStream input = new BufferedInputStream(connection.getInputStream());
             FileOutputStream output = new FileOutputStream(temp)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                copied += count;
                if (copied >= nextProgress) {
                    reportProgress(completedBefore + copied, totalBytes, listener);
                    nextProgress = copied + 1024L * 1024L;
                }
            }
            output.getFD().sync();
        } finally {
            connection.disconnect();
        }

        if (copied != expectedSize) {
            temp.delete();
            throw new IOException("Incomplete payload download for " + name + ": expected "
                    + expectedSize + " bytes, received " + copied + ".");
        }
        String actualSha = toHex(digest.digest());
        if (!expectedSha.equalsIgnoreCase(actualSha)) {
            temp.delete();
            throw new IOException("SHA-256 mismatch for " + name + ".");
        }
        SinglePlayerPayload.validatePayloadObject(name, temp);

        if (destination.exists() && !destination.delete()) {
            temp.delete();
            throw new IOException("Could not replace cached payload object for " + name + ".");
        }
        if (!temp.renameTo(destination)) {
            temp.delete();
            throw new IOException("Could not store payload object for " + name + ".");
        }
        reportProgress(completedBefore + copied, totalBytes, listener);
        return completedBefore + copied;
    }

    private static void reportProgress(long completed, long total, Listener listener) {
        if (total <= 0) {
            listener.onStatus("Downloading game update...");
            return;
        }
        int percent = (int) Math.min(100L, (completed * 100L) / total);
        listener.onStatus("Downloading game update... " + percent + "%");
    }

    private static String fetchText(String address, int maximumBytes) throws IOException {
        HttpURLConnection connection = openConnection(address);
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String detail = readResponseMessage(connection);
            connection.disconnect();
            throw new IOException("GitHub payload manifest request failed (HTTP "
                    + responseCode + ")" + detail);
        }
        try (InputStream input = connection.getInputStream()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (output.size() + count > maximumBytes) {
                    throw new IOException("GitHub payload manifest was unexpectedly large.");
                }
                output.write(buffer, 0, count);
            }
            return output.toString("UTF-8");
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(String address) throws IOException {
        URL url = new URL(address);
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IOException("Refusing non-HTTPS payload URL.");
        }
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(true);
        connection.setUseCaches(false);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    private static String readResponseMessage(HttpURLConnection connection) {
        try {
            InputStream error = connection.getErrorStream();
            if (error == null) return "";
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = error.read(buffer)) != -1 && output.size() < 240) {
                output.write(buffer, 0, Math.min(count, 240 - output.size()));
            }
            String message = output.toString("UTF-8").trim();
            return message.isEmpty() ? "" : ": " + message;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return builder.toString();
    }
}
