package examples.multitouch;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.win32.W32APIOptions;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Swing multi-touch using Windows WM_POINTER messages (Windows 8+).
 */
public final class SwingWmPointerDemo {

	private interface User32Ex extends com.sun.jna.Library {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

        int GET_POINTERID_WPARAM(WinDef.WPARAM wParam);
        boolean GetPointerType(int pointerId, IntByRef pointerType);
        boolean GetPointerTouchInfo(int pointerId, POINTER_TOUCH_INFO.ByReference touchInfo);
	}

	public static class IntByRef extends com.sun.jna.ptr.IntByReference {
		public IntByRef() { super(); }
	}

    public static class POINTER_INFO extends com.sun.jna.Structure {
		public int pointerType;
		public int pointerId;
		public int frameId;
		public int pointerFlags;
		public WinUser.POINT ptPixelLocation;
		public WinUser.POINT ptHimetricLocation;
		public WinUser.POINT ptPixelLocationRaw;
		public WinUser.POINT ptHimetricLocationRaw;
		public int dwTime;
		public int historyCount;
		public int inputData;
		public int dwKeyStates;
		public long PerformanceCount;
        public BaseTSD.ULONG_PTR ButtonChangeType;

		@Override protected java.util.List<String> getFieldOrder() {
			return java.util.Arrays.asList(
				"pointerType","pointerId","frameId","pointerFlags",
				"ptPixelLocation","ptHimetricLocation","ptPixelLocationRaw","ptHimetricLocationRaw",
				"dwTime","historyCount","inputData","dwKeyStates","PerformanceCount","ButtonChangeType"
			);
		}
	}

	public static class POINTER_TOUCH_INFO extends com.sun.jna.Structure {
		public POINTER_INFO pointerInfo;
		public int touchFlags;
		public int touchMask;
		public WinUser.RECT rcContact;
		public WinUser.RECT rcContactRaw;
		public int orientation;
		public int pressure;

		public static class ByReference extends POINTER_TOUCH_INFO implements com.sun.jna.Structure.ByReference {}

		@Override protected java.util.List<String> getFieldOrder() {
			return java.util.Arrays.asList(
				"pointerInfo","touchFlags","touchMask","rcContact","rcContactRaw","orientation","pressure"
			);
		}
	}

	private static final int WM_POINTERDOWN = 0x0246;
	private static final int WM_POINTERUPDATE = 0x0245;
	private static final int WM_POINTERUP = 0x0247;

	private static final Map<Integer, Point> activePointers = new HashMap<>();
	private static WinUser.WindowProc windowProcRef;
	private static Pointer originalWndProcPtr;

	static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("Swing WM_POINTER Demo");
			frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			JPanel panel = new JPanel() {
				@Override protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					Graphics2D g2 = (Graphics2D) g;
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(new Color(0x00BFA5));
					for (Map.Entry<Integer, Point> e : activePointers.entrySet()) {
						Point p = e.getValue();
						g2.fillOval(p.x - 20, p.y - 20, 40, 40);
						g2.drawString("pid=" + e.getKey(), p.x + 24, p.y);
					}
				}
			};
			panel.setBackground(Color.WHITE);
			frame.setContentPane(panel);
			frame.setSize(800, 600);
			frame.setLocationRelativeTo(null);

        subclassWindowProc(getHwnd(frame), panel);

			frame.setVisible(true);
		});
	}

	private static void subclassWindowProc(WinDef.HWND hwnd, JComponent repaintTarget) {
		windowProcRef = new WinUser.WindowProc() {
			@Override public WinDef.LRESULT callback(WinDef.HWND hWnd, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
				if (uMsg == WM_POINTERDOWN || uMsg == WM_POINTERUPDATE || uMsg == WM_POINTERUP) {
					int pointerId = lowWord(wParam.intValue());
					POINT pt = getCursorPos();
					if (uMsg == WM_POINTERUP) {
						activePointers.remove(pointerId);
						System.out.println("Pointer id=" + pointerId + " released");
					} else {
						activePointers.put(pointerId, new Point(pt.x, pt.y));
						System.out.println("Pointer id=" + pointerId + " x=" + pt.x + " y=" + pt.y);
					}
					System.out.println("Active pointers: " + activePointers.size());
					repaintTarget.repaint();
					return new WinDef.LRESULT(0);
				}
                return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
			}
		};
        Pointer fn = com.sun.jna.CallbackReference.getFunctionPointer(windowProcRef);
        originalWndProcPtr = User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, fn);
	}

    private static WinDef.HWND getHwnd(Window w) {
        Pointer p = Native.getComponentPointer(w);
        return new WinDef.HWND(p);
    }

	private static int lowWord(int value) { return value & 0xFFFF; }

	private static class POINT { int x; int y; }
	private static POINT getCursorPos() {
		WinUser.POINT lp = new WinUser.POINT();
		User32.INSTANCE.GetCursorPos(lp);
		POINT p = new POINT();
		p.x = lp.x; p.y = lp.y;
		return p;
	}
}


