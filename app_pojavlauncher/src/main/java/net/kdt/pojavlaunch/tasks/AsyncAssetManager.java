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

    private AsyncAssetManager(){}

    /**
     * Attempt to install the java 8 runtime, if necessary
     * @param am App context
     */
    public static void unpackRuntime(AssetManager am) {
        /* Check if JRE is included */
        String rt_version = null;
        String current_rt_version = MultiRTUtils.__internal__readBinpackVersion("Internal");
        try {
            rt_version = Tools.read(am.open("components/jre/version"));
        } catch (IOException e) {
            Log.e("JREAuto", "JRE was not included on this APK.", e);
        }
        String exactJREName = MultiRTUtils.getExactJreName(8);
        if(current_rt_version == null && exactJREName != null && !exactJREName.equals("Internal")/*this clause is for when the internal runtime is goofed*/) return;
        if(rt_version == null) return;
        if(rt_version.equals(current_rt_version)) return;

        // Install the runtime in an async manner, hope for the best
        String finalRt_version = rt_version;
        sExecutorService.execute(() -> {

            try {
                MultiRTUtils.installRuntimeNamedBinpack(
                        am.open("components/jre/universal.tar.xz"),
                        am.open("components/jre/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                        "Internal", finalRt_version);
                MultiRTUtils.postPrepare("Internal");
            }catch (IOException e) {
                Log.e("JREAuto", "Internal JRE unpack failed", e);
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
                unpackRt4Client(ctx);
                Tools.copyAssetFile(ctx,"config.json",Tools.DIR_DATA, false);

                // Unzip the plugins for use.
                extractAllPlugins(ctx);

            } catch (IOException e) {
                Log.e("AsyncAssetManager", "Failed o unpack components !",e );
            }
            ProgressLayout.clearProgress(ProgressLayout.EXTRACT_COMPONENTS);
        });
    }

    /**
     * Install the bundled RT4 client whenever its build hash changes.
     *
     * Older builds copied rt4.jar only once, so installing a newer APK could
     * silently continue launching the client from the previous APK. The build
     * now packages a SHA-256 value in rt4.version and this marker is copied
     * only after the matching JAR has been installed successfully.
     */
    private static void unpackRt4Client(Context ctx) throws IOException {
        String bundledVersion = Tools.read(ctx.getAssets().open("rt4.version")).trim();
        File installedClient = new File(Tools.DIR_DATA, "rt4.jar");
        File installedVersionFile = new File(Tools.DIR_DATA, "rt4.version");
        String installedVersion = installedVersionFile.isFile()
                ? Tools.read(new FileInputStream(installedVersionFile)).trim()
                : "";

        if (!installedClient.isFile() || !bundledVersion.equals(installedVersion)) {
            Tools.copyAssetFile(ctx, "rt4.jar", Tools.DIR_DATA, true);
            Tools.copyAssetFile(ctx, "rt4.version", Tools.DIR_DATA, true);
            Log.i("AsyncAssetManager", "Installed RT4 client " + bundledVersion);
        }
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

                boolean isMobileBindings = "MobileClientBindings.zip".equals(plugin);

                // Preserve user-managed plugins. MobileClientBindings is part of
                // the Android input bridge, so refresh it with every APK build.
                if (!isMobileBindings && (installedPluginDirectory.exists() || disabledPluginDirectory.exists())) {
                    continue;
                }

                File extractionDirectory = disabledPluginDirectory.exists()
                        ? disabledPluginsDirectory
                        : pluginsDirectory;
                if (isMobileBindings) {
                    File existingDirectory = disabledPluginDirectory.exists()
                            ? disabledPluginDirectory
                            : installedPluginDirectory;
                    if (existingDirectory.exists()) {
                        FileUtils.deleteDirectory(existingDirectory);
                    }
                }

                // Extract the plugin. A disabled mobile bindings plugin stays disabled.
                Tools.copyAssetFile(ctx, PLUGIN_PATH + "/" + plugin, Tools.DIR_DATA, true);
                Tools.ZipTool.unzip(
                        new File(Tools.DIR_DATA + "/" + plugin),
                        extractionDirectory
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
