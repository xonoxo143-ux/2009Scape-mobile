package rt4;

import com.jogamp.opengl.GL2;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.ARBVertexProgram.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.glActiveTexture;

@OriginalClass("client!rd")
public final class LiquidMaterialRenderer implements MaterialRenderer {

	@OriginalMember(owner = "client!rd", name = "d", descriptor = "[F")
	public static final float[] aFloatArray24 = new float[4];
	@OriginalMember(owner = "client!rd", name = "a", descriptor = "I")
	private int anInt4829 = -1;

	@OriginalMember(owner = "client!rd", name = "e", descriptor = "I")
	private int anInt4831 = -1;

	@OriginalMember(owner = "client!rd", name = "c", descriptor = "I")
	private int anInt4830;

	@OriginalMember(owner = "client!rd", name = "b", descriptor = "Ljava/nio/FloatBuffer;")
	private FloatBuffer aFloatBuffer1;

	public LiquidMaterialRenderer() {
		if (this.anInt4831 < 0 && (true)) {
			IntBuffer local19 = BufferUtils.createIntBuffer(1);
			GL15.glGenBuffers(local19);
			this.anInt4830 = local19.get(0);

			int[][] local42 = method874(0.4F);
			int[][] local53 = method874(0.4F);

			ByteBuffer local58 = BufferUtils.createByteBuffer(262144);

			for (int local60 = 0; local60 < 256; local60++) {
				int[] local67 = local42[local60];
				int[] local71 = local53[local60];
				for (int local73 = 0; local73 < 64; local73++) {
					local58.putFloat((float) local67[local73] / 4096.0F);
					local58.putFloat((float) local71[local73] / 4096.0F);
					local58.putFloat(1.0F);
					local58.putFloat(1.0F);
				}
			}

			local58.flip();
			this.aFloatBuffer1 = local58.asFloatBuffer().asReadOnlyBuffer();

			this.method3719();
			this.method3720();
		}
	}

	@OriginalMember(owner = "client!cj", name = "a", descriptor = "(ZIIIIIIFB)[[I")
	public static int[][] method874(@OriginalArg(7) float arg0) {
		@Pc(15) int[][] local15 = new int[256][64];
		@Pc(19) TextureOp34 local19 = new TextureOp34();
		local19.anInt648 = (int) (arg0 * 4096.0F);
		local19.anInt642 = 3;
		local19.anInt641 = 4;
		local19.aBoolean44 = false;
		local19.anInt646 = 8;
		local19.postDecode();
		Texture.setSize(256, 64);
		for (@Pc(46) int local46 = 0; local46 < 256; local46++) {
			local19.method584(local46, local15[local46]);
		}
		return local15;
	}

	@OriginalMember(owner = "client!rd", name = "a", descriptor = "()V")
	@Override
	public final void unbind() {
		if (this.anInt4831 >= 0) {
			glCallList(this.anInt4831 + 1);
		}
	}

	@OriginalMember(owner = "client!rd", name = "c", descriptor = "()I")
	@Override
	public final int getFlags() {
		return 0;
	}

	@OriginalMember(owner = "client!rd", name = "b", descriptor = "()V")
	@Override
	public final void bind() {
		if (this.anInt4831 < 0) {
			return;
		}
		glCallList(this.anInt4831);
		glActiveTexture(GL2.GL_TEXTURE1);
		glMatrixMode(GL2.GL_TEXTURE);
		glTranslatef((float) MaterialManager.anInt406, (float) MaterialManager.anInt4675, (float) MaterialManager.anInt5158);
		glRotatef(-((float) MaterialManager.anInt1815 * 360.0F) / 2048.0F, 0.0F, 1.0F, 0.0F);
		glRotatef(-((float) MaterialManager.anInt5559 * 360.0F) / 2048.0F, 1.0F, 0.0F, 0.0F);
		glRotatef(-180.0F, 1.0F, 0.0F, 0.0F);
		glMatrixMode(GL2.GL_MODELVIEW);
		if (!MaterialManager.allows3DTextureMapping) {
			glBindTexture(GL2.GL_TEXTURE_2D, MaterialManager.anIntArray341[(int) ((float) (GlRenderer.anInt5323 * 64) * 0.005F) % 64]);
		}
		glActiveTexture(GL2.GL_TEXTURE0);
		if (this.anInt4829 == GlRenderer.anInt5323) {
			return;
		}
		@Pc(85) int local85 = (GlRenderer.anInt5323 & 0xFF) * 256;
		for (@Pc(87) int local87 = 0; local87 < 64; local87++) {
			this.aFloatBuffer1.position(local85);
			glProgramLocalParameter4fvARB(GL2.GL_VERTEX_PROGRAM_ARB, local87, this.aFloatBuffer1);
			local85 += 4;
		}
		if (MaterialManager.allows3DTextureMapping) {
			glProgramLocalParameter4fARB(GL2.GL_VERTEX_PROGRAM_ARB, 65, (float) GlRenderer.anInt5323 * 0.005F, 0.0F, 0.0F, 1.0F);
		} else {
			glProgramLocalParameter4fARB(GL2.GL_VERTEX_PROGRAM_ARB, 65, 0.0F, 0.0F, 0.0F, 1.0F);
		}
		this.anInt4829 = GlRenderer.anInt5323;
	}

