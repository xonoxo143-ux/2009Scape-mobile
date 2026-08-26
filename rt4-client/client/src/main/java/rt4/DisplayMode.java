package rt4;

import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;
import plugin.PluginRepository;

import java.awt.*;
import java.util.Objects;
@OriginalClass("client!od")
public final class DisplayMode {

	private static final int HD_FULLSCREEN = 3;
	private static final int HD_RESIZABLE = 2;
	private static final int HD_FIXED = 1;
	private static final int SD_FIXED = 0;
	@OriginalMember(owner = "client!ib", name = "i", descriptor = "[Lclient!od;")
	public static DisplayMode[] aClass114Array1;
	@OriginalMember(owner = "client!rc", name = "M", descriptor = "Z")
	public static boolean aBoolean73 = false;
	@OriginalMember(owner = "client!jk", name = "y", descriptor = "Z")
	public static boolean resizable = false;
	@OriginalMember(owner = "client!hi", name = "f", descriptor = "J")
	public static long aLong89 = 0L;

	@OriginalMember(owner = "client!od", name = "j", descriptor = "I")
	public int width;

	@OriginalMember(owner = "client!od", name = "k", descriptor = "I")
	public int refreshRate;

	@OriginalMember(owner = "client!od", name = "l", descriptor = "I")
	public int height;

	@OriginalMember(owner = "client!od", name = "m", descriptor = "I")
	public int bitDepth;

	@OriginalMember(owner = "client!c", name = "a", descriptor = "(Ljava/awt/Frame;ZLsignlink!ll;)V")
	public static void exitFullScreen(@OriginalArg(0) Frame arg0, @OriginalArg(2) SignLink arg1) {
		while (true) {
			@Pc(16) PrivilegedRequest local16 = arg1.exitFullScreen(arg0);
			while (local16.status == 0) {
				ThreadUtils.sleep(10L);
			}
			if (local16.status == 1) {
				arg0.setVisible(false);
				arg0.dispose();
				return;
			}
			ThreadUtils.sleep(100L);
		}
	}

	@OriginalMember(owner = "client!th", name = "a", descriptor = "(ZIIII)V")
	public static void setWindowMode(@OriginalArg(0) boolean replaceCanvas, @OriginalArg(1) int newMode, @OriginalArg(3) int fullscreenW, @OriginalArg(4) int fullscreenH) {
		System.out.println("Hello from setWindowMode!");
		aLong89 = 0L;
		@Pc(4) int currentMode = getWindowMode();
		if (newMode == 3 || currentMode == 3) {
			replaceCanvas = true;
		}
		@Pc(44) boolean useHd = currentMode > 0 != newMode > 0;
		if (replaceCanvas && newMode > 0) {
			useHd = true;
		}
		setWindowMode(replaceCanvas, newMode, useHd, currentMode, fullscreenW, fullscreenH);
	}

	@OriginalMember(owner = "client!le", name = "a", descriptor = "(I)I")
	public static int getWindowMode() {
		if (GameShell.fullScreenFrame != null) {
			return HD_FULLSCREEN;
		} else if (GlRenderer.enabled && resizable) {
			return HD_RESIZABLE;
		} else if (GlRenderer.enabled && !resizable) {
			return HD_FIXED;
		} else {
			return SD_FIXED;
		}
	}

