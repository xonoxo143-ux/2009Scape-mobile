package rt4;

import com.jogamp.opengl.GL2;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL13.glMultiTexCoord2f;

@OriginalClass("client!mb")
public final class GlFont extends Font {

	@OriginalMember(owner = "client!dj", name = "a", descriptor = "Lclient!cf;")
	public static GlSprite masked = null;
	@OriginalMember(owner = "client!mb", name = "Gb", descriptor = "[I")
	private int[] listIds;

	@OriginalMember(owner = "client!mb", name = "Hb", descriptor = "I")
	private int contextId;

	@OriginalMember(owner = "client!mb", name = "Ib", descriptor = "I")
	private int powerOfTwoSize;

	@OriginalMember(owner = "client!mb", name = "Fb", descriptor = "I")
	private int textureId = -1;

	@OriginalMember(owner = "client!mb", name = "Eb", descriptor = "I")
	private int size = 0;

	@OriginalMember(owner = "client!mb", name = "<init>", descriptor = "([B[I[I[I[I[[B)V")
	public GlFont(@OriginalArg(0) byte[] bytes, @OriginalArg(1) int[] xOffsets, @OriginalArg(2) int[] yOffsets, @OriginalArg(3) int[] innerWidths, @OriginalArg(4) int[] innerHeights, @OriginalArg(5) byte[][] pixels) {
		super(bytes, xOffsets, yOffsets, innerWidths, innerHeights);
		this.createTexture(pixels);
		this.createLists();
	}

	@OriginalMember(owner = "client!dj", name = "a", descriptor = "()V")
	public static void method1173() {
		masked = null;
	}

	@OriginalMember(owner = "client!dj", name = "a", descriptor = "(Lclient!cf;)V")
	public static void method1188(@OriginalArg(0) GlSprite sprite) {
		if (sprite.height != GlRaster.clipBottom - GlRaster.clipTop) {
			throw new IllegalArgumentException();
		}
		masked = sprite;
	}

	@OriginalMember(owner = "client!mb", name = "finalize", descriptor = "()V")
	@Override
	protected void finalize() throws Throwable {
		if (this.textureId != -1) {
			GlCleaner.deleteTexture2d(this.textureId, this.size, this.contextId);
			this.textureId = -1;
			this.size = 0;
		}
		if (this.listIds != null) {
			for (@Pc(21) int i = 0; i < this.listIds.length; i++) {
				GlCleaner.deleteList(this.listIds[i], this.contextId);
			}
			this.listIds = null;
		}
		super.finalize();
	}

