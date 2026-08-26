package rt4;

import com.jogamp.opengl.GL2;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL13.glClientActiveTexture;

@OriginalClass("client!wg")
public final class UnderwaterMaterialRenderer implements MaterialRenderer {

	@OriginalMember(owner = "client!wg", name = "b", descriptor = "Z")
	public static boolean aBoolean308 = false;
	@OriginalMember(owner = "client!nh", name = "Z", descriptor = "I")
	public static int anInt3241 = 128;
	@OriginalMember(owner = "client!wg", name = "c", descriptor = "I")
	private int anInt5805 = -1;

	@OriginalMember(owner = "client!wg", name = "a", descriptor = "[F")
	private final float[] aFloatArray29 = new float[4];

	@OriginalMember(owner = "client!wg", name = "d", descriptor = "I")
	private int anInt5806 = -1;

	@OriginalMember(owner = "client!wg", name = "<init>", descriptor = "()V")
	public UnderwaterMaterialRenderer() {
		if (GlRenderer.maxTextureUnits >= 2) {
			byte[] local20 = new byte[8];
			int local22 = 0;
			while (local22 < 8) {
				local20[local22++] = (byte) (local22 * 159 / 8 + 96);
			}

			int textureId = glGenTextures();
			glBindTexture(GL11.GL_TEXTURE_1D, textureId);
			GL11.glTexImage1D(GL11.GL_TEXTURE_1D, 0, GL11.GL_ALPHA, 8, 0, GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, ByteBuffer.wrap(local20));
			GL11.glTexParameteri(GL11.GL_TEXTURE_1D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_1D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_1D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);

			this.anInt5805 = textureId;
			aBoolean308 = GlRenderer.maxTextureUnits > 2 && GlRenderer.extTexture3dSupported;
			this.method4606();
		}
	}


	@OriginalMember(owner = "client!wg", name = "e", descriptor = "()I")
	public static int method4607() {
		return aBoolean308 ? 33986 : 33985;
	}

	@OriginalMember(owner = "client!wg", name = "f", descriptor = "()V")
	public static void method4608() {
		glClientActiveTexture(method4607());
		glDisableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
		glClientActiveTexture(GL2.GL_TEXTURE0);
	}

	@OriginalMember(owner = "client!wg", name = "g", descriptor = "()V")
	public static void method4609() {
		glClientActiveTexture(method4607());
		glEnableClientState(GL2.GL_TEXTURE_COORD_ARRAY);
		glClientActiveTexture(GL2.GL_TEXTURE0);
	}

	@OriginalMember(owner = "client!mf", name = "a", descriptor = "()V")
	public static void applyFogFade() {
		glDisableClientState(GL2.GL_COLOR_ARRAY);
		GlRenderer.setLightingEnabled(false);
		glDisable(GL2.GL_DEPTH_TEST);
		glPushAttrib(GL2.GL_FOG_BIT);
		glFogf(GL2.GL_FOG_START, (float) GlobalConfig.VIEW_DISTANCE - (GlobalConfig.VIEW_FADE_DISTANCE * 2.0f));
		GlRenderer.disableDepthMask();
		try {
			for (@Pc(19) int i = 0; i < SceneGraph.surfaceHdTiles[0].length; i++) {
				@Pc(31) GlTile tile = SceneGraph.surfaceHdTiles[0][i];
				if (tile.texture >= 0 && Rasteriser.textureProvider.getMaterialType(tile.texture) == MaterialManager.WATER) {
					glColor4fv(ColorUtils.getRgbFloat(tile.underwaterColor));
					@Pc(57) float f = 201.5F - (tile.blend ? 1.0F : 0.5F);
					tile.method1944(SceneGraph.tiles, f, true);
				}
			}
		} catch (Exception ignored) {
		}
		glEnableClientState(GL2.GL_COLOR_ARRAY);
		GlRenderer.restoreLighting();
		glEnable(GL2.GL_DEPTH_TEST);
		glPopAttrib();
		GlRenderer.enableDepthMask();
	}

