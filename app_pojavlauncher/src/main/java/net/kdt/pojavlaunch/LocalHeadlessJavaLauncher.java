package net.kdt.pojavlaunch;

import android.content.Context;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.oracle.dalvik.VMLauncher;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/** Launches the modern headless 2009Scape server inside the dedicated :server process. */
final class LocalHeadlessJavaLauncher {
    private static final String TAG = "SinglePlayerJVM";

    private LocalHeadlessJavaLauncher() { }

    static int launch(Context context, String runtimeName, File workingDirectory, List<String> programArgs) throws Throwable {
        Tools.initContextConstants(context);
        Runtime runtime = MultiRTUtils.forceReread(runtimeName);
        if (runtime == null || runtime.versionString == null) {
            throw new IllegalStateException("Single-player server runtime is not installed: " + runtimeName);
        }

        String runtimeHome = MultiRTUtils.getRuntimeHome(runtimeName).getAbsolutePath();
        JREUtils.relocateLibPath(runtime, runtimeHome);
        setEnvironment(runtimeHome, workingDirectory);

        List<String> args = new ArrayList<>();
        args.add("java");
        args.add("-Djava.home=" + runtimeHome);
        args.add("-Djava.io.tmpdir=" + context.getCacheDir().getAbsolutePath());
        args.add("-Duser.home=" + workingDirectory.getAbsolutePath());
        args.add("-Duser.language=" + System.getProperty("user.language", "en"));
        args.add("-Duser.timezone=" + TimeZone.getDefault().getID());
        args.add("-Dos.name=Linux");
        args.add("-Dos.version=Android-" + Build.VERSION.RELEASE);
        args.add("-Djava.awt.headless=true");
        args.add("-Dsingleplayer.mobile=true");
        args.add("-Xms128M");
        args.add("-Xmx1024M");
        args.addAll(programArgs);

        JREUtils.initJavaRuntime(runtimeHome);
        JREUtils.setupExitTrap(context.getApplicationContext());
        if (JREUtils.chdir(workingDirectory.getAbsolutePath()) != 0) {
            throw new IllegalStateException("Unable to enter local server directory");
        }

        Log.i(TAG, "Launching local 2009Scape server with Java " + runtime.versionString);
        return VMLauncher.launchJVM(args.toArray(new String[0]));
    }

    private static void setEnvironment(String runtimeHome, File workingDirectory) throws ErrnoException {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("POJAV_NATIVEDIR", Tools.NATIVE_LIB_DIR);
        env.put("JAVA_HOME", runtimeHome);
        env.put("HOME", workingDirectory.getAbsolutePath());
        env.put("TMPDIR", Tools.DIR_CACHE.getAbsolutePath());
        env.put("LD_LIBRARY_PATH", JREUtils.LD_LIBRARY_PATH);
        String oldPath = Os.getenv("PATH");
        env.put("PATH", runtimeHome + "/bin" + (oldPath == null ? "" : ":" + oldPath));

        for (Map.Entry<String, String> entry : env.entrySet()) {
            if (entry.getValue() != null) {
                Os.setenv(entry.getKey(), entry.getValue(), true);
            }
        }
        JREUtils.setLdLibraryPath(JREUtils.jvmLibraryPath + ":" + JREUtils.LD_LIBRARY_PATH);
    }
}