	@OriginalMember(owner = "client!pm", name = "a", descriptor = "(ZIZIZII)V")
	public static void setWindowMode(@OriginalArg(0) boolean replaceCanvas, @OriginalArg(1) int newMode, @OriginalArg(2) boolean useHD, @OriginalArg(3) int currentMode, @OriginalArg(5) int fullscreenW, @OriginalArg(6) int fullscreenH) {
		System.out.println("Hello from setWindowMode! (the other one!)");
		boolean launchSD = Objects.equals(System.getProperty("launchSD"), "true");
		if (useHD) {
			GlRenderer.quit();
		}
		if (GameShell.fullScreenFrame != null && (newMode != HD_FULLSCREEN || fullscreenW != Preferences.fullScreenWidth || fullscreenH != Preferences.fullScreenHeight)) {
			exitFullScreen(GameShell.fullScreenFrame, GameShell.signLink);
			GameShell.fullScreenFrame = null;
		}
		if (newMode == HD_FULLSCREEN && GameShell.fullScreenFrame == null) {
			GameShell.fullScreenFrame = method3176(0, fullscreenH, fullscreenW, GameShell.signLink);
			if (GameShell.fullScreenFrame != null) {
				Preferences.fullScreenHeight = fullscreenH;
				Preferences.fullScreenWidth = fullscreenW;
				Preferences.write(GameShell.signLink);
			}
		}
		if (newMode == HD_FULLSCREEN && GameShell.fullScreenFrame == null) {
			setWindowMode(true, Preferences.favoriteWorlds, true, currentMode, -1, -1);
			return;
		}
		@Pc(85) Container container;
		if (GameShell.fullScreenFrame != null) {
			container = GameShell.fullScreenFrame;
		} else if (GameShell.frame == null) {
			container = GameShell.signLink.applet;
		} else {
			container = GameShell.frame;
		}
		GameShell.frameWidth = container.getSize().width;
		GameShell.frameHeight = container.getSize().height;
		@Pc(109) Insets insets;
		if (GameShell.frame == container) {
			insets = GameShell.frame.getInsets();
			GameShell.frameWidth -= insets.right + insets.left;
			GameShell.frameHeight -= insets.bottom + insets.top;
		}
		if (newMode == HD_RESIZABLE || newMode == HD_FULLSCREEN) {
			GameShell.canvasWidth = GlRenderer.canvasWidth;
			GameShell.canvasHeight = GlRenderer.canvasHeight;
			GameShell.leftMargin = 0;
			GameShell.topMargin = 0;
		} else {
			GameShell.topMargin = 0;
			GameShell.leftMargin = (GameShell.frameWidth - 765) / 2;
			GameShell.canvasWidth = 765;
			GameShell.canvasHeight = 503;
		}
		if (replaceCanvas) {
			Keyboard.stop(GameShell.canvas);
			Mouse.stop(GameShell.canvas);
			if (client.mouseWheel != null) {
				client.mouseWheel.stop(GameShell.canvas);
			}
			/* Causes the client to hang on Android...
			client.instance.addCanvas();
			*/
			Keyboard.start(GameShell.canvas);
			Mouse.start(GameShell.canvas);
			if (client.mouseWheel != null) {
				client.mouseWheel.start(GameShell.canvas);
			}

		} else {
			if (GlRenderer.enabled) {
				GlRenderer.setCanvasSize(GameShell.canvasWidth, GameShell.canvasHeight);
			}
			GameShell.canvas.setSize(GameShell.canvasWidth, GameShell.canvasHeight);
			if (GameShell.frame == container) {
				insets = GameShell.frame.getInsets();
				GameShell.canvas.setLocation(insets.left + GameShell.leftMargin, insets.top + GameShell.topMargin);
			} else {
				GameShell.canvas.setLocation(GameShell.leftMargin, GameShell.topMargin);
			}
		}
		if (newMode == SD_FIXED && GlRenderer.enabled) {
			// Switch Back from HD to SD (Disabled)
			// GlRenderer.quit();
			// GameShell.frame.setVisible(true);

			//Restore GameShell (It was just reset above)
			GameShell.canvasWidth = GlRenderer.canvasWidth;
			GameShell.canvasHeight = GlRenderer.canvasHeight;
			return;
		}
		if (useHD && newMode != SD_FIXED && !launchSD) {
			GameShell.canvas.setIgnoreRepaint(true);
			GlRenderer.init(null, 0);
		}
		if (!GlRenderer.enabled && newMode != SD_FIXED) {
			setWindowMode(true, 0, true, currentMode, -1, -1);
			return;
		}
		if (newMode != SD_FIXED && currentMode == SD_FIXED) {
			GameShell.thread.setPriority(5);
			SoftwareRaster.frameBuffer = null;
			SoftwareModel.method4580();
			((Js5GlTextureProvider) Rasteriser.textureProvider).method3248(200);
			if (Preferences.highDetailLighting) {
				Rasteriser.setBrightness(0.7F);
			}
			LoginManager.method4637();
		} else if (newMode == SD_FIXED && currentMode != SD_FIXED) {
			GameShell.thread.setPriority(1);
			SoftwareRaster.frameBuffer = FrameBuffer.create(503, 765, GameShell.canvas);
			SoftwareModel.method4583();
			ParticleSystem.quit();
			((Js5GlTextureProvider) Rasteriser.textureProvider).method3248(20);
			if (Preferences.highDetailLighting) {
				if (Preferences.brightness == 1) {
					Rasteriser.setBrightness(0.9F);
				}
				if (Preferences.brightness == 2) {
					Rasteriser.setBrightness(0.8F);
				}
				if (Preferences.brightness == 3) {
					Rasteriser.setBrightness(0.7F);
				}
				if (Preferences.brightness == 4) {
					Rasteriser.setBrightness(0.6F);
				}
			}
			GlTile.method1939();
			LoginManager.method4637();
		}
		SceneGraph.aBoolean130 = !SceneGraph.allLevelsAreVisible();
		if (useHD) {
			client.clearSoftwareRenderer();
		}
		resizable = newMode >= 2;
		if (InterfaceList.topLevelInterface != -1) {
			InterfaceList.method3712(true);
		}
		if (Protocol.socket != null && (client.gameState == 30 || client.gameState == 25)) {
			ClientProt.sendWindowDetails();
		}
		for (@Pc(466) int local466 = 0; local466 < 100; local466++) {
			InterfaceList.aBooleanArray100[local466] = true;
		}
		GameShell.fullRedraw = true;
		PluginRepository.reloadPlugins();
	}

