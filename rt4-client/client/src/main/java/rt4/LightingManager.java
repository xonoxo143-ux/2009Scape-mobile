package rt4;

import com.jogamp.opengl.GL2;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import static com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_LIGHT0;
import static com.jogamp.opengl.fixedfunc.GLLightingFunc.GL_LIGHT4;
import static org.lwjgl.opengl.GL11.*;

public class LightingManager {
	@OriginalMember(owner = "client!jf", name = "b", descriptor = "[F")
	private static final float[] lightPosition = new float[]{0.0F, 0.0F, 0.0F, 1.0F};
	@OriginalMember(owner = "client!jf", name = "l", descriptor = "I")
	public static int lightCount = 0;
	@OriginalMember(owner = "client!jf", name = "a", descriptor = "[Lclient!gi;")
	public static Light[] lights;
	@OriginalMember(owner = "client!rh", name = "d", descriptor = "I")
	public static int anInt4866;
	@OriginalMember(owner = "client!aa", name = "m", descriptor = "I")
	public static int anInt15;
	@OriginalMember(owner = "client!gf", name = "M", descriptor = "I")
	public static int anInt4698;
	@OriginalMember(owner = "client!ch", name = "w", descriptor = "I")
	public static int anInt987;
	@OriginalMember(owner = "client!id", name = "b", descriptor = "I")
	public static int anInt2875 = -1;
	@OriginalMember(owner = "client!jf", name = "c", descriptor = "[I")
	private static int[] anIntArray283;
	@OriginalMember(owner = "client!jf", name = "d", descriptor = "I")
	private static int anInt3029;
	@OriginalMember(owner = "client!jf", name = "e", descriptor = "I")
	private static int anInt3030;
	@OriginalMember(owner = "client!jf", name = "f", descriptor = "[Z")
	private static boolean[] enabledLights;
	@OriginalMember(owner = "client!jf", name = "g", descriptor = "[[[I")
	private static int[][][] lightingGraph;
	@OriginalMember(owner = "client!jf", name = "h", descriptor = "[I")
	private static int[] activeLightIDs;
	@OriginalMember(owner = "client!jf", name = "i", descriptor = "I")
	private static int anInt3031;
	@OriginalMember(owner = "client!jf", name = "j", descriptor = "I")
	private static int planeMax;
	@OriginalMember(owner = "client!jf", name = "k", descriptor = "I")
	private static int anInt3033;
	@OriginalMember(owner = "client!jf", name = "m", descriptor = "[Z")
	private static boolean[] aBooleanArray66;
	@OriginalMember(owner = "client!jf", name = "n", descriptor = "I")
	private static int anInt3035;
	@OriginalMember(owner = "client!jf", name = "o", descriptor = "I")
	private static int zMax;
	@OriginalMember(owner = "client!jf", name = "p", descriptor = "I")
	private static int xMax;

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "(IIIIIII)V")
	public static void method2388(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int arg3, @OriginalArg(4) int arg4, @OriginalArg(5) int arg5, @OriginalArg(6) int arg6) {
		if (!Preferences.highDetailLighting) {
			return;
		}
		if (arg0 == 1 && arg5 > 0) {
			method2393(arg1, arg2, arg3, arg4, arg5 - 1, arg6);
		} else if (arg0 == 4 && arg5 < xMax - 1) {
			method2393(arg1, arg2, arg3, arg4, arg5 + 1, arg6);
		} else if (arg0 == 8 && arg6 > 0) {
			method2393(arg1, arg2, arg3, arg4, arg5, arg6 - 1);
		} else if (arg0 == 2 && arg6 < zMax - 1) {
			method2393(arg1, arg2, arg3, arg4, arg5, arg6 + 1);
		} else if (arg0 == 16 && arg5 > 0 && arg6 < zMax - 1) {
			method2393(arg1, arg2, arg3, arg4, arg5 - 1, arg6 + 1);
		} else if (arg0 == 32 && arg5 < xMax - 1 && arg6 < zMax - 1) {
			method2393(arg1, arg2, arg3, arg4, arg5 + 1, arg6 + 1);
		} else if (arg0 == 128 && arg5 > 0 && arg6 > 0) {
			method2393(arg1, arg2, arg3, arg4, arg5 - 1, arg6 - 1);
		} else if (arg0 == 64 && arg5 < xMax - 1 && arg6 > 0) {
			method2393(arg1, arg2, arg3, arg4, arg5 + 1, arg6 - 1);
		}
	}

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "(Lclient!gi;)V")
	public static void addLight(@OriginalArg(0) Light light) {
		if (lightCount >= 255) {
			System.out.println("Number of lights added exceeds maximum!");
		} else {
			lights[lightCount++] = light;
		}
	}

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "()V")
	public static void method2390() {
		for (@Pc(1) int lightIndex = 0; lightIndex < 4; lightIndex++) {
			activeLightIDs[lightIndex] = -1;
			disableLight(lightIndex);
		}
	}

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "(IIIIIIII)V")
	public static void method2391(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int arg3, @OriginalArg(4) int arg4, @OriginalArg(5) int arg5, @OriginalArg(6) int arg6, @OriginalArg(7) int arg7) {
		if (!Preferences.highDetailLighting || anInt3031 == arg3 && anInt3033 == arg4 && anInt3029 == arg5 && anInt3035 == arg6 && anInt3030 == arg7) {
			return;
		}
		@Pc(20) int local20;
		for (local20 = 0; local20 < 4; local20++) {
			aBooleanArray66[local20] = false;
		}
		local20 = 0;
		@Pc(33) int local33 = 0;
		@Pc(35) int local35;
		@Pc(40) int local40;
		label112:
		for (local35 = arg4; local35 <= arg6; local35++) {
			label110:
			for (local40 = arg5; local40 <= arg7; local40++) {
				@Pc(51) int local51 = lightingGraph[arg3][local35][local40];
				while (true) {
					while (true) {
						label96:
						while (true) {
							if (local51 == 0) {
								continue label110;
							}
							@Pc(59) int local59 = (local51 & 0xFF) - 1;
							local51 >>>= 0x8;
							@Pc(65) int local65;
							for (local65 = 0; local65 < local33; local65++) {
								if (local59 == anIntArray283[local65]) {
									continue label96;
								}
							}
							for (local65 = 0; local65 < 4; local65++) {
								if (local59 == activeLightIDs[local65]) {
									if (!aBooleanArray66[local65]) {
										aBooleanArray66[local65] = true;
										local20++;
										if (local20 == 4) {
											break label112;
										}
									}
									continue label96;
								}
							}
							anIntArray283[local33++] = local59;
							local20++;
							if (local20 == 4) {
								break label112;
							}
						}
					}
				}
			}
		}
		for (local35 = 0; local35 < local33; local35++) {
			for (local40 = 0; local40 < 4; local40++) {
				if (!aBooleanArray66[local40]) {
					activeLightIDs[local40] = anIntArray283[local35];
					aBooleanArray66[local40] = true;
					enableLight(local40, lights[anIntArray283[local35]], arg0, arg1, arg2);
					break;
				}
			}
		}
		for (local35 = 0; local35 < 4; local35++) {
			if (!aBooleanArray66[local35]) {
				activeLightIDs[local35] = -1;
				disableLight(local35);
			}
		}
		anInt3031 = arg3;
		anInt3033 = arg4;
		anInt3029 = arg5;
		anInt3035 = arg6;
		anInt3030 = arg7;
	}

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "(III)V")
	public static void method2392() {
		planeMax = 4;
		xMax = 104;
		zMax = 104;
		lightingGraph = new int[planeMax][xMax][zMax];
	}

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "(IIIIII)V")
	public static void method2393(@OriginalArg(0) int cameraX, @OriginalArg(1) int cameraY, @OriginalArg(2) int cameraZ, @OriginalArg(3) int arg3, @OriginalArg(4) int arg4, @OriginalArg(5) int arg5) {
		if (!Preferences.highDetailLighting || anInt3031 == arg3 && anInt3033 == arg4 && anInt3029 == arg5 && anInt3035 == arg4 && anInt3030 == arg5) {
			return;
		}
		@Pc(20) int local20;
		for (local20 = 0; local20 < 4; local20++) {
			aBooleanArray66[local20] = false;
		}
		local20 = 0;
		@Pc(39) int local39 = lightingGraph[arg3][arg4][arg5];
		while (true) {
			@Pc(47) int local47;
			@Pc(53) int local53;
			label72:
			while (local39 != 0) {
				local47 = (local39 & 0xFF) - 1;
				local39 >>>= 0x8;
				for (local53 = 0; local53 < 4; local53++) {
					if (local47 == activeLightIDs[local53]) {
						aBooleanArray66[local53] = true;
						continue label72;
					}
				}
				anIntArray283[local20++] = local47;
			}
			for (local47 = 0; local47 < local20; local47++) {
				for (local53 = 0; local53 < 4; local53++) {
					if (!aBooleanArray66[local53]) {
						activeLightIDs[local53] = anIntArray283[local47];
						aBooleanArray66[local53] = true;
						enableLight(local53, lights[anIntArray283[local47]], cameraX, cameraY, cameraZ);
						break;
					}
				}
			}
			for (local47 = 0; local47 < 4; local47++) {
				if (!aBooleanArray66[local47]) {
					activeLightIDs[local47] = -1;
					disableLight(local47);
				}
			}
			anInt3031 = arg3;
			anInt3033 = arg4;
			anInt3029 = arg5;
			anInt3035 = arg4;
			anInt3030 = arg5;
			return;
		}
	}

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "(IZ)V")
	public static void method2394(@OriginalArg(0) int arg0, @OriginalArg(1) boolean disableFlicker) {
		for (@Pc(1) int i = 0; i < lightCount; i++) {
			lights[i].method1765(disableFlicker, arg0);
		}
		anInt3031 = -1;
		anInt3033 = -1;
		anInt3029 = -1;
		anInt3035 = -1;
		anInt3030 = -1;
	}

	@OriginalMember(owner = "client!jf", name = "b", descriptor = "()V")
	public static void method2395() {
		for (@Pc(1) int i = 0; i < lightCount; i++) {
			@Pc(8) Light light = lights[i];
			@Pc(11) int lightLevel = light.level;
			if (light.aBoolean124) {
				lightLevel = 0;
			}
			@Pc(19) int local19 = light.level;
			if (light.aBoolean126) {
				local19 = 3;
			}
			for (@Pc(26) int local26 = lightLevel; local26 <= local19; local26++) {
				@Pc(31) int local31 = 0;
				@Pc(39) int local39 = (light.z >> 7) - light.radius;
				if (local39 < 0) {
					local31 = -local39;
					local39 = 0;
				}
				@Pc(55) int local55 = (light.z >> 7) + light.radius;
				if (local55 > zMax - 1) {
					local55 = zMax - 1;
				}
				for (@Pc(66) int local66 = local39; local66 <= local55; local66++) {
					@Pc(75) short local75 = light.aShortArray30[local31++];
					@Pc(87) int local87 = (light.x >> 7) + (local75 >> 8) - light.radius;
					@Pc(95) int local95 = local87 + (local75 & 0xFF) - 1;
					if (local87 < 0) {
						local87 = 0;
					}
					if (local95 > xMax - 1) {
						local95 = xMax - 1;
					}
					for (@Pc(110) int local110 = local87; local110 <= local95; local110++) {
						@Pc(121) int local121 = lightingGraph[local26][local110][local66];
						if ((local121 & 0xFF) == 0) {
							lightingGraph[local26][local110][local66] = local121 | i + 1;
						} else if ((local121 & 0xFF00) == 0) {
							lightingGraph[local26][local110][local66] = local121 | i + 1 << 8;
						} else if ((local121 & 0xFF0000) == 0) {
							lightingGraph[local26][local110][local66] = local121 | i + 1 << 16;
						} else if ((local121 & 0xFF000000) == 0) {
							lightingGraph[local26][local110][local66] = local121 | i + 1 << 24;
						}
					}
				}
			}
		}
	}

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "(I)V")
	private static void disableLight(@OriginalArg(0) int i) {
		if (enabledLights[i]) {
			enabledLights[i] = false;
			@Pc(14) int light = i + GL_LIGHT4;
			
			glDisable(light);
		}
	}

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "(IIIII)V")
	public static void refreshLightingStateForPlane(@OriginalArg(0) int plane, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int x, @OriginalArg(4) int z) {
		if (!Preferences.highDetailLighting) {
			return;
		}
		label43:
		for (@Pc(4) int lightIndex = 0; lightIndex < 4; lightIndex++) {
			if (activeLightIDs[lightIndex] != -1) {
				@Pc(20) int local20 = lightingGraph[plane][arg1][arg2];
				@Pc(28) int local28;
				while (local20 != 0) {
					local28 = (local20 & 0xFF) - 1;
					local20 >>>= 0x8;
					if (local28 == activeLightIDs[lightIndex]) {
						continue label43;
					}
				}
				local20 = lightingGraph[plane][x][z];
				while (local20 != 0) {
					local28 = (local20 & 0xFF) - 1;
					local20 >>>= 0x8;
					if (local28 == activeLightIDs[lightIndex]) {
						continue label43;
					}
				}
			}
			activeLightIDs[lightIndex] = -1;
			disableLight(lightIndex);
		}
	}
	@OriginalMember(owner = "client!jf", name = "f", descriptor = "()V")
	public static void init() {
		lights = new Light[255];
		activeLightIDs = new int[4];
		enabledLights = new boolean[4];
		anIntArray283 = new int[4];
		aBooleanArray66 = new boolean[4];
		lightingGraph = new int[planeMax][xMax][zMax];
	}
	@OriginalMember(owner = "client!jf", name = "c", descriptor = "()V")
	public static void releaseLighting() {
		lights = null;
		activeLightIDs = null;
		enabledLights = null;
		anIntArray283 = null;
		aBooleanArray66 = null;
		lightingGraph = null;
	}

	@OriginalMember(owner = "client!jf", name = "e", descriptor = "()V")
	public static void resetLightingState() {
		
		@Pc(3) int lightIndex;
		for (lightIndex = 0; lightIndex < 4; lightIndex++) {
			@Pc(10) int glLightIndex = lightIndex + GL_LIGHT0; // Constant from OpenGL for lighting
			glLightfv(glLightIndex, GL2.GL_AMBIENT, new float[]{0.0F, 0.0F, 0.0F, 1.0F});
			glLightf(glLightIndex, GL2.GL_LINEAR_ATTENUATION, 0.0F);
			glLightf(glLightIndex, GL2.GL_CONSTANT_ATTENUATION, 0.0F);
		}
		for (lightIndex = 0; lightIndex < 4; lightIndex++) {
			activeLightIDs[lightIndex] = -1;
			disableLight(lightIndex);
		}
	}

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "(II[[[Lclient!bj;)V")
	public static void renderLighting(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) Tile[][][] tiles) {
		if (!Preferences.highDetailLighting) {
			return;
		}
		

		// Configure materials, textures, and lighting settings
		MaterialManager.setMaterial(0, 0);
		GlRenderer.setTextureCombineRgbMode(0);
		GlRenderer.resetTextureMatrix();
		GlRenderer.setTextureId(GlRenderer.anInt5328);
		glDepthMask(false);
		GlRenderer.setLightingEnabled(false);
		glBlendFunc(GL2.GL_DST_COLOR, GL2.GL_ONE);
		glFogfv(GL2.GL_FOG_COLOR, new float[]{0.0F, 0.0F, 0.0F, 0.0F});
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_CONSTANT);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_ALPHA);
		label71:
		for (@Pc(56) int i = 0; i < lightCount; i++) {
			@Pc(63) Light light = lights[i];
			@Pc(66) int lightLevel = light.level;
			if (light.doesNotInteractWithLight) {
				lightLevel--;
			}
			if (light.aClass45_1 != null) {
				@Pc(76) int local76 = 0;
				@Pc(84) int zStart = (light.z >> 7) - light.radius;
				@Pc(92) int zEnd = (light.z >> 7) + light.radius;

				if (zEnd >= anInt4866) {
					zEnd = anInt4866 - 1;
				}
				if (zStart < anInt4698) {
					local76 = anInt4698 - zStart;
					zStart = anInt4698;
				}

				for (@Pc(112) int z = zStart; z <= zEnd; z++) {
					@Pc(121) short local121 = light.aShortArray30[local76++];
					@Pc(133) int xStart = (light.x >> 7) + (local121 >> 8) - light.radius;
					@Pc(141) int xEnd = xStart + (local121 & 0xFF) - 1;

					if (xStart < anInt987) {
						xStart = anInt987;
					}
					if (xEnd >= anInt15) {
						xEnd = anInt15 - 1;
					}

					for (@Pc(155) int x = xStart; x <= xEnd; x++) {
						@Pc(160) Tile localTile = null;
						if (lightLevel >= 0) {
							localTile = tiles[lightLevel][x][z];
						}
						if (lightLevel < 0 || localTile != null && localTile.aBoolean45) {
							GlRenderer.configureFixedDepthAdjustment(201.5F - (float) light.level * 50.0F - 1.5F);
							glTexEnvfv(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_COLOR, new float[]{0.0F, 0.0F, 0.0F, light.alpha});
							light.aClass45_1.method1556();
							continue label71;
						}
					}
				}
			}
		}
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_TEXTURE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
		glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
		glDepthMask(true);
		glFogfv(GL2.GL_FOG_COLOR, FogManager.fogColor);
		glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
		GlRenderer.restoreLighting();
	}

	@OriginalMember(owner = "client!jf", name = "a", descriptor = "(ILclient!gi;III)V")
	private static void enableLight(@OriginalArg(0) int lightIndex, @OriginalArg(1) Light light, @OriginalArg(2) int cameraX, @OriginalArg(3) int cameraY, @OriginalArg(4) int cameraZ) {
		@Pc(5) int glLight = lightIndex + GL_LIGHT4;
		
		if (!enabledLights[lightIndex]) {
			glEnable(glLight);
			enabledLights[lightIndex] = true;
		}
		glLightf(glLight, GL2.GL_QUADRATIC_ATTENUATION, light.attenuation);
		glLightfv(glLight, GL2.GL_DIFFUSE, light.diffuse);
		lightPosition[0] = light.x - cameraX;
		lightPosition[1] = light.y - cameraY;
		lightPosition[2] = light.z - cameraZ;
		glLightfv(glLight, GL2.GL_POSITION, lightPosition);
	}

	@OriginalMember(owner = "client!jf", name = "g", descriptor = "()V")
	public static void clearLightingGraph() {
		lightCount = 0;
		for (@Pc(3) int plane = 0; plane < planeMax; plane++) {
			for (@Pc(8) int x = 0; x < xMax; x++) {
				for (@Pc(13) int z = 0; z < zMax; z++) {
					lightingGraph[plane][x][z] = 0;
				}
			}
		}
	}

}
