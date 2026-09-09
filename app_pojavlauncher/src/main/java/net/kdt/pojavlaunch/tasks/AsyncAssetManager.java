package net.kdt.pojavlaunch.tasks;


import static net.kdt.pojavlaunch.utils.Architecture.archAsString;
import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class AsyncAssetManager {

    private static final String PLUGIN_PATH = "plugins";
    private static volatile boolean sRuntimePreparationComplete = true;
    private static volatile String sRuntimePreparationError;

    private AsyncAssetManager(){}

    public static boolean isRuntimePreparationComplete() {
        return sRuntimePreparationComplete;
    }

    public static String getRuntimePreparationError() {
        return sRuntimePreparationError;
    }

    /**
     * Attempt to install the java 8 runtime, if necessary
     * @param am App context
     */
    public static void unpackRuntime(AssetManager am) {
        sRuntimePreparationComplete = false;
        sRuntimePreparationError = null;

        /* Check if JRE is included */
        String rt_version = null;
        String current_rt_version = MultiRTUtils.__internal__readBinpackVersion("Internal");
        try {
            rt_version = Tools.read(am.open("components/jre/version"));
        } catch (IOException e) {
            Log.e("JREAuto", "JRE was not included on this APK.", e);
        }
        String exactJREName = MultiRTUtils.getExactJreName(8);
        if(current_rt_version == null && exactJREName != null && !exactJREName.equals("Internal")/*this clause is for when the internal runtime is goofed*/) {
            sRuntimePreparationComplete = true;
            return;
        }
        if(rt_version == null) {
            if (exactJREName == null) {
                sRuntimePreparationError = "Bundled Java 8 runtime metadata is missing.";
            }
            sRuntimePreparationComplete = true;
            return;
        }
        if(rt_version.equals(current_rt_version)) {
            sRuntimePreparationComplete = true;
            return;
        }

        // Mark the task synchronously so the launcher cannot race ahead before the
        // executor starts reading the first tar entry.
        ProgressLayout.setProgress(ProgressLayout.UNPACK_RUNTIME, 0,
                "Preparing Java 8 client runtime...");

        String finalRt_version = rt_version;
        sExecutorService.execute(() -> {
            try {
                MultiRTUtils.installRuntimeNamedBinpack(
                        am.open("components/jre/universal.tar.xz"),
                        am.open("components/jre/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                        "Internal", finalRt_version);
                MultiRTUtils.postPrepare("Internal");
            } catch (Throwable e) {
                String message = e.getMessage();
                sRuntimePreparationError = e.getClass().getSimpleName()
                        + (message == null || message.trim().isEmpty() ? "" : ": " + message.trim());
                Log.e("JREAuto", "Internal JRE unpack failed", e);
            } finally {
                // installRuntimeNamedBinpack clears this on its success path, but failures
                // used to leave the task registered forever and deadlock ScapeLauncher.
                ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME);
                sRuntimePreparationComplete = true;
            }
        });
    }

    /** Unpack single files, with no regard to version tracking */
    public static void unpackSingleFiles(Context ctx){
        ProgressLayout.setProgress(ProgressLayout.EXTRACT_SINGLE_FILES, 0);
        sExecutorService.execute(() -> {
            try {
                Tools.copyAssetFile(ctx, "options.txt", Tools.DIR_GAME_NEW, false);
                Tools.copyAssetFile(ctx, "default.json", Tools.CTRLMAP_PATH, false);
                Tools.copyAssetFile(ctx, "launcher_profiles.json", Tools.DIR_GAME_NEW, false);
            } catch (IOException e) {
                Log.e("AsyncAssetManager", "Failed to unpack critical components !");
            }
            ProgressLayout.clearProgress(ProgressLayout.EXTRACT_SINGLE_FILES);
        });
    }

    public static void unpackComponents(Context ctx){
        ProgressLayout.setProgress(ProgressLayout.EXTRACT_COMPONENTS, 0);
        sExecutorService.execute(() -> {
            try {
                unpackComponent(ctx, "caciocavallo", false);
                unpackComponent(ctx, "caciocavallo17", false);
                // Since the Java module system doesn't allow multiple JARs to declare the same module,
                // we repack them to a single file here
                unpackComponent(ctx, "lwjgl3", false);
                unpackComponent(ctx, "security", true);
                Tools.copyAssetFile(ctx,"rt4.jar",Tools.DIR_DATA, false); // Change this to true if you're working on client features.
                Tools.copyAssetFile(ctx,"config.json",Tools.DIR_DATA, false);

                // Unzip the plugins for use.
                extractAllPlugins(ctx);

            } catch (IOException e) {
                Log.e("AsyncAssetManager", "Failed o unpack components !",e );
            }
            ProgressLayout.clearProgress(ProgressLayout.EXTRACT_COMPONENTS);
        });
    }

    private static void extractAllPlugins(Context ctx) throws IOException {
        // Path for plugins and disabled plugins
        File pluginsDirectory = new File(Tools.DIR_DATA + "/plugins/");
        File disabledPluginsDirectory = new File(Tools.DIR_DATA + "/disabledPlugins/");

        // Check if disabledPluginsDirectory exists, if not, create it.
        if (!disabledPluginsDirectory.exists()) {
            boolean success = disabledPluginsDirectory.mkdirs();
            if (!success) {
                Log.e("TAG", "Failed to create directory: " + disabledPluginsDirectory.getPath());
                // If we failed to create the directory, we can return early from this method
                return;
            }
        }

        String[] plugins = ctx.getAssets().list(PLUGIN_PATH);
        if (plugins != null) {
            for (String plugin : plugins) {
                // Name of the directory that would be created when the plugin is extracted
                String pluginDirectoryName = plugin.substring(0, plugin.lastIndexOf('.'));
                File installedPluginDirectory = new File(pluginsDirectory, pluginDirectoryName);
                File disabledPluginDirectory = new File(disabledPluginsDirectory, pluginDirectoryName);

                // The local single-player login plugin is part of the APK contract rather than
                // an optional user plugin. Refresh it on APK upgrades so a later login fix is
                // actually deployed instead of being hidden by an older extracted copy.
                if ("LocalSinglePlayerLogin.zip".equals(plugin)) {
                    if (installedPluginDirectory.exists()) {
                        FileUtils.deleteDirectory(installedPluginDirectory);
                    }
                    if (disabledPluginDirectory.exists()) {
                        FileUtils.deleteDirectory(disabledPluginDirectory);
                    }
                } else if (installedPluginDirectory.exists() || disabledPluginDirectory.exists()) {
                    // Preserve the user's installed/disabled state for ordinary plugins.
                    continue;
                }

                // Extract the plugin
                Tools.copyAssetFile(ctx, PLUGIN_PATH + "/" + plugin, Tools.DIR_DATA, true);
                Tools.ZipTool.unzip(
                        new File(Tools.DIR_DATA + "/" + plugin),
                        new File(Tools.DIR_DATA + "/plugins/")
                );
            }
        }
    }


    public static void extractPluginZip(File plugin) throws IOException {
        Tools.ZipTool.unzip(plugin, new File(Tools.DIR_DATA + "/plugins/"));
    }

    private static void unpackComponent(Context ctx, String component, boolean privateDirectory) throws IOException {
        AssetManager am = ctx.getAssets();
        String rootDir = privateDirectory ? Tools.DIR_DATA : Tools.DIR_GAME_HOME;

        File versionFile = new File(rootDir + "/" + component + "/version");
        InputStream is = am.open("components/" + component + "/version");
        if(!versionFile.exists()) {
            if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                FileUtils.deleteDirectory(versionFile.getParentFile());
            }
            versionFile.getParentFile().mkdir();

            Log.i("UnpackPrep", component + ": Pack was installed manually, or does not exist, unpacking new...");
            String[] fileList = am.list("components/" + component);
            for(String s : fileList) {
                Tools.copyAssetFile(ctx, "components/" + component + "/" + s, rootDir + "/" + component, true);
            }
        } else {
            FileInputStream fis = new FileInputStream(versionFile);
            String release1 = Tools.read(is);
            String release2 = Tools.read(fis);
            if (!release1.equals(release2)) {
                if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                    FileUtils.deleteDirectory(versionFile.getParentFile());
                }
                versionFile.getParentFile().mkdir();

                String[] fileList = am.list("components/" + component);
                for (String fileName : fileList) {
                    Tools.copyAssetFile(ctx, "components/" + component + "/" + fileName, rootDir + "/" + component, true);
                }
            } else {
                Log.i("UnpackPrep", component + ": Pack is up-to-date with the launcher, continuing...");
            }
        }
    }
}
