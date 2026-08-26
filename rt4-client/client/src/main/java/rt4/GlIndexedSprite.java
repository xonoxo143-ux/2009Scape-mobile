package rt4;

import com.jogamp.opengl.GL2;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;

@OriginalClass("client!oh")
public final class GlIndexedSprite extends IndexedSprite {

	@OriginalMember(owner = "client!oh", name = "r", descriptor = "I")
	private int anInt4285;

	@OriginalMember(owner = "client!oh", name = "s", descriptor = "I")
	private int anInt4286;

	@OriginalMember(owner = "client!oh", name = "t", descriptor = "I")
	private int anInt4287;

	@OriginalMember(owner = "client!oh", name = "n", descriptor = "I")
	private int anInt4281 = -1;

	@OriginalMember(owner = "client!oh", name = "p", descriptor = "I")
	private int anInt4283 = 0;

	@OriginalMember(owner = "client!oh", name = "o", descriptor = "I")
	private int anInt4282 = -1;

	@OriginalMember(owner = "client!oh", name = "q", descriptor = "I")
	private int anInt4284 = 0;

	@OriginalMember(owner = "client!oh", name = "<init>", descriptor = "(IIIIII[B[I)V")
	public GlIndexedSprite(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2, @OriginalArg(3) int arg3, @OriginalArg(4) int arg4, @OriginalArg(5) int arg5, @OriginalArg(6) byte[] arg6, @OriginalArg(7) int[] arg7) {
		this.innerWidth = arg0;
		this.innerHeight = arg1;
		this.xOffset = arg2;
		this.yOffset = arg3;
		this.width = arg4;
		this.height = arg5;
		this.method3337(arg6, arg7);
		this.method3339();
	}

	@OriginalMember(owner = "client!oh", name = "a", descriptor = "([B[I)V")
	private void method3337(@OriginalArg(0) byte[] arg0, @OriginalArg(1) int[] arg1) {
		this.anInt4287 = IntUtils.clp2(this.width);
		this.anInt4286 = IntUtils.clp2(this.height);
		@Pc(20) byte[] local20 = new byte[this.anInt4287 * this.anInt4286 * 4];
		@Pc(22) int local22 = 0;
		@Pc(24) int local24 = 0;
		for (@Pc(26) int local26 = 0; local26 < this.height; local26++) {
			for (@Pc(32) int local32 = 0; local32 < this.width; local32++) {
				@Pc(41) byte local41 = arg0[local24++];
				if (local41 == 0) {
					local22 += 4;
				} else {
					@Pc(47) int local47 = arg1[local41];
					local20[local22++] = (byte) (local47 >> 16);
					local20[local22++] = (byte) (local47 >> 8);
					local20[local22++] = (byte) local47;
					local20[local22++] = -1;
				}
			}
			local22 += (this.anInt4287 - this.width) * 4;
		}

		ByteBuffer tempBuffer = ByteBuffer.wrap(local20);
		ByteBuffer local93 = BufferUtils.createByteBuffer(tempBuffer.capacity());
		local93.put(tempBuffer);
		local93.flip();

		if (this.anInt4281 == -1) {
			int[] local102 = new int[1];
			GL11.glGenTextures(local102);
			this.anInt4281 = local102[0];
			this.anInt4285 = GlCleaner.contextId;
		}
		GlRenderer.setTextureId(this.anInt4281);
		glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGBA, this.anInt4287, this.anInt4286, 0, GL2.GL_RGBA, GL2.GL_UNSIGNED_BYTE, local93);
		GlCleaner.onCard2d += local93.limit() - this.anInt4284;
		this.anInt4284 = local93.limit();
	}

	@OriginalMember(owner = "client!oh", name = "a", descriptor = "(III)V")
	@Override
	public final void method3335(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1, @OriginalArg(2) int arg2) {
		GlRenderer.setupRgbAlphaMode0Rendering();
		@Pc(5) int local5 = arg0 + this.xOffset;
		@Pc(10) int local10 = arg1 + this.yOffset;
		GlRenderer.setTextureId(this.anInt4281);
		this.method3338();
		glColor4f(1.0F, 1.0F, 1.0F, (float) arg2 / 256.0F);
		glTranslatef((float) local5, (float) (GlRenderer.canvasHeight - local10), 0.0F);
		glCallList(this.anInt4282);
		glLoadIdentity();
	}

	@OriginalMember(owner = "client!oh", name = "b", descriptor = "(I)V")
	private void method3338() {
		if (this.anInt4283 != 1) {
			this.anInt4283 = 1;
			glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_NEAREST);
			glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_NEAREST);
		}
	}

	@OriginalMember(owner = "client!oh", name = "a", descriptor = "(II)V")
	@Override
	public final void renderTransparent(@OriginalArg(0) int arg0, @OriginalArg(1) int arg1) {
		GlRenderer.setupRgbAlphaMode1Rendering();
		@Pc(5) int local5 = arg0 + this.xOffset;
		@Pc(10) int local10 = arg1 + this.yOffset;
		GlRenderer.setTextureId(this.anInt4281);
		this.method3338();
		glTranslatef((float) local5, (float) (GlRenderer.canvasHeight - local10), 0.0F);
		glCallList(this.anInt4282);
		glLoadIdentity();
	}

	@OriginalMember(owner = "client!oh", name = "finalize", descriptor = "()V")
	@Override
	public final void finalize() throws Throwable {
		if (this.anInt4281 != -1) {
			GlCleaner.deleteTexture2d(this.anInt4281, this.anInt4284, this.anInt4285);
			this.anInt4281 = -1;
			this.anInt4284 = 0;
		}
		if (this.anInt4282 != -1) {
			GlCleaner.deleteList(this.anInt4282, this.anInt4285);
			this.anInt4282 = -1;
		}
		super.finalize();
	}

	@OriginalMember(owner = "client!oh", name = "a", descriptor = "()V")
	private void method3339() {
		@Pc(7) float local7 = (float) this.width / (float) this.anInt4287;
		@Pc(15) float local15 = (float) this.height / (float) this.anInt4286;
		if (this.anInt4282 == -1) {
			this.anInt4282 = glGenLists(1);
			this.anInt4285 = GlCleaner.contextId;
		}
		glNewList(this.anInt4282, GL2.GL_COMPILE);
		glBegin(GL2.GL_TRIANGLE_FAN);
		glTexCoord2f(local7, 0.0F);
		glVertex2f((float) this.width, 0.0F);
		glTexCoord2f(0.0F, 0.0F);
		glVertex2f(0.0F, 0.0F);
		glTexCoord2f(0.0F, local15);
		glVertex2f(0.0F, (float) -this.height);
		glTexCoord2f(local7, local15);
		glVertex2f((float) this.width, (float) -this.height);
		glEnd();
		glEndList();
	}
}
