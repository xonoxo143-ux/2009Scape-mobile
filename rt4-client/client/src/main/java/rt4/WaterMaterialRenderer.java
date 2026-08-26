package rt4;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL2GL3;
import org.lwjgl.opengl.GL11;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.glActiveTexture;

@OriginalClass("client!pd")
public final class WaterMaterialRenderer implements MaterialRenderer {

	@OriginalMember(owner = "client!v", name = "c", descriptor = "[F")
	public static final float[] aFloatArray2 = new float[]{0.073F, 0.169F, 0.24F, 1.0F};
	@OriginalMember(owner = "client!pd", name = "b", descriptor = "[F")
	public static final float[] aFloatArray22 = new float[]{0.1F, 0.1F, 0.15F, 0.1F};
	@OriginalMember(owner = "client!pd", name = "a", descriptor = "I")
	private int anInt4440 = -1;

	@OriginalMember(owner = "client!pd", name = "c", descriptor = "[F")
	private final float[] aFloatArray23 = new float[4];

	@OriginalMember(owner = "client!pd", name = "d", descriptor = "I")
	private int anInt4441 = -1;

	@OriginalMember(owner = "client!pd", name = "e", descriptor = "I")
	private int anInt4442 = -1;

	@OriginalMember(owner = "client!pd", name = "<init>", descriptor = "()V")
	public WaterMaterialRenderer() {
		this.method3435();
		this.method3437();
	}

	@OriginalMember(owner = "client!jj", name = "a", descriptor = "(B)[F")
	public static float[] method2422() {
		@Pc(3) float local3 = FogManager.getLightingModelAmbient() + FogManager.getLight0Diffuse();
		@Pc(9) int local9 = FogManager.getLightColor();
		@Pc(18) float local18 = (float) (local9 >> 16 & 0xFF) / 255.0F;
		ColorUtils.aFloatArray28[3] = 1.0F;
		@Pc(37) float local37 = (float) (local9 >> 8 & 0xFF) / 255.0F;
		@Pc(39) float local39 = 0.58823526F;
		@Pc(46) float local46 = (float) (local9 & 0xFF) / 255.0F;
		ColorUtils.aFloatArray28[2] = aFloatArray2[2] * local46 * local39 * local3;
		ColorUtils.aFloatArray28[0] = aFloatArray2[0] * local18 * local39 * local3;
		ColorUtils.aFloatArray28[1] = local3 * local39 * local37 * aFloatArray2[1];
		return ColorUtils.aFloatArray28;
	}

	@OriginalMember(owner = "client!bk", name = "a", descriptor = "(BI)V")
	public static void method619(@OriginalArg(1) int color) {
		aFloatArray2[0] = (float) (color >> 16 & 0xFF) / 255.0F;
		aFloatArray2[1] = (float) (color >> 8 & 0xFF) / 255.0F;
		aFloatArray2[2] = (float) (color & 0xFF) / 255.0F;
		MaterialManager.resetArgument(3);
		MaterialManager.resetArgument(4);
	}

	@OriginalMember(owner = "client!pd", name = "d", descriptor = "()V")
	private void method3435() {
		byte[] local2 = new byte[]{0, -1};
		int textureId = glGenTextures();
		glBindTexture(GL_TEXTURE_1D, textureId);
		glTexImage1D(GL_TEXTURE_1D, 0, GL11.GL_ALPHA, 2, 0, GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, ByteBuffer.wrap(local2));
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_1D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
		this.anInt4441 = textureId;
	}