	@OriginalMember(owner = "client!mb", name = "a", descriptor = "(IIIIIIZ)V")
	@Override
	protected final void renderGlyph(@OriginalArg(0) int glyph, @OriginalArg(1) int x, @OriginalArg(2) int y, @OriginalArg(3) int width, @OriginalArg(4) int height, @OriginalArg(5) int color) {
		@Pc(4) GL2 gl;
		if (masked == null) {
			GlRenderer.setupRgbAlphaMode0Rendering();
			GlRenderer.setTextureId(this.textureId);
			glColor3ub((byte) (color >> 16), (byte) (color >> 8), (byte) color);
			glTranslatef((float) x, (float) (GlRenderer.canvasHeight - y), 0.0F);
			glCallList(this.listIds[glyph]);
			glLoadIdentity();
			return;
		}
		GlRenderer.setupRgbAlphaMode0Rendering();
		glColor3ub((byte) (color >> 16), (byte) (color >> 8), (byte) color);
		glTranslatef((float) x, (float) (GlRenderer.canvasHeight - y), 0.0F);
		@Pc(32) float s0 = (float) (glyph % 16) / 16.0F;
		@Pc(39) float t0 = (float) (glyph / 16) / 16.0F;
		@Pc(51) float s1 = s0 + (float) this.spriteInnerWidths[glyph] / (float) this.powerOfTwoSize;
		@Pc(63) float t1 = t0 + (float) this.spriteInnerHeights[glyph] / (float) this.powerOfTwoSize;
		GlRenderer.setTextureId(this.textureId);
		@Pc(68) GlSprite mask = masked;
		glActiveTexture(GL2.GL_TEXTURE1);
		glEnable(GL2.GL_TEXTURE_2D);
		glBindTexture(GL2.GL_TEXTURE_2D, mask.textureId);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_REPLACE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_PREVIOUS);
		@Pc(98) float maskX0 = (float) (x - GlRaster.clipLeft) / (float) mask.powerOfTwoWidth;
		@Pc(107) float maskY0 = (float) (y - GlRaster.clipTop) / (float) mask.powerOfTwoHeight;
		@Pc(118) float maskX1 = (float) (x + width - GlRaster.clipLeft) / (float) mask.powerOfTwoWidth;
		@Pc(129) float maskY1 = (float) (y + height - GlRaster.clipTop) / (float) mask.powerOfTwoHeight;
		glBegin(GL2.GL_TRIANGLE_FAN);
		glMultiTexCoord2f(GL2.GL_TEXTURE1, maskX1, maskY0);
		glTexCoord2f(s1, t0);
		glVertex2f((float) this.spriteInnerWidths[glyph], 0.0F);
		glMultiTexCoord2f(GL2.GL_TEXTURE1, maskX0, maskY0);
		glTexCoord2f(s0, t0);
		glVertex2f(0.0F, 0.0F);
		glMultiTexCoord2f(GL2.GL_TEXTURE1, maskX0, maskY1);
		glTexCoord2f(s0, t1);
		glVertex2f(0.0F, (float) -this.spriteInnerHeights[glyph]);
		glMultiTexCoord2f(GL2.GL_TEXTURE1, maskX1, maskY1);
		glTexCoord2f(s1, t1);
		glVertex2f((float) this.spriteInnerWidths[glyph], (float) -this.spriteInnerHeights[glyph]);
		glEnd();
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_COMBINE_RGB, GL2.GL_MODULATE);
		glTexEnvi(GL2.GL_TEXTURE_ENV, GL2.GL_SRC0_RGB, GL2.GL_TEXTURE);
		glDisable(GL2.GL_TEXTURE_2D);
		glActiveTexture(GL2.GL_TEXTURE0);
		glLoadIdentity();
	}

	@OriginalMember(owner = "client!mb", name = "a", descriptor = "(IIIIIIIZ)V")
	@Override
	protected final void renderGlyphTransparent(@OriginalArg(0) int glyph, @OriginalArg(1) int x, @OriginalArg(2) int y, @OriginalArg(3) int width, @OriginalArg(4) int height, @OriginalArg(5) int color, @OriginalArg(6) int alpha) {
		GlRenderer.setupRgbAlphaMode0Rendering();
		
		GlRenderer.setTextureId(this.textureId);
		glColor4ub((byte) (color >> 16), (byte) (color >> 8), (byte) color, alpha > 255 ? -1 : (byte) alpha);
		glTranslatef((float) x, (float) (GlRenderer.canvasHeight - y), 0.0F);
		glCallList(this.listIds[glyph]);
		glLoadIdentity();
	}

	@OriginalMember(owner = "client!mb", name = "b", descriptor = "()V")
	private void createLists() {
		if (this.listIds != null) {
			return;
		}
		this.listIds = new int[256];
		
		for (@Pc(11) int i = 0; i < 256; i++) {
			@Pc(21) float s0 = (float) (i % 16) / 16.0F;
			@Pc(28) float t0 = (float) (i / 16) / 16.0F;
			@Pc(40) float s1 = s0 + (float) this.spriteInnerWidths[i] / (float) this.powerOfTwoSize;
			@Pc(52) float t1 = t0 + (float) this.spriteInnerHeights[i] / (float) this.powerOfTwoSize;
			this.listIds[i] = glGenLists(1);
			glNewList(this.listIds[i], GL2.GL_COMPILE);
			glBegin(GL2.GL_TRIANGLE_FAN);
			glTexCoord2f(s1, t0);
			glVertex2f((float) this.spriteInnerWidths[i], 0.0F);
			glTexCoord2f(s0, t0);
			glVertex2f(0.0F, 0.0F);
			glTexCoord2f(s0, t1);
			glVertex2f(0.0F, (float) -this.spriteInnerHeights[i]);
			glTexCoord2f(s1, t1);
			glVertex2f((float) this.spriteInnerWidths[i], (float) -this.spriteInnerHeights[i]);
			glEnd();
			glEndList();
		}
		this.contextId = GlCleaner.contextId;
	}

	private void createTexture(byte[][] pixels) {
		if (this.textureId != -1) {
			return;
		}
		this.powerOfTwoSize = 0;
		for (int i = 0; i < 256; i++) {
			if (this.spriteInnerHeights[i] > this.powerOfTwoSize) {
				this.powerOfTwoSize = this.spriteInnerHeights[i];
			}
			if (this.spriteInnerWidths[i] > this.powerOfTwoSize) {
				this.powerOfTwoSize = this.spriteInnerWidths[i];
			}
		}
		this.powerOfTwoSize *= 16;
		this.powerOfTwoSize = IntUtils.clp2(this.powerOfTwoSize);
		int glyphSize = this.powerOfTwoSize / 16;
		byte[] dest = new byte[this.powerOfTwoSize * this.powerOfTwoSize * 2];
		for (int i = 0; i < 256; i++) {
			int s = i % 16 * glyphSize;
			int t = i / 16 * glyphSize;
			int width = this.spriteInnerWidths[i];
			int height = this.spriteInnerHeights[i];
			byte[] src = pixels[i];

			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int destIndex = ((t + y) * this.powerOfTwoSize + (s + x)) * 2;
					if (src[y * width + x] == 0) {
						dest[destIndex] = 0;
						dest[destIndex + 1] = 0;
					} else {
						dest[destIndex] = -1;
						dest[destIndex + 1] = -1;
					}
				}
			}
		}

		ByteBuffer tempBuffer = ByteBuffer.wrap(dest);
		ByteBuffer buffer = BufferUtils.createByteBuffer(tempBuffer.capacity());
		buffer.put(tempBuffer);
		buffer.flip();

		if (this.textureId == -1) {
			this.textureId = GL11.glGenTextures();
			this.contextId = GlCleaner.contextId;
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.textureId);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_LUMINANCE_ALPHA, this.powerOfTwoSize, this.powerOfTwoSize, 0, GL11.GL_LUMINANCE_ALPHA, GL11.GL_UNSIGNED_BYTE, buffer);
		GlCleaner.onCard2d += buffer.limit() - this.size;
		this.size = buffer.limit();
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
	}
}