	@OriginalMember(owner = "client!rd", name = "e", descriptor = "()V")
	private void method3719() {
		this.anInt4831 = -1;
		glNewList(this.anInt4831, GL2.GL_COMPILE);
		glActiveTexture(GL2.GL_TEXTURE1);
		if (MaterialManager.allows3DTextureMapping) {
			glBindTexture(GL2.GL_TEXTURE_3D, MaterialManager.texture3D);
		}
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_ADD);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_REPLACE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_PREVIOUS);
		glActiveTexture(GL2.GL_TEXTURE0);
		glBindProgramARB(GL2.GL_VERTEX_PROGRAM_ARB, this.anInt4830);
		glEnable(GL2.GL_VERTEX_PROGRAM_ARB);
		glEndList();
		glNewList(this.anInt4831 + 1, GL2.GL_COMPILE);
		glActiveTexture(GL2.GL_TEXTURE1);
		glMatrixMode(GL2.GL_TEXTURE);
		glLoadIdentity();
		glMatrixMode(GL2.GL_MODELVIEW);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_ALPHA, GL2.GL_MODULATE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_ALPHA, GL2.GL_TEXTURE);
		glDisable(MaterialManager.allows3DTextureMapping ? GL2.GL_TEXTURE_3D : GL2.GL_TEXTURE_2D);
		glActiveTexture(GL2.GL_TEXTURE0);
		glBindProgramARB(GL2.GL_VERTEX_PROGRAM_ARB, 0);
		glDisable(GL2.GL_VERTEX_PROGRAM_ARB);
		glDisable(GL2.GL_FRAGMENT_PROGRAM_ARB);
		glEndList();
	}

	@OriginalMember(owner = "client!rd", name = "f", descriptor = "()V")
	private void method3720() {
		if (this.anInt4831 < 0) {
			return;
		}
		@Pc(7) int[] local7 = new int[1];
		glBindProgramARB(GL2.GL_VERTEX_PROGRAM_ARB, this.anInt4830);
		// WaterShader Needs to be rewritten
		//glProgramStringARB(GL2.GL_VERTEX_PROGRAM_ARB, GL2.GL_PROGRAM_FORMAT_ASCII_ARB, "!!ARBvp1.0\nATTRIB  iPos         = vertex.position;\nATTRIB  iColour      = vertex.color;\nOUTPUT  oPos         = result.position;\nOUTPUT  oColour      = result.color;\nOUTPUT  oTexCoord0   = result.texcoord[0];\nOUTPUT  oTexCoord1   = result.texcoord[1];\nOUTPUT  oFogCoord    = result.fogcoord;\nPARAM   time         = program.local[65];\nPARAM   turbulence   = program.local[64];\nPARAM   lightAmbient = program.local[66]; \nPARAM   pMatrix[4]   = { state.matrix.projection };\nPARAM   mvMatrix[4]  = { state.matrix.modelview };\nPARAM   ivMatrix[4]  = { state.matrix.texture[1] };\nPARAM   fNoise[64]   = { program.local[0..63] };\nTEMP    noise, clipPos, viewPos, worldPos;\nADDRESS noiseAddr;\nDP4   viewPos.x, mvMatrix[0], iPos;\nDP4   viewPos.y, mvMatrix[1], iPos;\nDP4   viewPos.z, mvMatrix[2], iPos;\nDP4   viewPos.w, mvMatrix[3], iPos;\nDP4   worldPos.x, ivMatrix[0], viewPos;\nDP4   worldPos.y, ivMatrix[1], viewPos;\nDP4   worldPos.z, ivMatrix[2], viewPos;\nDP4   worldPos.w, ivMatrix[3], viewPos;\nADD   noise.x, worldPos.x, worldPos.z;SUB   noise.y, worldPos.z, worldPos.x;MUL   noise, noise, 0.0001220703125;\nFRC   noise, noise;\nMUL   noise, noise, 64;\nARL   noiseAddr.x, noise.x;\nMOV   noise.x, fNoise[noiseAddr.x].x;\nARL   noiseAddr.x, noise.y;\nMOV   noise.y, fNoise[noiseAddr.x].y;\nMUL   noise, noise, turbulence.x;\nMAD   oTexCoord0, worldPos.xzww, 0.0078125, noise;\nMOV   oTexCoord0.w, 1;\nMUL   oTexCoord1.xy, worldPos.xzww, 0.0009765625;\nMOV   oTexCoord1.zw, time.xxxw;\nDP4   clipPos.x, pMatrix[0], viewPos;\nDP4   clipPos.y, pMatrix[1], viewPos;\nDP4   clipPos.z, pMatrix[2], viewPos;\nDP4   clipPos.w, pMatrix[3], viewPos;\nMUL   oColour.xyz, iColour, lightAmbient;\nMOV   oColour.w, 1;\nMOV   oFogCoord.x, clipPos.z;\nMOV   oPos, clipPos; \nEND".length(), "!!ARBvp1.0\nATTRIB  iPos         = vertex.position;\nATTRIB  iColour      = vertex.color;\nOUTPUT  oPos         = result.position;\nOUTPUT  oColour      = result.color;\nOUTPUT  oTexCoord0   = result.texcoord[0];\nOUTPUT  oTexCoord1   = result.texcoord[1];\nOUTPUT  oFogCoord    = result.fogcoord;\nPARAM   time         = program.local[65];\nPARAM   turbulence   = program.local[64];\nPARAM   lightAmbient = program.local[66]; \nPARAM   pMatrix[4]   = { state.matrix.projection };\nPARAM   mvMatrix[4]  = { state.matrix.modelview };\nPARAM   ivMatrix[4]  = { state.matrix.texture[1] };\nPARAM   fNoise[64]   = { program.local[0..63] };\nTEMP    noise, clipPos, viewPos, worldPos;\nADDRESS noiseAddr;\nDP4   viewPos.x, mvMatrix[0], iPos;\nDP4   viewPos.y, mvMatrix[1], iPos;\nDP4   viewPos.z, mvMatrix[2], iPos;\nDP4   viewPos.w, mvMatrix[3], iPos;\nDP4   worldPos.x, ivMatrix[0], viewPos;\nDP4   worldPos.y, ivMatrix[1], viewPos;\nDP4   worldPos.z, ivMatrix[2], viewPos;\nDP4   worldPos.w, ivMatrix[3], viewPos;\nADD   noise.x, worldPos.x, worldPos.z;SUB   noise.y, worldPos.z, worldPos.x;MUL   noise, noise, 0.0001220703125;\nFRC   noise, noise;\nMUL   noise, noise, 64;\nARL   noiseAddr.x, noise.x;\nMOV   noise.x, fNoise[noiseAddr.x].x;\nARL   noiseAddr.x, noise.y;\nMOV   noise.y, fNoise[noiseAddr.x].y;\nMUL   noise, noise, turbulence.x;\nMAD   oTexCoord0, worldPos.xzww, 0.0078125, noise;\nMOV   oTexCoord0.w, 1;\nMUL   oTexCoord1.xy, worldPos.xzww, 0.0009765625;\nMOV   oTexCoord1.zw, time.xxxw;\nDP4   clipPos.x, pMatrix[0], viewPos;\nDP4   clipPos.y, pMatrix[1], viewPos;\nDP4   clipPos.z, pMatrix[2], viewPos;\nDP4   clipPos.w, pMatrix[3], viewPos;\nMUL   oColour.xyz, iColour, lightAmbient;\nMOV   oColour.w, 1;\nMOV   oFogCoord.x, clipPos.z;\nMOV   oPos, clipPos; \nEND");
		glGetIntegerv(GL2.GL_PROGRAM_ERROR_POSITION_ARB, local7);
		if (local7[0] != -1) {
			return;
		}
	}

	@OriginalMember(owner = "client!rd", name = "a", descriptor = "(I)V")
	@Override
	public final void setArgument(@OriginalArg(0) int arg0) {
		if (this.anInt4831 < 0) {
			return;
		}
		glActiveTexture(GL2.GL_TEXTURE1);
		if ((arg0 & 0x80) == 0) {
			glEnable(MaterialManager.allows3DTextureMapping ? GL2.GL_TEXTURE_3D : GL2.GL_TEXTURE_2D);
		} else {
			glDisable(MaterialManager.allows3DTextureMapping ? GL2.GL_TEXTURE_3D : GL2.GL_TEXTURE_2D);
		}
		glActiveTexture(GL2.GL_TEXTURE0);
		if ((arg0 & 0x40) == 0) {
			glGetFloatv(GL2.GL_LIGHT_MODEL_AMBIENT, aFloatArray24);
			glProgramLocalParameter4fvARB(GL2.GL_VERTEX_PROGRAM_ARB, 66, aFloatArray24);
		} else {
			glProgramLocalParameter4fARB(GL2.GL_VERTEX_PROGRAM_ARB, 66, 1.0F, 1.0F, 1.0F, 1.0F);
		}
		@Pc(58) int local58 = arg0 & 0x3;
		if (local58 == 2) {
			glProgramLocalParameter4fARB(GL2.GL_VERTEX_PROGRAM_ARB, 64, 0.05F, 1.0F, 1.0F, 1.0F);
		} else if (local58 == 3) {
			glProgramLocalParameter4fARB(GL2.GL_VERTEX_PROGRAM_ARB, 64, 0.1F, 1.0F, 1.0F, 1.0F);
		} else {
			glProgramLocalParameter4fARB(GL2.GL_VERTEX_PROGRAM_ARB, 64, 0.025F, 1.0F, 1.0F, 1.0F);
		}
	}
}