	@OriginalMember(owner = "client!pd", name = "f", descriptor = "()V")
	private void method3437() {
		this.anInt4440 = glGenLists(2);
		glNewList(this.anInt4440, GL2.GL_COMPILE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC1_RGB, GL2.GL_CONSTANT);
		glTexEnvf(GL2.GL_TEXTURE_ENV, GL2.GL_RGB_SCALE, 2.0F);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2GL3.GL_SRC1_ALPHA, GL2.GL_CONSTANT);
		glTexGeni(GL2.GL_S, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
		glTexGeni(GL2.GL_T, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
		glTexGenfv(GL2.GL_S, GL2.GL_OBJECT_PLANE, new float[]{9.765625E-4F, 0.0F, 0.0F, 0.0F});
		glTexGenfv(GL2.GL_T, GL2.GL_OBJECT_PLANE, new float[]{0.0F, 0.0F, 9.765625E-4F, 0.0F});
		glEnable(GL2.GL_TEXTURE_GEN_S);
		glEnable(GL2.GL_TEXTURE_GEN_T);
		if (MaterialManager.allows3DTextureMapping) {
			glBindTexture(GL2.GL_TEXTURE_3D, MaterialManager.texture3D);
			glTexGeni(GL2.GL_R, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
			glTexGeni(GL2.GL_Q, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_OBJECT_LINEAR);
			glTexGenfv(GL2.GL_Q, GL2.GL_OBJECT_PLANE, new float[]{0.0F, 0.0F, 0.0F, 1.0F});
			glEnable(GL2.GL_TEXTURE_GEN_R);
			glEnable(GL2.GL_TEXTURE_GEN_Q);
			glEnable(GL2.GL_TEXTURE_3D);
		}
		glActiveTexture(GL2.GL_TEXTURE1);
		glEnable(GL2.GL_TEXTURE_1D);
		glBindTexture(GL2.GL_TEXTURE_1D, this.anInt4441);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_INTERPOLATE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_CONSTANT);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC2_RGB, GL2.GL_TEXTURE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_INTERPOLATE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_CONSTANT);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC2_ALPHA, GL2.GL_TEXTURE);
		glEnable(GL2.GL_TEXTURE_GEN_S);
		glTexGeni(GL2.GL_S, GL2.GL_TEXTURE_GEN_MODE, GL2.GL_EYE_LINEAR);
		glPushMatrix();
		glLoadIdentity();
		glEndList();
		glNewList(this.anInt4440 + 1, GL2.GL_COMPILE);
		glActiveTexture(GL2.GL_TEXTURE1);
		glDisable(GL2.GL_TEXTURE_1D);
		glDisable(GL2.GL_TEXTURE_GEN_S);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_TEXTURE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC2_RGB, GL2.GL_CONSTANT);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC2_ALPHA, GL2.GL_CONSTANT);
		glActiveTexture(GL2.GL_TEXTURE0);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_OPERAND0_RGB, GL2.GL_SRC_COLOR);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC1_RGB, GL2.GL_PREVIOUS);
		glTexEnvf(GL2.GL_TEXTURE_ENV, GL2.GL_RGB_SCALE, 1.0F);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2GL3.GL_SRC1_ALPHA, GL2.GL_PREVIOUS);
		glDisable(GL2.GL_TEXTURE_GEN_S);
		glDisable(GL2.GL_TEXTURE_GEN_T);
		if (MaterialManager.allows3DTextureMapping) {
			glDisable(GL2.GL_TEXTURE_GEN_R);
			glDisable(GL2.GL_TEXTURE_GEN_Q);
			glDisable(GL2.GL_TEXTURE_3D);
		}
		glEndList();
	}

	@OriginalMember(owner = "client!pd", name = "a", descriptor = "()V")
	@Override
	public final void unbind() {
		glCallList(this.anInt4440 + 1);
	}

	@OriginalMember(owner = "client!pd", name = "a", descriptor = "(I)V")
	@Override
	public final void setArgument(@OriginalArg(0) int arg0) {
		
		glActiveTexture(GL2.GL_TEXTURE1);
		glTexEnvfv(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_COLOR, aFloatArray2);
		glActiveTexture(GL2.GL_TEXTURE0);
		if ((arg0 & 0x1) == 1) {
			if (!MaterialManager.allows3DTextureMapping) {
				GlRenderer.setTextureId(MaterialManager.anIntArray341[GlRenderer.anInt5323 * 64 / 100 % 64]);
			} else if (this.anInt4442 != GlRenderer.anInt5323) {
				this.aFloatArray23[0] = 0.0F;
				this.aFloatArray23[1] = 0.0F;
				this.aFloatArray23[2] = 0.0F;
				this.aFloatArray23[3] = (float) GlRenderer.anInt5323 * 0.005F;
				glTexGenfv(GL2.GL_R, GL2.GL_OBJECT_PLANE, this.aFloatArray23);
				this.anInt4442 = GlRenderer.anInt5323;
			}
		} else if (MaterialManager.allows3DTextureMapping) {
			this.aFloatArray23[0] = 0.0F;
			this.aFloatArray23[1] = 0.0F;
			this.aFloatArray23[2] = 0.0F;
			this.aFloatArray23[3] = 0.0F;
			glTexGenfv(GL2.GL_R, GL2.GL_OBJECT_PLANE, this.aFloatArray23);
		} else {
			GlRenderer.setTextureId(MaterialManager.anIntArray341[0]);
		}
	}

	@OriginalMember(owner = "client!pd", name = "b", descriptor = "()V")
	@Override
	public final void bind() {
		
		GlRenderer.setTextureCombineRgbMode(2);
		GlRenderer.setTextureCombineAlphaMode(2);
		GlRenderer.resetTextureMatrix();
		glCallList(this.anInt4440);
		@Pc(12) float local12 = 2662.4001F;
		local12 += (float) (MaterialManager.anInt5559 - 128) * 0.5F;
		float max = (float) GlobalConfig.VIEW_DISTANCE - GlobalConfig.VIEW_FADE_DISTANCE;
		if (local12 >= max) {
			local12 = max - 1.0f;
		}
		this.aFloatArray23[0] = 0.0F;
		this.aFloatArray23[1] = 0.0F;
		this.aFloatArray23[2] = 1.0F / (local12 - max);
		this.aFloatArray23[3] = local12 / (local12 - max);
		glTexGenfv(GL2.GL_S, GL2.GL_EYE_PLANE, this.aFloatArray23);
		glPopMatrix();
		glActiveTexture(GL2.GL_TEXTURE0);
		glTexEnvfv(GL2.GL_TEXTURE_ENV, GL2.GL_TEXTURE_ENV_COLOR, aFloatArray22);
	}

	@OriginalMember(owner = "client!pd", name = "c", descriptor = "()I")
	@Override
	public final int getFlags() {
		return 15;
	}
}
