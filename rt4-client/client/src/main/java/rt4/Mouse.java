package rt4;

import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;
import plugin.api.API;

import javax.swing.*;
import java.awt.Component;
import java.awt.event.*;

@OriginalClass("client!ug")
public final class Mouse implements MouseListener, MouseMotionListener, FocusListener {

	@OriginalMember(owner = "client!ah", name = "s", descriptor = "I")
	public static int clickX = 0;
	@OriginalMember(owner = "client!em", name = "y", descriptor = "I")
	public static int clickY = 0;
	@OriginalMember(owner = "client!sc", name = "v", descriptor = "I")
	public static int lastMouseY = 0;
	@OriginalMember(owner = "client!rh", name = "o", descriptor = "I")
	public static int lastMouseX = 0;
	@OriginalMember(owner = "client!he", name = "bb", descriptor = "Lclient!ug;")
	public static Mouse instance = new Mouse();
	@OriginalMember(owner = "client!he", name = "Y", descriptor = "I")
	public static volatile int idleLoops = 0;
	@OriginalMember(owner = "client!lk", name = "Z", descriptor = "I")
	public static int lastButton = 0;
	@OriginalMember(owner = "client!bl", name = "Q", descriptor = "I")
	public static int lastAction = 0;
	@OriginalMember(owner = "client!ra", name = "jb", descriptor = "J")
	public static volatile long eventTime = 0L;
	@OriginalMember(owner = "client!ck", name = "k", descriptor = "I")
	public static volatile int eventMouseDownX = 0;
	@OriginalMember(owner = "client!eg", name = "w", descriptor = "I")
	public static volatile int eventAction = 0;
	@OriginalMember(owner = "client!kf", name = "c", descriptor = "J")
	public static long clickTime = 0L;
	@OriginalMember(owner = "client!dc", name = "W", descriptor = "I")
	public static volatile int eventButton = 0;
	@OriginalMember(owner = "client!nb", name = "j", descriptor = "I")
	public static volatile int eventMouseY = -1;
	@OriginalMember(owner = "client!lh", name = "u", descriptor = "I")
	public static volatile int eventMouseX = -1;
	@OriginalMember(owner = "client!sa", name = "Y", descriptor = "I")
	public static volatile int eventMouseDownY = 0;
	@OriginalMember(owner = "client!wi", name = "W", descriptor = "I")
	public static int anInt5850 = 0;

	public static boolean isDragClick = false;
	public static boolean isCameraToggle = false;
	public static boolean isRightClick = false;

	public static float lastMouseWheelX = 0;
	public static float lastMouseWheelY = 0;


	@OriginalMember(owner = "client!ok", name = "f", descriptor = "J")
	public static long prevClickTime = 0L;
	@OriginalMember(owner = "client!wl", name = "u", descriptor = "I")
	public static int anInt5895 = 0;

	@OriginalMember(owner = "client!sc", name = "a", descriptor = "(ILjava/awt/Component;)V")
	public static void stop(@OriginalArg(1) Component arg0) {
		arg0.removeMouseListener(instance);
		arg0.removeMouseMotionListener(instance);
		arg0.removeFocusListener(instance);
		eventAction = 0;
	}

	@OriginalMember(owner = "client!ug", name = "a", descriptor = "(I)V")
	public static void quit() {
		if (instance != null) {
			@Pc(5) Mouse local5 = instance;
			synchronized (instance) {
				instance = null;
			}
		}
	}

	@OriginalMember(owner = "client!ii", name = "b", descriptor = "(I)V")
	public static void loop() {
		@Pc(2) Mouse local2 = instance;
		synchronized (instance) {
			clickX = eventMouseDownX;
			clickY = eventMouseDownY;
			lastAction = eventAction;

			if(isDragClick) {
				lastAction = 1;
				lastButton = 1;
			} else {
				lastButton = eventButton;
			}

			lastMouseX = eventMouseX;
			lastMouseY = eventMouseY;
			idleLoops++;
			clickTime = eventTime;
			eventButton = 0;
		}
	}

	@OriginalMember(owner = "client!h", name = "a", descriptor = "(Ljava/awt/Component;Z)V")
	public static void start(@OriginalArg(0) Component arg0) {
		arg0.addMouseListener(instance);
		arg0.addMouseMotionListener(instance);
		arg0.addFocusListener(instance);
	}

	@OriginalMember(owner = "client!lc", name = "a", descriptor = "(B)I")
	public static int getIdleLoops() {
		return idleLoops;
	}

	@OriginalMember(owner = "client!dl", name = "a", descriptor = "(II)V")
	public static void setIdleLoops(@OriginalArg(1) int arg0) {
		@Pc(10) Mouse local10 = instance;
		synchronized (instance) {
			idleLoops = arg0;
		}
	}