	@OriginalMember(owner = "client!wg", name = "d", descriptor = "()V")
	private void method4606() {
		this.anInt5806 = glGenLists(2);
		glNewList(this.anInt5806, GL2.GL_COMPILE);
		glActiveTexture(GL2.GL_TEXTURE1);
		if (aBoolean308) {
			glBindTexture(GL2.GL_TEXTURE_3D, MaterialManager.texture3D);
			glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_ADD);
			glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
			glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_REPLACE);
			glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_PREVIOUS);
			glTexGeni(GL2.GL_S, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_EYE_LINEAR);
			glTexGeni(GL2.GL_R, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_EYE_LINEAR);
			glTexGeni(GL2.GL_T, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_EYE_LINEAR);
			glTexGeni(GL2.GL_Q, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
			glTexGenfv(GL2.GL_Q, GL2.GL_OBJECT_PLANE, new float[]{0.0F, 0.0F, 0.0F, 1.0F});
			glEnable(GL2.GL_TEXTURE_GEN_S);
			glEnable(GL2.GL_TEXTURE_GEN_T);
			glEnable(GL2.GL_TEXTURE_GEN_R);
			glEnable(GL2.GL_TEXTURE_GEN_Q);
			glEnable(GL2.GL_TEXTURE_3D);
			glActiveTexture(GL2.GL_TEXTURE2);
			glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_COMBINE);
		}
		glBindTexture(GL2.GL_TEXTURE_1D, this.anInt5805);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_INTERPOLATE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_CONSTANT);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC2_RGB, GL2.GL_TEXTURE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_REPLACE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_PREVIOUS);
		glTexGeni(GL2.GL_S, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_EYE_LINEAR);
		glEnable(GL2.GL_TEXTURE_1D);
		glEnable(GL2.GL_TEXTURE_GEN_S);
		glActiveTexture(GL2.GL_TEXTURE0);
		glEndList();
		glNewList(this.anInt5806 + 1, GL2.GL_COMPILE);
		glActiveTexture(GL2.GL_TEXTURE1);
		if (aBoolean308) {
			glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
			glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
			glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
			glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
			glDisable(GL2.GL_TEXTURE_GEN_S);
			glDisable(GL2.GL_TEXTURE_GEN_T);
			glDisable(GL2.GL_TEXTURE_GEN_R);
			glDisable(GL2.GL_TEXTURE_GEN_Q);
			glDisable(GL2.GL_TEXTURE_3D);
			glActiveTexture(GL2.GL_TEXTURE2);
			glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_MODE, GL2.GL_MODULATE);
		}
		glTexEnvfv(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_COLOR, new float[]{0.0F, 1.0F, 0.0F, 1.0F});
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_TEXTURE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC2_RGB, GL2.GL_CONSTANT);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
		glDisable(GL2.GL_TEXTURE_1D);
		glDisable(GL2.GL_TEXTURE_GEN_S);
		glActiveTexture(GL2.GL_TEXTURE0);
		glEndList();
	}

	@OriginalMember(owner = "client!wg", name = "b", descriptor = "()V")
	@Override
	public final void bind() {
		glCallList(this.anInt5806);
	}

	@OriginalMember(owner = "client!wg", name = "c", descriptor = "()I")
	@Override
	public final int getFlags() {
		return 0;
	}

	@OriginalMember(owner = "client!wg", name = "a", descriptor = "()V")
	@Override
	public final void unbind() {
		glCallList(this.anInt5806 + 1);
	}

	@OriginalMember(owner = "client!wg", name = "a", descriptor = "(I)V")
	@Override
	public final void setArgument(@OriginalArg(0) int arg0) {
		glActiveTexture(GL2.GL_TEXTURE1);
		if (aBoolean308 || arg0 >= 0) {
			glPushMatrix();
			glLoadIdentity();
			glRotatef(180.0F, 1.0F, 0.0F, 0.0F);
			glRotatef((float) MaterialManager.anInt5559 * 360.0F / 2048.0F, 1.0F, 0.0F, 0.0F);
			glRotatef((float) MaterialManager.anInt1815 * 360.0F / 2048.0F, 0.0F, 1.0F, 0.0F);
			glTranslatef((float) -MaterialManager.anInt406, (float) -MaterialManager.anInt4675, (float) -MaterialManager.anInt5158);
			if (aBoolean308) {
				this.aFloatArray29[0] = 0.001F;
				this.aFloatArray29[1] = 9.0E-4F;
				this.aFloatArray29[2] = 0.0F;
				this.aFloatArray29[3] = 0.0F;
				glTexGenfv(GL2.GL_S, GL2.GL_EYE_PLANE, this.aFloatArray29);
				this.aFloatArray29[0] = 0.0F;
				this.aFloatArray29[1] = 9.0E-4F;
				this.aFloatArray29[2] = 0.001F;
				this.aFloatArray29[3] = 0.0F;
				glTexGenfv(GL2.GL_T, GL2.GL_EYE_PLANE, this.aFloatArray29);
				this.aFloatArray29[0] = 0.0F;
				this.aFloatArray29[1] = 0.0F;
				this.aFloatArray29[2] = 0.0F;
				this.aFloatArray29[3] = (float) GlRenderer.anInt5323 * 0.005F;
				glTexGenfv(GL2.GL_R, GL2.GL_EYE_PLANE, this.aFloatArray29);
				glActiveTexture(GL2.GL_TEXTURE2);
			}
			glTexEnvfv(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_COLOR, WaterMaterialRenderer.method2422());
			if (arg0 >= 0) {
				this.aFloatArray29[0] = 0.0F;
				this.aFloatArray29[1] = 1.0F / (float) anInt3241;
				this.aFloatArray29[2] = 0.0F;
				this.aFloatArray29[3] = (float) arg0 * 1.0F / (float) anInt3241;
				glTexGenfv(GL2.GL_S, GL2.GL_EYE_PLANE, this.aFloatArray29);
				glEnable(GL2.GL_TEXTURE_GEN_S);
			} else {
				glDisable(GL2.GL_TEXTURE_GEN_S);
			}
			glPopMatrix();
		} else {
			glDisable(GL2.GL_TEXTURE_GEN_S);
		}
		glActiveTexture(GL2.GL_TEXTURE0);
	}
}
