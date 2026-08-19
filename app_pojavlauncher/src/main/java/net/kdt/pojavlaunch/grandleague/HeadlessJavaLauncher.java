package net.kdt.pojavlaunch.grandleague;

import android.content.Context;
import android.os.Build;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;

import com.oracle.dalvik.VMLauncher;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.JREUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/** Starts a non-graphical JVM in the dedicated Grand League Android process. */
final class HeadlessJavaLauncher {
    private static final String TAG = "GrandLeagueJVM";

    private HeadlessJavaLauncher() { }

    static int launch(Context context, String runtimeName, File workingDirectory, List<String> programArgs) throws Throwable {
        Tools.initContextConstants(context);
        Runtime runtime = MultiRTUtils.forceReread(runtimeName);
        if (runtime.versionString == null) {
            throw new IllegalStateException("Grand League Java runtime is not installed: " + runtimeName);
        }

        String runtimeHome = MultiRTUtils.getRuntimeHome(runtimeName).getAbsolutePath();
        JREUtils.relocateLibPath(runtime, runtimeHome);

        File serverJvm = new File(runtimeHome + "/" + Tools.DIRNAME_HOME_JRE + "/server/libjvm.so");
        File clientJvm = new File(runtimeHome + "/" + Tools.DIRNAME_HOME_JRE + "/client/libjvm.so");
        File jvm = serverJvm.isFile() ? serverJvm : clientJvm;
        if (!jvm.isFile()) {
            throw new IllegalStateException("libjvm.so is missing from embedded Grand League runtime");
        }
        JREUtils.jvmLibraryPath = jvm.getParentFile().getAbsolutePath();

        String ldPath = JREUtils.jvmLibraryPath + ":" + JREUtils.LD_LIBRARY_PATH;
        setEnvironment(runtimeHome, workingDirectory, ldPath);
        JREUtils.setLdLibraryPath(ldPath);

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
        args.add("-Dgrandleague.mobile=true");
        args.add("-Xms128M");
        args.add("-Xmx768M");
        args.addAll(programArgs);

        JREUtils.initJavaRuntime(runtimeHome);
        JREUtils.setupExitTrap(context.getApplicationContext());
        if (JREUtils.chdir(workingDirectory.getAbsolutePath()) != 0) {
            throw new IllegalStateException("Unable to enter embedded Grand League server directory");
        }

        Log.i(TAG, "Launching embedded Grand League server with Java " + runtime.versionString);
        return VMLauncher.launchJVM(args.toArray(new String[0]));
    }

    private static void setEnvironment(String runtimeHome, File workingDirectory, String ldPath) throws ErrnoException {
        Os.setenv("POJAV_NATIVEDIR", Tools.NATIVE_LIB_DIR, true);
        Os.setenv("JAVA_HOME", runtimeHome, true);
        Os.setenv("HOME", workingDirectory.getAbsolutePath(), true);
        Os.setenv("TMPDIR", Tools.DIR_CACHE.getAbsolutePath(), true);
        Os.setenv("LD_LIBRARY_PATH", ldPath, true);
        String oldPath = Os.getenv("PATH");
        Os.setenv("PATH", runtimeHome + "/bin" + (oldPath == null ? "" : ":" + oldPath), true);
    }
}
