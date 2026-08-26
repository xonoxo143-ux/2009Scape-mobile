package rt4;

import com.jogamp.nativewindow.awt.AWTGraphicsConfiguration;
import com.jogamp.nativewindow.awt.JAWTWindow;
import com.jogamp.opengl.*;
import com.jogamp.opengl.GLCapabilities;
import jogamp.newt.awt.NewtFactoryAWT;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.openal.ALCapabilities;
import org.lwjgl.opengl.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.awt.event.MouseWheelEvent;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Function;

import org.lwjgl.*;
import org.lwjgl.glfw.*;
import org.lwjgl.system.*;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL15.glGenBuffers;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;
import static rt4.GameShell.canvas;
import static rt4.GameShell.frame;
import static rt4.client.gameState;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;

public final class GlRenderer {

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "Ljava/lang/String;")
	private static String vendor;

	@OriginalMember(owner = "client!tf", name = "b", descriptor = "Ljava/lang/String;")
	private static String renderer;
	public static float vFOV = 0;
	public static float hFOV = 0;

	public static int leftMargin;

	public static int topMargin;

	public static int viewportWidth;

	public static int viewportHeight;

	@OriginalMember(owner = "client!tf", name = "c", descriptor = "F")
	private static float depthScaleFactor;

	@OriginalMember(owner = "client!tf", name = "e", descriptor = "I")
	public static int maxTextureUnits = 2;

	@OriginalMember(owner = "client!tf", name = "f", descriptor = "Z")
	public static boolean bigEndian = false;

	@OriginalMember(owner = "client!tf", name = "k", descriptor = "F")
	private static float scaledFarClipDistance;

	@OriginalMember(owner = "client!tf", name = "r", descriptor = "Z")
	public static boolean extTexture3dSupported;

	@OriginalMember(owner = "client!tf", name = "y", descriptor = "Z")
	public static boolean arbMultisampleSupported = false;

	@OriginalMember(owner = "client!tf", name = "z", descriptor = "I")
	public static int anInt5328;

	@OriginalMember(owner = "client!tf", name = "A", descriptor = "I")
	public static int canvasHeight;

	@OriginalMember(owner = "client!tf", name = "C", descriptor = "Z")
	public static boolean arbVboSupported = false;

	private static long LWJGLwindow;

	@OriginalMember(owner = "client!tf", name = "J", descriptor = "I")
	public static int canvasWidth;

	@OriginalMember(owner = "client!tf", name = "K", descriptor = "Z")
	public static boolean arbTextureCubeMapSupported = false;

	@OriginalMember(owner = "client!tf", name = "d", descriptor = "Z")
	private static boolean textureMatrixModified = false;

	@OriginalMember(owner = "client!tf", name = "g", descriptor = "I")
	public static int anInt5323 = 0;

	@OriginalMember(owner = "client!tf", name = "h", descriptor = "I")
	private static int textureCombineAlphaMode = 0;

	@OriginalMember(owner = "client!tf", name = "i", descriptor = "I")
	private static int textureCombineRgbMode = 0;

	@OriginalMember(owner = "client!tf", name = "j", descriptor = "F")
	private static float depthAdjustmentFactor = 0.0F;

	@OriginalMember(owner = "client!tf", name = "l", descriptor = "Z")
	private static boolean lightingEnabled = true;

	@OriginalMember(owner = "client!tf", name = "m", descriptor = "F")
	private static float depthAdjustmentParameter = 0.0F;

	@OriginalMember(owner = "client!tf", name = "n", descriptor = "Z")
	public static boolean normalArrayEnabled = true;

	@OriginalMember(owner = "client!tf", name = "o", descriptor = "Z")
	private static boolean isOrthoViewConfigured = false;

	@OriginalMember(owner = "client!tf", name = "q", descriptor = "F")
	private static final float projectionCoordinateScaleFactor = 0.09765625F;

	@OriginalMember(owner = "client!tf", name = "s", descriptor = "I")
	private static int textureId = -1;

	@OriginalMember(owner = "client!tf", name = "u", descriptor = "Z")
	private static boolean depthTestEnabled = true;

	@OriginalMember(owner = "client!tf", name = "w", descriptor = "Z")
	public static boolean enabled = false;

	@OriginalMember(owner = "client!tf", name = "x", descriptor = "[F")
	private static final float[] matrix = new float[16];

	@OriginalMember(owner = "client!tf", name = "F", descriptor = "Z")
	private static boolean fogEnabled = true;

	public static void glDrawElementsWrapper(int mode, int count, int type, java.nio.Buffer buffer) {
		long pointer = MemoryUtil.memAddress(buffer);
		glDrawElements(mode, count, type, pointer);
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(IIII)V")
	public static void method4148(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int arg3) {
		setupViewTransformations(0, 0, canvasWidth, canvasHeight, arg0, arg1, 0.0F, 0.0F, arg2, arg3);
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "()V")
	public static void setupRgbAlphaMode1Rendering() {
		MaterialManager.setMaterial(0, 0);
		configureOrthographicView();
		setTextureCombineRgbMode(1);
		setTextureCombineAlphaMode(1);
		setLightingEnabled(false);
		setDepthTestEnabled(false);
		setFogEnabled(false);
		resetTextureMatrix();
	}

	@OriginalMember(owner = "client!tf", name = "c", descriptor = "()V")
	public static void setupRgbAlphaMode0Rendering() {
		MaterialManager.setMaterial(0, 0);
		configureOrthographicView();
		setTextureCombineRgbMode(0);
		setTextureCombineAlphaMode(0);
		setLightingEnabled(false);
		setDepthTestEnabled(false);
		setFogEnabled(false);
		resetTextureMatrix();
	}

	@OriginalMember(owner = "client!tf", name = "i", descriptor = "()V")
	public static void setupRenderingWithNoTexture() {
		MaterialManager.setMaterial(0, 0);
		configureOrthographicView();
		setTextureId(-1);
		setLightingEnabled(false);
		setDepthTestEnabled(false);
		setFogEnabled(false);
		resetTextureMatrix();
	}

	@OriginalMember(owner = "client!tf", name = "b", descriptor = "()V")
	public static void resetTextureMatrix() {
		if (textureMatrixModified) {
			glMatrixMode(GL20.GL_TEXTURE);
			glLoadIdentity();
			glMatrixMode(GL20.GL_MODELVIEW);
			textureMatrixModified = false;
		}
	}

	@OriginalMember(owner = "client!tf", name = "d", descriptor = "()V")
	public static void swapBuffers() {
		try {

			if ( !glfwWindowShouldClose(LWJGLwindow) ) {
				if(gameState != 25) // Hack for now to prevent flashing screen on loading new areas
					glfwSwapBuffers(LWJGLwindow); // swap the color buffers
				glfwPollEvents();
			} else {
				glfwTerminate();
				GameShell.instance.mainQuit();
			}
		} catch (@Pc(3) Exception local3) {
		}
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(Z)V")
	public static void setFogEnabled(@OriginalArg(0) boolean enabled) {
		if (enabled == fogEnabled) {
			return;
		}
		if (enabled) {
			glEnable(GL20.GL_FOG);
		} else {
			glDisable(GL20.GL_FOG);
		}
		fogEnabled = enabled;
	}

	@OriginalMember(owner = "client!tf", name = "f", descriptor = "()V")
	private static void resetOpenGLState() {
		isOrthoViewConfigured = false;
		glDisable(GL20.GL_TEXTURE_2D);
		textureId = -1;
		glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_TEXTURE_ENV_MODE, GL20.GL_COMBINE);
		glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_RGB, GL20.GL_MODULATE);
		textureCombineRgbMode = 0;
		glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_ALPHA, GL20.GL_MODULATE);
		textureCombineAlphaMode = 0;
		glEnable(GL20.GL_LIGHTING);
		glEnable(GL20.GL_FOG);
		glEnable(GL20.GL_DEPTH_TEST);
		lightingEnabled = true;
		depthTestEnabled = true;
		fogEnabled = true;
		resetMaterial();
		glActiveTexture(GL20.GL_TEXTURE1);
		glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_TEXTURE_ENV_MODE, GL20.GL_COMBINE);
		glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_RGB, GL20.GL_MODULATE);
		glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_ALPHA, GL20.GL_MODULATE);
		glActiveTexture(GL20.GL_TEXTURE0);
		//setSwapInterval(0);
		glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
		glShadeModel(GL20.GL_SMOOTH);
		glClearDepth(1.0D);
		glDepthFunc(GL20.GL_LEQUAL);
		enableDepthMask();
		glMatrixMode(GL20.GL_TEXTURE);
		glLoadIdentity();
		glPolygonMode(GL20.GL_FRONT, GL20.GL_FILL);
		glEnable(GL20.GL_CULL_FACE);
		glCullFace(GL20.GL_BACK);
		glEnable(GL20.GL_BLEND);										// Enable the OpenGL Blending functionality
		glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);	// Set the blend mode to blend our current RGBA with what is already in the buffer
		glEnable(GL20.GL_ALPHA_TEST);
		glAlphaFunc(GL20.GL_GREATER, 0.0F);
		glEnableClientState(GL20.GL_VERTEX_ARRAY);
		glEnableClientState(GL20.GL_NORMAL_ARRAY);
		normalArrayEnabled = true;
		glEnableClientState(GL20.GL_COLOR_ARRAY);
		glEnableClientState(GL20.GL_TEXTURE_COORD_ARRAY);
		glMatrixMode(GL20.GL_MODELVIEW);
		glLoadIdentity();
		FogManager.setup();
		LightingManager.resetLightingState();
	}

	@OriginalMember(owner = "client!tf", name = "g", descriptor = "()V")
	public static void enableDepthMask() { glDepthMask(true); }

	@OriginalMember(owner = "client!tf", name = "n", descriptor = "()V")
	public static void clearDepthBuffer() { glClear(GL20.GL_DEPTH_BUFFER_BIT); }

	@OriginalMember(owner = "client!tf", name = "q", descriptor = "()V")
	public static void disableDepthMask() { glDepthMask(false); }

	@OriginalMember(owner = "client!tf", name = "r", descriptor = "()F")
	public static float method4179() { return depthAdjustmentParameter; }

	@OriginalMember(owner = "client!tf", name = "l", descriptor = "()F")
	public static float method4166() { return depthAdjustmentFactor; }

	@OriginalMember(owner = "client!gj", name = "b", descriptor = "(I)V")
	public static void resetMaterial() {
		MaterialManager.setMaterial(0, 0);
	}

	@OriginalMember(owner = "client!tf", name = "b", descriptor = "(Z)V")
	public static void setDepthTestEnabled(@OriginalArg(0) boolean enabled) {
		if (enabled == depthTestEnabled) {
			return;
		}
		if (enabled) {
			glEnable(GL20.GL_DEPTH_TEST);
		} else {
			glDisable(GL20.GL_DEPTH_TEST);
		}
		depthTestEnabled = enabled;
	}

	@OriginalMember(owner = "client!tf", name = "h", descriptor = "()V")
	public static void draw() {
		// Set the clear color to black.
		GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		// Clear the screen.
		//GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);

	}

	@OriginalMember(owner = "client!tf", name = "j", descriptor = "()V")
	private static void configureOrthographicView() {
		if (isOrthoViewConfigured) {
			return;
		}
		glMatrixMode(GL20.GL_PROJECTION);		// Switch to the projection matrix so that we can manipulate how our scene is viewed
		glLoadIdentity();					// Reset the projection matrix to the identity matrix so that we don't get any artifacts (cleaning up)
		glOrtho(0, canvasWidth, 0, canvasHeight, -1.0D, 1.0D);
		setViewportBounds(0, 0, canvasWidth, canvasHeight);
		glMatrixMode(GL20.GL_MODELVIEW);		// Switch back to the model view matrix, so that we can start drawing shapes correctly
		glLoadIdentity();					// Reset the projection matrix to the identity matrix so that we don't get any artifacts (cleaning up)
		isOrthoViewConfigured = true;
	}

	@OriginalMember(owner = "client!tf", name = "c", descriptor = "(Z)V")
	public static void setLightingEnabled(@OriginalArg(0) boolean enabled) {
		if (enabled == lightingEnabled) {
			return;
		}
		if (enabled) {
			glEnable(GL20.GL_LIGHTING);
		} else {
			glDisable(GL20.GL_LIGHTING);
		}
		lightingEnabled = enabled;
	}

	@OriginalMember(owner = "client!tf", name = "o", descriptor = "()V")
	public static void quit() {
		if (false) {
			try {
				MaterialManager.quit(); // MaterialManager
			} catch (@Pc(5) Throwable local5) {
			}
			// Release LWJGL
			// Free the window callbacks and destroy the window
			glfwFreeCallbacks(LWJGLwindow);
			glfwDestroyWindow(LWJGLwindow);

			// Terminate GLFW and free the error callback
			glfwTerminate();
			glfwSetErrorCallback(null).free();
			GlCleaner.clear(); // GlCleaner
			LightingManager.releaseLighting();
			enabled = false;
		}
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(FFF)V")
	public static void translateTextureMatrix(@OriginalArg(0) float x, @OriginalArg(1) float y, @OriginalArg(2) float z) {
		glMatrixMode(GL20.GL_TEXTURE);
		if (textureMatrixModified) {
			glLoadIdentity();
		}
		glTranslatef(x, y, z);
		glMatrixMode(GL20.GL_MODELVIEW);
		textureMatrixModified = true;
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(IIIIIIFFII)V")
	public static void setupViewTransformations(@OriginalArg(0) int boxX, @OriginalArg(1) int boxY, @OriginalArg(2) int boxWidth, @OriginalArg(3) int boxHeight, @OriginalArg(4) int offsetX, @OriginalArg(5) int offsetY, @OriginalArg(6) float rotationX, @OriginalArg(7) float rotationY, @OriginalArg(8) int scaleX, @OriginalArg(9) int scaleY) {
		@Pc(7) int scaledBoxStartX = (boxX - offsetX << 8) / scaleX;
		@Pc(17) int scaledBoxEndX = (boxX + boxWidth - offsetX << 8) / scaleX;
		@Pc(25) int scaledBoxStartY = (boxY - offsetY << 8) / scaleY;
		@Pc(35) int scaledBoxEndY = (boxY + boxHeight - offsetY << 8) / scaleY;
		glMatrixMode(GL20.GL_PROJECTION);
		glLoadIdentity();
		configureProjectionMatrix((float) scaledBoxStartX * projectionCoordinateScaleFactor, (float) scaledBoxEndX * projectionCoordinateScaleFactor, (float) -scaledBoxEndY * projectionCoordinateScaleFactor, (float) -scaledBoxStartY * projectionCoordinateScaleFactor, 50.0F, (float) GlobalConfig.VIEW_DISTANCE);
		setViewportBounds(boxX, canvasHeight - boxY - boxHeight, boxWidth, boxHeight);
		glMatrixMode(GL20.GL_MODELVIEW);
		glLoadIdentity();
		glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
		if (rotationX != 0.0F) {
			glRotatef(rotationX, 1.0F, 0.0F, 0.0F);
		}
		if (rotationY != 0.0F) {
			glRotatef(rotationY, 0.0F, 1.0F, 0.0F);
		}
		isOrthoViewConfigured = false;
		Rasteriser.screenLowerX = scaledBoxStartX;
		Rasteriser.screenUpperX = scaledBoxEndX;
		Rasteriser.screenLowerY = scaledBoxStartY;
		Rasteriser.screenUpperY = scaledBoxEndY;
	}

	@OriginalMember(owner = "client!tf", name = "d", descriptor = "(Z)V")
	private static void setNormalArrayEnabled(@OriginalArg(0) boolean enabled) {
		if (enabled == normalArrayEnabled) {
			return;
		}
		if (enabled) {
			glEnableClientState(GL20.GL_NORMAL_ARRAY);
		} else {
			glDisableClientState(GL20.GL_NORMAL_ARRAY);
		}
		normalArrayEnabled = enabled;
	}

	@OriginalMember(owner = "client!tf", name = "p", descriptor = "()V")
	public static void restoreLighting() {
		if (Preferences.highDetailLighting) {
			setLightingEnabled(true);
			setNormalArrayEnabled(true);
		} else {
			setLightingEnabled(false);
			setNormalArrayEnabled(false);
		}
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(I)V")
	public static void setTextureCombineAlphaMode(@OriginalArg(0) int mode) {
		if (mode == textureCombineAlphaMode) {
			return;
		}
		if (mode == 0) {
			glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_ALPHA, GL20.GL_MODULATE);
		}
		if (mode == 1) {
			glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_ALPHA, GL20.GL_REPLACE);
		}
		if (mode == 2) {
			glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_ALPHA, GL20.GL_ADD);
		}
		textureCombineAlphaMode = mode;
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(FFFFFF)V")
	private static void configureProjectionMatrix(@OriginalArg(0) float xMin, @OriginalArg(1) float xMax, @OriginalArg(2) float yMin, @OriginalArg(3) float yMax, @OriginalArg(4) float nearClip, @OriginalArg(5) float farClip) {
		float width = xMax - xMin;
		float height = yMax - yMin;

		hFOV = 2 * (float)Math.atan(width / (2 * nearClip));
		vFOV = 2 * (float)Math.atan(height / (2 * nearClip));
		hFOV = (float)Math.toDegrees(hFOV);
		vFOV = (float)Math.toDegrees(vFOV);

		@Pc(3) float doubleNearClip = nearClip * 2.0F;
		matrix[0] = doubleNearClip / (xMax - xMin);
		matrix[1] = 0.0F;
		matrix[2] = 0.0F;
		matrix[3] = 0.0F;
		matrix[4] = 0.0F;
		matrix[5] = doubleNearClip / (yMax - yMin);
		matrix[6] = 0.0F;
		matrix[7] = 0.0F;
		matrix[8] = (xMax + xMin) / (xMax - xMin);
		matrix[9] = (yMax + yMin) / (yMax - yMin);
		matrix[10] = depthScaleFactor = -(farClip + nearClip) / (farClip - nearClip);
		matrix[11] = -1.0F;
		matrix[12] = 0.0F;
		matrix[13] = 0.0F;
		matrix[14] = scaledFarClipDistance = -(doubleNearClip * farClip) / (farClip - nearClip);
		matrix[15] = 0.0F;
		glLoadMatrixf(matrix);
		depthAdjustmentParameter = 0.0F;
		depthAdjustmentFactor = 0.0F;
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(F)V")
	public static void configureFixedDepthAdjustment(@OriginalArg(0) float multiplier) {
		configureDepthAdjustment(3000.0F, multiplier * 1.5F);
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(FF)V")
	public static void configureDepthAdjustment(@OriginalArg(0) float arg0, @OriginalArg(1) float arg1) {
		if (isOrthoViewConfigured || arg0 == depthAdjustmentParameter && arg1 == depthAdjustmentFactor) {
			return;
		}
		depthAdjustmentParameter = arg0;
		depthAdjustmentFactor = arg1;
		if (arg1 == 0.0F) {
			matrix[10] = depthScaleFactor;
			matrix[14] = scaledFarClipDistance;
		} else {
			@Pc(25) float depthRatio = arg0 / (arg1 + arg0);
			@Pc(29) float depthRatioSquared = depthRatio * depthRatio;
			@Pc(42) float depthAdjustment = -scaledFarClipDistance * (1.0F - depthRatio) * (1.0F - depthRatio) / arg1;
			matrix[10] = depthScaleFactor + depthAdjustment;
			matrix[14] = scaledFarClipDistance * depthRatioSquared;
		}
		glMatrixMode(GL20.GL_PROJECTION);
		glLoadMatrixf(matrix);
		glMatrixMode(GL20.GL_MODELVIEW);
	}

	@OriginalMember(owner = "client!tf", name = "b", descriptor = "(I)V")
	public static void clearColorAndDepthBuffers(@OriginalArg(0) int rgb) {
		glClearColor((float) (rgb >> 16 & 0xFF) / 255.0F, (float) (rgb >> 8 & 0xFF) / 255.0F, (float) (rgb & 0xFF) / 255.0F, 0.0F);
		glClear(GL20.GL_DEPTH_BUFFER_BIT | GL20.GL_COLOR_BUFFER_BIT);
		glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
	}

	@OriginalMember(owner = "client!tf", name = "c", descriptor = "(I)V")
	public static void setTextureId(@OriginalArg(0) int id) {
		if (id == textureId) {
			return;
		}
		if (id == -1) {
			glDisable(GL20.GL_TEXTURE_2D);
		} else {
			if (textureId == -1) {
				glEnable(GL20.GL_TEXTURE_2D);
			}
			glBindTexture(GL20.GL_TEXTURE_2D, id);
		}
		textureId = id;
	}

	private static void initLWJGL() {
		System.out.println("Initializing LWJGL...");  // Add this at the beginning of initLWJGL()

		// Setup an error callback. The default implementation
		// will print the error message in System.err.
		GLFWErrorCallback.createPrint(System.out).set();

		// Initialize GLFW. Most GLFW functions will not work before doing this.
		if ( !glfwInit() )
			throw new IllegalStateException("Unable to initialize GLFW");

		// Configure GLFW
		glfwDefaultWindowHints(); // optional, the current window hints are already the default
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE); // the window will stay hidden after creation
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE); // the window will be resizable

		// Create the window
		LWJGLwindow = glfwCreateWindow(canvasWidth, canvasHeight, "LWJGL Window", 0, 0);
		if ( LWJGLwindow == NULL )
			throw new RuntimeException("Failed to create the GLFW window");

		class KeyMapping {
			int keyCode;
			char keyChar;

			KeyMapping(int keyCode, char keyChar) {
				this.keyCode = keyCode;
				this.keyChar = keyChar;
			}
		}

		Map<Integer, KeyMapping> keyMappings = new HashMap<>();
		keyMappings.put(GLFW.GLFW_KEY_ENTER, new KeyMapping(KeyEvent.VK_ENTER, '\n'));
		keyMappings.put(GLFW.GLFW_KEY_BACKSPACE, new KeyMapping(KeyEvent.VK_BACK_SPACE, '\b'));
		keyMappings.put(GLFW.GLFW_KEY_TAB, new KeyMapping(KeyEvent.VK_TAB, '\t'));
		keyMappings.put(GLFW.GLFW_KEY_UP, new KeyMapping(KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_DOWN, new KeyMapping(KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_LEFT, new KeyMapping(KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_RIGHT, new KeyMapping(KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW_KEY_LEFT_SHIFT, new KeyMapping(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW_KEY_RIGHT_SHIFT, new KeyMapping(KeyEvent.VK_SHIFT, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F1, new KeyMapping(KeyEvent.VK_F1, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F2, new KeyMapping(KeyEvent.VK_F2, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F3, new KeyMapping(KeyEvent.VK_F3, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F4, new KeyMapping(KeyEvent.VK_F4, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F5, new KeyMapping(KeyEvent.VK_F5, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F6, new KeyMapping(KeyEvent.VK_F6, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F7, new KeyMapping(KeyEvent.VK_F7, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F8, new KeyMapping(KeyEvent.VK_F8, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F9, new KeyMapping(KeyEvent.VK_F9, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F10, new KeyMapping(KeyEvent.VK_F10, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F11, new KeyMapping(KeyEvent.VK_F11, KeyEvent.CHAR_UNDEFINED));
		keyMappings.put(GLFW.GLFW_KEY_F12, new KeyMapping(KeyEvent.VK_F12, KeyEvent.CHAR_UNDEFINED));

		Map<Integer, Character> specialCharMappings = new HashMap<>();
		specialCharMappings.put(GLFW.GLFW_KEY_1, '!');
		specialCharMappings.put(GLFW.GLFW_KEY_2, '@');
		specialCharMappings.put(GLFW.GLFW_KEY_3, '#');
		specialCharMappings.put(GLFW.GLFW_KEY_4, '$');
		specialCharMappings.put(GLFW.GLFW_KEY_5, '%');
		specialCharMappings.put(GLFW.GLFW_KEY_6, '^');
		specialCharMappings.put(GLFW.GLFW_KEY_7, '&');
		specialCharMappings.put(GLFW.GLFW_KEY_8, '*');
		specialCharMappings.put(GLFW.GLFW_KEY_9, '(');
		specialCharMappings.put(GLFW.GLFW_KEY_0, ')');
		specialCharMappings.put(GLFW.GLFW_KEY_MINUS, '_');
		specialCharMappings.put(GLFW.GLFW_KEY_EQUAL, '+');
		specialCharMappings.put(GLFW.GLFW_KEY_LEFT_BRACKET, '{');
		specialCharMappings.put(GLFW.GLFW_KEY_RIGHT_BRACKET, '}');
		specialCharMappings.put(GLFW.GLFW_KEY_SEMICOLON, ':');
		specialCharMappings.put(GLFW.GLFW_KEY_APOSTROPHE, '"');
		specialCharMappings.put(GLFW.GLFW_KEY_COMMA, '<');
		specialCharMappings.put(GLFW.GLFW_KEY_PERIOD, '>');
		specialCharMappings.put(GLFW.GLFW_KEY_SLASH, '?');
		specialCharMappings.put(GLFW.GLFW_KEY_BACKSLASH, '|');

		glfwSetKeyCallback(LWJGLwindow, (window, keyCode, scancode, action, mods) -> {
			int id;
			if (action == GLFW.GLFW_PRESS) {
				id = KeyEvent.KEY_PRESSED;
			} else if (action == GLFW.GLFW_RELEASE) {
				id = KeyEvent.KEY_RELEASED;
			} else if (action == GLFW.GLFW_REPEAT) {
				id = KeyEvent.KEY_PRESSED;  // Add key typed event when GLFW_REPEAT action is received
			} else {
				return; // Ignore any other unknown actions
			}

			long when = System.currentTimeMillis();
			char keyChar;
			boolean shiftPressed = (mods & GLFW.GLFW_MOD_SHIFT) != 0;

			KeyMapping mapping = keyMappings.get(keyCode);

			if (mapping != null) {
				keyChar = mapping.keyChar;
			} else {
				if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
					keyChar = (char) (keyCode + (shiftPressed ? 0 : 32)); // Convert to lowercase if Shift is not pressed
				} else if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9 || specialCharMappings.get(keyCode) != null) {
					if(shiftPressed) {
						Character specialChar = specialCharMappings.get(keyCode);
						keyChar = (specialChar != null) ? specialChar : KeyEvent.CHAR_UNDEFINED;
					} else {
						keyChar = (char) (keyCode - GLFW.GLFW_KEY_0 + '0'); // Convert GLFW number key code to corresponding character
					}
				} else {
					keyChar = (char) keyCode;
				}
			}

			KeyEvent event = new KeyEvent(canvas, id, when, 0, (mapping != null) ? mapping.keyCode : keyCode, keyChar, KeyEvent.KEY_LOCATION_STANDARD);

			if (id == KeyEvent.KEY_PRESSED) {
				if(mapping != null) {
					Keyboard.instance.keyPressed(event);
				} else {
					Keyboard.instance.keyTyped(event);
				}
			} else {
				Keyboard.instance.keyReleased(event);
			}
		});

		glfwSetScrollCallback(LWJGLwindow, new GLFWScrollCallback() {
			@Override
			public void invoke(long window, double xoffset, double yoffset) {
				int type = MouseWheelEvent.MOUSE_WHEEL;
				int mods = 0;
				Point point = new Point(0,0);
				int clickCount = 0;
				int scrollType = MouseWheelEvent.WHEEL_UNIT_SCROLL;
				int scrollAmount = 1;
				int wheelRotation = (int)-yoffset;
				boolean popupTrigger = false;

				MouseWheelEvent event = new MouseWheelEvent(canvas, type, System.currentTimeMillis(),
						mods, point.x, point.y, clickCount, popupTrigger,
						scrollType, scrollAmount, wheelRotation);
				canvas.getMouseWheelListeners()[0].mouseWheelMoved(event);
			}
		});

		glfwSetCursorPosCallback(LWJGLwindow, (window, xpos, ypos) -> {
				int id = MouseEvent.MOUSE_MOVED;
				int modifiers = 0;
				Point point = new Point((int) xpos, (int) ypos);
				int clickCount = 0;
				boolean popupTrigger = false;
				MouseEvent event = new MouseEvent(canvas, id, System.currentTimeMillis(), 0, point.x, point.y, 0, popupTrigger);
				Mouse.instance.mouseMoved(event);
		});

		glfwSetMouseButtonCallback(LWJGLwindow, (window, button, action, mods) -> {
			double[] xpos = new double[1];
			double[] ypos = new double[1];
			glfwGetCursorPos(window, xpos, ypos); // get current mouse position
			Point point = new Point((int)xpos[0], (int)ypos[0]);

			// This should trigger normal AWT events in the future, but I got frustrated making it work
			// if we convert it to AWT (such as the other bridges) all plugins will work.
			Mouse.instance.triggerMouseClick(point.x,point.y,button,action);
		});


		// Get the thread stack and push a new frame
		try ( MemoryStack stack = stackPush() ) {
			IntBuffer pWidth = stack.mallocInt(1); // int*
			IntBuffer pHeight = stack.mallocInt(1); // int*

			// Get the window size passed to glfwCreateWindow
			glfwGetWindowSize(LWJGLwindow, pWidth, pHeight);

			// Get the resolution of the primary monitor
			GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

			// Center the window
			glfwSetWindowPos(
					LWJGLwindow,
					(vidmode.width() - pWidth.get(0)) / 2,
					(vidmode.height() - pHeight.get(0)) / 2
			);
		} // the stack frame is popped automatically

		// Make the OpenGL context current
		glfwMakeContextCurrent(LWJGLwindow);
		// Enable v-sync
		glfwSwapInterval(1);

		// Make the window visible
		glfwShowWindow(LWJGLwindow);
		GL.createCapabilities();
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(Ljava/awt/Canvas;I)I")
	public static int init(@OriginalArg(0) Canvas canvas, @OriginalArg(1) int numSamples) {
		System.out.println("Initializing LWJGL...");  // Add this at the beginning of init()
		try {

			int swapBuffersAttempts = 0;
			System.out.println("Hello LWJGL " + Version.getVersion() + "!");


			// Set from inside of pojav launch args for resolution scaling ect
			if(System.getProperty("glfwWidth") != null && System.getProperty("glfwHeight") != null) {
				canvasWidth = Integer.parseInt(System.getProperty("glfwWidth"));
				canvasHeight = Integer.parseInt(System.getProperty("glfwHeight"));
			} else {
				// These are the size of my pixel 6 at 50% resolution scaling..
				canvasWidth = 2400/2;
				canvasHeight = 1080/2;
			}


			if(LWJGLwindow == NULL){
				initLWJGL();
			}

			enabled = true;
			glLineWidth((float) GameShell.canvasScale);
			genTextures();
			resetOpenGLState();
			glClear(GL20.GL_COLOR_BUFFER_BIT);
			setCanvasSize(canvasWidth,canvasHeight);
			setViewportBounds(0,0,canvasWidth,canvasHeight);


			// Check initialization
			while (true) {
				try {
					swapBuffers();
					break;
				} catch (@Pc(86) Exception ex) {
					if (swapBuffersAttempts++ > 5) {
						quit();
						return -3;
					}
				}
			}
			glClear(GL20.GL_COLOR_BUFFER_BIT);
			return 0;
		} catch (@Pc(103) Throwable ex) {
			quit();
			return -5;
		}
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(II)V")
	public static void setCanvasSize(@OriginalArg(0) int width, @OriginalArg(1) int height) {
		GameShell.canvasWidth = width;
		GameShell.canvasHeight = height;
		isOrthoViewConfigured = false;
	}

	public static void setViewportBounds(@OriginalArg(0) int x, @OriginalArg(1) int y, @OriginalArg(2) int width, @OriginalArg(3) int height) {
		leftMargin = x;
		topMargin = y;
		viewportWidth = width;
		viewportHeight = height;
		resizeViewport();
	}

	@OriginalMember(owner = "client!gi", name = "b", descriptor = "()V")
	private static void resizeViewport() {
		glViewport((int) (leftMargin * GameShell.canvasScale + GameShell.subpixelX), (int) (topMargin * GameShell.canvasScale + GameShell.subpixelY),
			(int) (viewportWidth * GameShell.canvasScale + GameShell.subpixelX), (int) (viewportHeight * GameShell.canvasScale + GameShell.subpixelY));
	}

	@OriginalMember(owner = "client!tf", name = "a", descriptor = "(IIIIII)V")
	public static void setupOrthographicProjection(@OriginalArg(0) int xOffset, @OriginalArg(1) int yOffset, @OriginalArg(2) int resolution, @OriginalArg(3) int arg3, @OriginalArg(4) int color, @OriginalArg(5) int cardMemory) {
		@Pc(2) int negXOffset = -xOffset;
		@Pc(6) int adjustedCanvasWidth = canvasWidth - xOffset;
		@Pc(9) int negYOffset = -yOffset;
		@Pc(13) int adjustedCanvasHeight = canvasHeight - yOffset;
		@Pc(23) float resolutionFactor = (float) resolution / 512.0F;
		@Pc(30) float colorDepthFactor = resolutionFactor * (256.0F / (float) color);
		@Pc(37) float memoryFactor = resolutionFactor * (256.0F / (float) cardMemory);
		glMatrixMode(GL20.GL_PROJECTION);
		glLoadIdentity();
		glOrtho((float) negXOffset * colorDepthFactor, (float) adjustedCanvasWidth * colorDepthFactor, (float) -adjustedCanvasHeight * memoryFactor, (float) -negYOffset * memoryFactor, 50 - arg3, GlobalConfig.VIEW_DISTANCE - arg3);
		setViewportBounds(0, 0, canvasWidth, canvasHeight);
		glMatrixMode(GL20.GL_MODELVIEW);
		glLoadIdentity();
		glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
		isOrthoViewConfigured = false;
	}

	@OriginalMember(owner = "client!tf", name = "d", descriptor = "(I)V")
	public static void setTextureCombineRgbMode(@OriginalArg(0) int mode) {
		if (mode == textureCombineRgbMode) {
			return;
		}
		if (mode == 0) {
			glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_RGB, GL20.GL_MODULATE);
		}
		if (mode == 1) {
			glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_RGB, GL20.GL_REPLACE);
		}
		if (mode == 2) {
			glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_RGB, GL20.GL_ADD);
		}
		if (mode == 3) {
			glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_RGB, GL20.GL_SUBTRACT);
		}
		if (mode == 4) {
			glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_RGB, GL20.GL_ADD_SIGNED);
		}
		if (mode == 5) {
			glTexEnvi(GL20.GL_TEXTURE_ENV, GL20.GL_COMBINE_RGB, GL20.GL_INTERPOLATE);
		}
		textureCombineRgbMode = mode;
	}

	@OriginalMember(owner = "client!tf", name = "s", descriptor = "()V")
	private static void genTextures() {
		glBindTexture(GL20.GL_TEXTURE_2D, glGenTextures());
		glTexImage2D(GL20.GL_TEXTURE_2D, 0, 4, 1, 1, 0, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, IntBuffer.wrap(new int[]{-1}));
		LightingManager.init();
		MaterialManager.init();
	}

}