	@OriginalMember(owner = "client!ab", name = "c", descriptor = "(B)[Lclient!od;")
	public static DisplayMode[] getDisplayModes() {
		if (aClass114Array1 == null) {
			@Pc(16) DisplayMode[] local16 = method3558(GameShell.signLink);
			@Pc(20) DisplayMode[] local20 = new DisplayMode[local16.length];
			@Pc(22) int local22 = 0;
			label52:
			for (@Pc(24) int local24 = 0; local24 < local16.length; local24++) {
				@Pc(32) DisplayMode local32 = local16[local24];
				if ((local32.bitDepth <= 0 || local32.bitDepth >= 24) && local32.width >= 800 && local32.height >= 600) {
					for (@Pc(52) int local52 = 0; local52 < local22; local52++) {
						@Pc(59) DisplayMode local59 = local20[local52];
						if (local32.width == local59.width && local59.height == local32.height) {
							if (local32.bitDepth > local59.bitDepth) {
								local20[local52] = local32;
							}
							continue label52;
						}
					}
					local20[local22] = local32;
					local22++;
				}
			}
			aClass114Array1 = new DisplayMode[local22];
			ArrayUtils.copy(local20, 0, aClass114Array1, 0, local22);
			@Pc(112) int[] local112 = new int[aClass114Array1.length];
			for (@Pc(114) int local114 = 0; local114 < aClass114Array1.length; local114++) {
				@Pc(122) DisplayMode local122 = aClass114Array1[local114];
				local112[local114] = local122.height * local122.width;
			}
			ArrayUtils.sort(local112, aClass114Array1);
		}
		return aClass114Array1;
	}

	@OriginalMember(owner = "client!pm", name = "a", descriptor = "(ILsignlink!ll;)[Lclient!od;")
	public static DisplayMode[] method3558(@OriginalArg(1) SignLink arg0) {
		if (!arg0.isFullScreenSupported()) {
			return new DisplayMode[0];
		}
		@Pc(17) PrivilegedRequest local17 = arg0.getDisplayModes();
		while (local17.status == 0) {
			ThreadUtils.sleep(10L);
		}
		if (local17.status == 2) {
			return new DisplayMode[0];
		}
		@Pc(39) int[] local39 = (int[]) local17.result;
		@Pc(45) DisplayMode[] local45 = new DisplayMode[local39.length >> 2];
		for (@Pc(47) int local47 = 0; local47 < local45.length; local47++) {
			@Pc(59) DisplayMode local59 = new DisplayMode();
			local45[local47] = local59;
			local59.width = local39[local47 << 2];
			local59.height = local39[(local47 << 2) + 1];
			local59.bitDepth = local39[(local47 << 2) + 2];
			local59.refreshRate = local39[(local47 << 2) + 3];
		}
		return local45;
	}

	@OriginalMember(owner = "client!nf", name = "a", descriptor = "(IIIIILsignlink!ll;)Ljava/awt/Frame;")
	public static Frame method3176(@OriginalArg(2) int arg0, @OriginalArg(3) int arg1, @OriginalArg(4) int arg2, @OriginalArg(5) SignLink arg3) {
		if (!arg3.isFullScreenSupported()) {
			return null;
		}
		@Pc(20) DisplayMode[] local20 = method3558(arg3);
		if (local20 == null) {
			return null;
		}
		@Pc(27) boolean local27 = false;
		for (@Pc(29) int local29 = 0; local29 < local20.length; local29++) {
			if (arg2 == local20[local29].width && arg1 == local20[local29].height && (!local27 || local20[local29].bitDepth > arg0)) {
				arg0 = local20[local29].bitDepth;
				local27 = true;
			}
		}
		if (!local27) {
			return null;
		}
		@Pc(90) PrivilegedRequest local90 = arg3.enterFullScreen(arg0, arg1, arg2);
		while (local90.status == 0) {
			ThreadUtils.sleep(10L);
		}
		@Pc(103) Frame local103 = (Frame) local90.result;
		if (local103 == null) {
			return null;
		} else if (local90.status == 2) {
			exitFullScreen(local103, arg3);
			return null;
		} else {
			return local103;
		}
	}

}
