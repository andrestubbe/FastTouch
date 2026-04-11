package examples.multitouch;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.GestureEvent;
import org.eclipse.swt.events.GestureListener;
import org.eclipse.swt.events.TouchEvent;
import org.eclipse.swt.events.TouchListener;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

public final class SwtTouchDemo {
	static void main(String[] args) {
		Display display = new Display();
		Shell shell = new Shell(display);
		shell.setText("SWT Multitouch Demo");
		shell.setSize(800, 600);
		Canvas canvas = new Canvas(shell, SWT.NONE);
		canvas.setBounds(0, 0, 800, 600);

		canvas.addTouchListener(new TouchListener() {
			@Override public void touch(TouchEvent e) {
				for (org.eclipse.swt.widgets.Touch touch : e.touches) {
					System.out.println("touch id=" + touch.id + " x=" + touch.x + " y=" + touch.y);
				}
			}
		});

		canvas.addGestureListener(new GestureListener() {
			@Override public void gesture(GestureEvent e) {
				if ((e.detail & SWT.GESTURE_MAGNIFY) != 0) {
					System.out.println("zoom: " + e.magnification);
				}
				if ((e.detail & SWT.GESTURE_ROTATE) != 0) {
					System.out.println("rotate: " + e.rotation);
				}
				if ((e.detail & SWT.GESTURE_PAN) != 0) {
					System.out.println("pan: " + e.xDirection + "," + e.yDirection);
				}
			}
		});

		shell.open();
		while (!shell.isDisposed()) {
			if (!display.readAndDispatch()) display.sleep();
		}
		display.dispose();
	}
}


