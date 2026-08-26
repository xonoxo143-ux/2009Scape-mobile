package net.kdt.pojavlaunch;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.oracle.dalvik.VMLauncher;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs the local 2009Scape server in a dedicated Android process/JVM.
 * Keeping this process separate is important because the SD client also embeds a JVM.
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
            File root = ServerFilesProvider.ensureServerRoot(this);
            File serverJar = new File(root, "server.jar");
            File config = new File(root, "worldprops/local.conf");
            if (!serverJar.isFile()) {
                throw new IllegalStateException("Local server.jar is missing: " + serverJar);
            }
            if (!config.isFile()) {
                throw new IllegalStateException("Local server config is missing: " + config);
            }

            Runtime runtime = MultiRTUtils.forceReread("Internal");
            if (runtime == null) {
                throw new IllegalStateException("Embedded Java runtime is not installed yet.");
            }
            String runtimeHome = MultiRTUtils.getRuntimeHome(runtime.name).getAbsolutePath();

            JREUtils.relocateLibPath(runtime, runtimeHome);
            // setJavaEnvironment currently does not dereference its Activity parameter.
            // The server has no Activity because it intentionally lives in a Service process.
            JREUtils.setJavaEnvironment(null, runtimeHome);

            List<String> args = new ArrayList<>(JREUtils.getJavaArgs(this, runtimeHome, ""));
            purgeArg(args, "-Xms");
            purgeArg(args, "-Xmx");
            purgeArg(args, "-Dorg.lwjgl.opengl.libname");
            purgeArg(args, "-Dorg.lwjgl.util.Debug");
            purgeArg(args, "-Dorg.lwjgl.util.DebugFunctions");
            purgeArg(args, "-Dorg.lwjgl.util.DebugLoader");

            args.add("-Xms128M");
            args.add("-Xmx768M");
            args.add("-Djava.awt.headless=true");
            args.add("-Dsingleplayer=true");
            args.add("-jar");
            args.add(serverJar.getAbsolutePath());
            args.add(config.getAbsolutePath());

            JREUtils.initJavaRuntime(runtimeHome);
            JREUtils.setupExitTrap(getApplicationContext());
            JREUtils.chdir(root.getAbsolutePath());
            args.add(0, "java");

            Log.i(TAG, "Starting local 2009Scape server from " + root);
            int exitCode = VMLauncher.launchJVM(args.toArray(new String[0]));
            Log.i(TAG, "Local server JVM exited with code " + exitCode);
        } catch (Throwable t) {
            Log.e(TAG, "Local server failed", t);
        } finally {
            stopSelf();
        }
    }

    private static void purgeArg(List<String> args, String prefix) {
        Iterator<String> iterator = args.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(prefix)) {
                iterator.remove();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
