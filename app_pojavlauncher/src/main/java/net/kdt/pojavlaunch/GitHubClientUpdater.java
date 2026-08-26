package net.kdt.pojavlaunch;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.zip.ZipFile;

/**
 * Updates the RT4 client JAR directly from this project's GitHub master branch.
 *
 * The launcher APK is intentionally kept separate from game/client updates:
 * changing rt4.jar in GitHub is enough to publish a new client build.
 */
public final class GitHubClientUpdater {
    private static final String METADATA_URL =
            "https://api.github.com/repos/xonoxo143-ux/2009Scape-mobile/contents/" +
            "app_pojavlauncher/src/main/assets/rt4.jar?ref=master";
    private static final String USER_AGENT = "2009Scape-Mobile-RT4-Updater";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;

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
        }, "rt4-github-updater").start();
    }

    private static void performUpdate(Context context, Listener listener) throws Exception {
        listener.onStatus("Checking GitHub...");
        JSONObject metadata = fetchMetadata();

        String remoteSha = metadata.getString("sha").toLowerCase(Locale.US);
        long remoteSize = metadata.getLong("size");
        String downloadUrl = metadata.getString("download_url");
        if (downloadUrl == null || downloadUrl.length() == 0 || "null".equals(downloadUrl)) {
            throw new IOException("GitHub did not provide an RT4 download URL.");
        }

        File dataDir;
        if (Tools.DIR_DATA != null) {
            dataDir = new File(Tools.DIR_DATA);
        } else {
            File filesDir = context.getFilesDir();
            dataDir = filesDir.getParentFile() != null ? filesDir.getParentFile() : filesDir;
        }
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IOException("Could not create the launcher data directory.");
        }

        File target = new File(dataDir, "rt4.jar");
        if (target.isFile()) {
            String localSha = gitBlobSha1(target);
            if (remoteSha.equalsIgnoreCase(localSha)) {
                listener.onFinished(false, remoteSha);
                return;
            }
        }

        File temp = new File(dataDir, "rt4.jar.download");
        File previous = new File(dataDir, "rt4.jar.previous");
        if (temp.exists() && !temp.delete()) {
            throw new IOException("Could not clear the previous partial download.");
        }

        listener.onStatus("Downloading RT4 update...");
        downloadAndVerify(downloadUrl, temp, remoteSize, remoteSha, listener);

        listener.onStatus("Validating RT4 client...");
        validateRt4Jar(temp);

        if (previous.exists() && !previous.delete()) {
            throw new IOException("Could not replace the previous-client backup.");
        }
        if (target.exists() && !target.renameTo(previous)) {
            throw new IOException("Could not back up the installed RT4 client.");
        }

        if (!temp.renameTo(target)) {
            if (previous.exists()) {
                // Best-effort rollback.
                previous.renameTo(target);
            }
            throw new IOException("Could not install the downloaded RT4 client.");
        }

        listener.onFinished(true, remoteSha);
    }

    private static JSONObject fetchMetadata() throws Exception {
        HttpURLConnection connection = openConnection(METADATA_URL);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String detail = readResponseMessage(connection);
            connection.disconnect();
            throw new IOException("GitHub metadata request failed (HTTP " + responseCode + ")" + detail);
        }

        String json;
        try (InputStream input = connection.getInputStream()) {
            json = readText(input);
        } finally {
            connection.disconnect();
        }
        return new JSONObject(json);
    }

    private static void downloadAndVerify(String url, File destination, long expectedSize,
                                          String expectedGitSha, Listener listener) throws Exception {
        HttpURLConnection connection = openConnection(url);
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            String detail = readResponseMessage(connection);
            connection.disconnect();
            throw new IOException("RT4 download failed (HTTP " + responseCode + ")" + detail);
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + expectedSize + "\0").getBytes("UTF-8"));
        long copied = 0;
        long nextProgress = 1024L * 1024L;

        try (InputStream input = connection.getInputStream();
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                digest.update(buffer, 0, count);
                copied += count;
                if (copied >= nextProgress && expectedSize > 0) {
                    int percent = (int) Math.min(100, (copied * 100L) / expectedSize);
                    listener.onStatus("Downloading RT4 update... " + percent + "%");
                    nextProgress = copied + (1024L * 1024L);
                }
            }
            output.getFD().sync();
        } finally {
            connection.disconnect();
        }

        if (copied != expectedSize) {
            destination.delete();
            throw new IOException("RT4 download was incomplete: expected " + expectedSize +
                    " bytes, received " + copied + ".");
        }

        String actualGitSha = toHex(digest.digest());
        if (!expectedGitSha.equalsIgnoreCase(actualGitSha)) {
            destination.delete();
            throw new IOException("RT4 download checksum did not match GitHub.");
        }
    }

    private static String gitBlobSha1(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + file.length() + "\0").getBytes("UTF-8"));
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
        }
        return toHex(digest.digest());
    }

    private static void validateRt4Jar(File jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            if (zip.getEntry("rt4/client.class") == null) {
                throw new IOException("Downloaded JAR is not an RT4 mobile client.");
            }
        }
    }

    private static HttpURLConnection openConnection(String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
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
            String message = readText(error).trim();
            if (message.length() > 240) message = message.substring(0, 240);
            return message.length() == 0 ? "" : ": " + message;
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String readText(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toString("UTF-8");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format(Locale.US, "%02x", value & 0xff));
        }
        return builder.toString();
    }
}
