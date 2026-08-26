package rt4;

import com.jogamp.opengl.GL2;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLCapabilities;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL14.glPointParameterf;
import static org.lwjgl.opengl.GL14.glPointParameterfv;

@OriginalClass("client!ga")
public final class ParticleSystem extends ParticleNode {

	static {
		new SecondaryHashTable(8);
		new Buffer(131056);
	}

	public static void load() {
		GLCapabilities caps = GL.getCapabilities();

		if (caps.GL_ARB_point_parameters) {
			float[] coefficients = new float[]{1.0F, 0.0F, 5.0E-4F};
			glPointParameterfv(GL14.GL_POINT_DISTANCE_ATTENUATION, coefficients);

			FloatBuffer buffer = BufferUtils.createFloatBuffer(1);
			GL11.glGetFloatv(GL2.GL_POINT_SIZE_MAX, buffer);
			float pointSizeMax = buffer.get(0);
			if (pointSizeMax > 1024.0F) {
				pointSizeMax = 1024.0F;
			}

			glPointParameterf(GL14.GL_POINT_SIZE_MIN, 1.0F);
			glPointParameterf(GL14.GL_POINT_SIZE_MAX, pointSizeMax);
		}

		// For the extension GL_ARB_point_sprite, you can add implementation here if it's available.
		if (caps.GL_ARB_point_sprite) {
			// Implementation here
		}
	}


	@OriginalMember(owner = "client!ga", name = "b", descriptor = "()V")
	public static void quit() {
	}

	@OriginalMember(owner = "client!ga", name = "d", descriptor = "()V")
	public final void method1646() {
	}
}
