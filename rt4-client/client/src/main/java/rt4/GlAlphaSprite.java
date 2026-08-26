package rt4;

import com.jogamp.opengl.GL2;
import org.lwjgl.BufferUtils;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.glGenTextures;
import static org.lwjgl.opengl.GL11.glTexImage2D;

@OriginalClass("client!el")
public final class GlAlphaSprite extends GlSprite {

	@OriginalMember(owner = "client!el", name = "<init>", descriptor = "(IIIIII[I)V")
	public GlAlphaSprite(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int arg3, @OriginalArg(4) int arg4, @OriginalArg(5) int arg5, @OriginalArg(6) int[] arg6) {
		super(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
	}

	@OriginalMember(owner = "client!el", name = "<init>", descriptor = "(Lclient!mm;)V")
	public GlAlphaSprite(@OriginalArg(0) SoftwareSprite arg0) {
		super(arg0);
	}

	@Override
	protected final void method1430(int[] arg0) {
		this.powerOfTwoWidth = IntUtils.clp2(this.width);
		this.powerOfTwoHeight = IntUtils.clp2(this.height);
		byte[] local20 = new byte[this.powerOfTwoWidth * this.powerOfTwoHeight * 4];
		int local22 = 0;
		int local24 = 0;
		int local32 = (this.powerOfTwoWidth - this.width) * 4;
		for (int local34 = 0; local34 < this.height; local34++) {
			for (int local40 = 0; local40 < this.width; local40++) {
				int local49 = arg0[local24++];
				if (local49 == 0) {
					local22 += 4;
				} else {
					local20[local22++] = (byte) (local49 >> 16);
					local20[local22++] = (byte) (local49 >> 8);
					local20[local22++] = (byte) local49;
					local20[local22++] = (byte) (local49 >> 24);
				}
			}
			local22 += local32;
		}

		/*
		Magic code to convert not working JOGL buffers into working buffers.
		 */
		ByteBuffer tempBuffer = ByteBuffer.wrap(local20);
		ByteBuffer local94 = BufferUtils.createByteBuffer(tempBuffer.capacity());
		local94.put(tempBuffer);
		local94.flip();

		if (this.textureId == -1) {
			this.textureId = glGenTextures();
		}
		GlRenderer.setTextureId(this.textureId);

		glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, this.powerOfTwoWidth, this.powerOfTwoHeight, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, local94);
		GlCleaner.onCard2d += local94.limit() - this.anInt1869;
		this.anInt1869 = local94.limit();
	}
}