	@OriginalMember(owner = "client!ug", name = "mouseMoved", descriptor = "(Ljava/awt/event/MouseEvent;)V")
	@Override
	public final synchronized void mouseMoved(@OriginalArg(0) MouseEvent arg0) {
		if (instance != null) {
			if (isCameraToggle) {
				float x = arg0.getX();
				float y = arg0.getY();
				float accelX = lastMouseWheelX - x;
				float accelY = lastMouseWheelY - y;
				lastMouseWheelX = x;
				lastMouseWheelY = y;
				API.UpdateCameraYaw(accelX * 2.0);
				API.UpdateCameraPitch(-accelY * 2.0);
			}

			idleLoops = 0;
			eventMouseX = arg0.getX();
			eventMouseY = arg0.getY();
		}
	}

	@OriginalMember(owner = "client!ug", name = "focusLost", descriptor = "(Ljava/awt/event/FocusEvent;)V")
	@Override
	public final synchronized void focusLost(@OriginalArg(0) FocusEvent arg0) {
		if (instance != null) {
			eventAction = 0;
		}
	}

	public final synchronized void triggerMouseClick(int x, int y, int button, int action) {
		if (instance != null) {
			idleLoops = 0;
			if(action == 0) {
				// Button Released
				//lastButton = 0;
				eventAction = 0;
				return;
			}
			eventMouseX = lastMouseX;
			eventMouseY = lastMouseY;
			eventMouseDownX = lastMouseX;
			eventMouseDownY = lastMouseY;
			eventTime = System.currentTimeMillis();
			if (button == MouseEvent.BUTTON1) {
				eventButton = 2;
				eventAction = 2;
			} else {
				eventButton = 1;
				eventAction = 1;
			}
			//lastButton = button;
		}
	}

	@OriginalMember(owner = "client!ug", name = "mouseDragged", descriptor = "(Ljava/awt/event/MouseEvent;)V")
	@Override
	public final synchronized void mouseDragged(@OriginalArg(0) MouseEvent event) {
		int x = event.getX();
		int y = event.getY();

		if (SwingUtilities.isMiddleMouseButton(event)) return;

		if (instance != null) {
			idleLoops = 0;
			eventMouseX = x;
			eventMouseY = y;
		}
	}

	@OriginalMember(owner = "client!ug", name = "mouseReleased", descriptor = "(Ljava/awt/event/MouseEvent;)V")
	@Override
	public final synchronized void mouseReleased(@OriginalArg(0) MouseEvent arg0) {
		if (instance != null) {
			idleLoops = 0;
			eventAction = 0;
			@Pc(14) int local14 = arg0.getModifiers();
			if ((local14 & 0x10) == 0) {
			}
			if ((local14 & 0x4) == 0) {
			}
			if ((local14 & 0x8) == 0) {
			}
		}
		if (arg0.isPopupTrigger()) {
			arg0.consume();
		}
	}

	@OriginalMember(owner = "client!ug", name = "mouseClicked", descriptor = "(Ljava/awt/event/MouseEvent;)V")
	@Override
	public final void mouseClicked(@OriginalArg(0) MouseEvent arg0) {
		if (arg0.isPopupTrigger()) {
			arg0.consume();
		}
	}

	@OriginalMember(owner = "client!ug", name = "focusGained", descriptor = "(Ljava/awt/event/FocusEvent;)V")
	@Override
	public final void focusGained(@OriginalArg(0) FocusEvent arg0) {
	}

	@OriginalMember(owner = "client!ug", name = "mousePressed", descriptor = "(Ljava/awt/event/MouseEvent;)V")
	@Override
	public final synchronized void mousePressed(@OriginalArg(0) MouseEvent event) {
		if (SwingUtilities.isMiddleMouseButton(event)) {
			return;
		}

		if (instance != null) {
			idleLoops = 0;
			eventMouseDownX = event.getX();
			eventMouseDownY = event.getY();
			eventTime = MonotonicClock.currentTimeMillis();
			if ((event.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK) == 0) {
				eventButton = 1;
				eventAction = 1;
			} else {
				eventButton = 2;
				eventAction = 2;
			}
			@Pc(29) int local29 = event.getModifiers();
			if ((local29 & 0x10) == 0) {
			}
			if ((local29 & 0x4) != 0) {
			}
			if ((local29 & 0x8) != 0) {
			}
		}
		if (event.isPopupTrigger()) {
			event.consume();
		}
	}

	@OriginalMember(owner = "client!ug", name = "mouseExited", descriptor = "(Ljava/awt/event/MouseEvent;)V")
	@Override
	public final synchronized void mouseExited(@OriginalArg(0) MouseEvent arg0) {
		if (instance != null) {
			idleLoops = 0;
			eventMouseX = -1;
			eventMouseY = -1;
		}
	}

	@OriginalMember(owner = "client!ug", name = "mouseEntered", descriptor = "(Ljava/awt/event/MouseEvent;)V")
	@Override
	public final synchronized void mouseEntered(@OriginalArg(0) MouseEvent arg0) {
		if (instance != null) {
			idleLoops = 0;
			eventMouseX = arg0.getX();
			eventMouseY = arg0.getY();
		}
	}
}
