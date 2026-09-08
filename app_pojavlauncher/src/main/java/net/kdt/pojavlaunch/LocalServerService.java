package net.kdt.pojavlaunch;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.oracle.dalvik.VMLauncher;

import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Runs the local 2009Scape server in a dedicated Android process and dedicated Java 17 runtime.
 * The RT4 client keeps using its existing runtime in the :game process.
 */
public class LocalServerService extends Service {
    private static final String TAG = "LocalServerService";
    private Thread serverThread;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (this) {
            if (serverThread != null && serverThread.isAlive()) {
                return START_STICKY;
            }
            serverThread = new Thread(this::runServer, "2009scape-local-server-jvm");
            serverThread.start();
        }
        return START_STICKY;
    }

    private void runServer() {
        try {
            // This Service is in its own Android process, so initialize process-local paths here.
            Tools.initContextConstants(getApplicationContext());
            LocalServerManager.clearServerStartupError(this);

            File root = ServerFilesProvider.ensureServerRoot(this);
            File serverJar = new File(root, "server.jar");
            File config = new File(root, "worldprops/local.conf");
            if (!serverJar.isFile()) {
                throw new IllegalStateException("Local server.jar is missing: " + serverJar);
            }
            if (!config.isFile()) {
                throw new IllegalStateException("Local server config is missing: " + config);
            }

            Runtime runtime = LocalServerManager.getServerRuntime();
            File runtimeHomeFile = LocalServerManager.getServerRuntimeHome();
            String runtimeHome = runtimeHomeFile.getAbsolutePath();

            // Configure only what the headless server JVM needs. Do not inherit client renderer,
            // LWJGL, user JVM arguments, or the client's Java runtime selection.
            JREUtils.relocateLibPath(runtime, runtimeHome);
            configureServerEnvironment(runtimeHome, root);

            List<String> args = new ArrayList<>();
            args.add("java");
            // Current server startup exceeds 2 GiB while spawning the world NPC set.
            // Keep the initial commitment moderate, but leave enough ceiling for startup
            // and steady-state play on the ARM64 single-player target.
            args.add("-Xms512M");
            args.add("-Xmx3072M");
            args.add("-Djava.home=" + runtimeHome);
            args.add("-Djava.io.tmpdir=" + Tools.DIR_CACHE.getAbsolutePath());
            args.add("-Duser.home=" + root.getAbsolutePath());
            args.add("-Duser.language=" + System.getProperty("user.language", "en"));
            args.add("-Duser.timezone=" + TimeZone.getDefault().getID());
            args.add("-Dos.name=Linux");
            args.add("-Dos.version=Android-" + Build.VERSION.RELEASE);
            args.add("-Djava.awt.headless=true");
            args.add("-Dsingleplayer=true");
            args.add("-Dlog4j2.formatMsgNoLookups=true");
            args.add("-jar");
            args.add(serverJar.getAbsolutePath());
            // 2009Scape's ServerConfigParser normalizes paths by splitting on '/'.
            // That intentionally supports project-relative paths but strips the leading
            // slash from absolute paths. Since the server JVM chdirs to root below,
            // pass the config exactly the way upstream expects it.
            args.add("worldprops/local.conf");

            JREUtils.initJavaRuntime(runtimeHome);
            JREUtils.setupExitTrap(getApplicationContext());
            JREUtils.chdir(root.getAbsolutePath());

            Log.i(TAG, "Starting local 2009Scape server with " + runtime.versionString
                    + " from " + runtimeHome);
            int exitCode = VMLauncher.launchJVM(args.toArray(new String[0]));
            Log.i(TAG, "Local server JVM exited with code " + exitCode);
            // This service has no normal "server finished" state. If launchJVM returns
            // before Android explicitly tears the process down, startup/runtime failed
            // even when upstream chose exit code 0 for a fatal configuration error.
            throw new IllegalStateException("Server JVM exited unexpectedly with code " + exitCode);
        } catch (Throwable t) {
            LocalServerManager.recordServerStartupError(this, t);
            Log.e(TAG, "Local server failed", t);
        } finally {
            stopSelf();
        }
    }

    private void configureServerEnvironment(String runtimeHome, File serverRoot) throws ErrnoException {
        String ldLibraryPath = JREUtils.LD_LIBRARY_PATH;
        String jvmLibraryPath = runtimeHome + "/lib/server";
        JREUtils.jvmLibraryPath = jvmLibraryPath;

        Os.setenv("JAVA_HOME", runtimeHome, true);
        Os.setenv("HOME", serverRoot.getAbsolutePath(), true);
        Os.setenv("TMPDIR", Tools.DIR_CACHE.getAbsolutePath(), true);
        Os.setenv("PATH", runtimeHome + "/bin:" + Os.getenv("PATH"), true);
        Os.setenv("LD_LIBRARY_PATH", ldLibraryPath, true);

        JREUtils.setLdLibraryPath(jvmLibraryPath + ":" + ldLibraryPath);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
