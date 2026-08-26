package rt4;

import com.jogamp.opengl.GL2;
import org.lwjgl.opengl.GL20;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL15.*;

@OriginalClass("client!vi")
public final class GlVertexBufferObject {

	@OriginalMember(owner = "client!vi", name = "a", descriptor = "I")
	private int id;

	@OriginalMember(owner = "client!vi", name = "b", descriptor = "I")
	private final int contextId;

	@OriginalMember(owner = "client!vi", name = "c", descriptor = "I")
	private int size;

	@OriginalMember(owner = "client!vi", name = "d", descriptor = "Z")
	private final boolean stream;

	@OriginalMember(owner = "client!vi", name = "<init>", descriptor = "()V")
	public GlVertexBufferObject() {
		this(false);
	}

	@OriginalMember(owner = "client!vi", name = "<init>", descriptor = "(Z)V")
	public GlVertexBufferObject(boolean stream) {
		this.id = -1;
		this.size = 0;
		this.id = glGenBuffers();
		this.stream = stream;
		this.contextId = GlCleaner.contextId;
	}


	public final void updateArrayBuffer(ByteBuffer buffer) {
		if (buffer.limit() <= this.size) {
			glBindBuffer(GL_ARRAY_BUFFER, this.id);
			glBufferSubData(GL_ARRAY_BUFFER, 0, buffer);
		} else {
			this.setArrayBuffer(buffer);
		}
	}
	@OriginalMember(owner = "client!vi", name = "finalize", descriptor = "()V")
	@Override
	public final void finalize() throws Throwable {
		if (this.id != -1) {
			GlCleaner.deleteBuffer(this.id, this.size, this.contextId);
			this.id = -1;
			this.size = 0;
		}
		super.finalize();
	}

	@OriginalMember(owner = "client!vi", name = "a", descriptor = "()V")
	public final void bindArray() {
		glBindBuffer(GL2.GL_ARRAY_BUFFER, this.id);
	}

	public final void setElementArrayBuffer(ByteBuffer buffer) {
		glBindBuffer(GL20.GL_ELEMENT_ARRAY_BUFFER, this.id);
		glBufferData(GL20.GL_ELEMENT_ARRAY_BUFFER, buffer, this.stream ? GL20.GL_STREAM_DRAW : GL20.GL_STATIC_DRAW);
		GlCleaner.onCardGeometry += buffer.limit() - this.size;
		this.size = buffer.limit();
	}

	@OriginalMember(owner = "client!vi", name = "b", descriptor = "()V")
	public final void bindElementArray() {
		glBindBuffer(GL2.GL_ELEMENT_ARRAY_BUFFER, this.id);
	}

	public final void setArrayBuffer(ByteBuffer buffer) {
		glBindBuffer(GL20.GL_ARRAY_BUFFER, this.id);
		glBufferData(GL20.GL_ARRAY_BUFFER, buffer, this.stream ? GL20.GL_STREAM_DRAW : GL20.GL_STATIC_DRAW);
		GlCleaner.onCardGeometry += buffer.limit() - this.size;
		this.size = buffer.limit();
	}
}
