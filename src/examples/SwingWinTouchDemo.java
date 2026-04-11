package examples.multitouch;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.win32.W32APIOptions;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.CallbackReference;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinUser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal Swing demo handling Windows WM_TOUCH multi-touch via JNA.
 * Requires: jna and jna-platform dependencies.
 * Windows sends coordinates in 1/100 px; convert to pixels.
 */
public final class SwingWinTouchDemo {

	private interface User32Ex extends com.sun.jna.Library {
		User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);

		boolean RegisterTouchWindow(WinDef.HWND hWnd, int ulFlags);
		boolean UnregisterTouchWindow(WinDef.HWND hWnd);
		boolean GetTouchInputInfo(WinNT.HANDLE hTouchInput, int cInputs, TOUCHINPUT[] pInputs, int cbSize);
		boolean CloseTouchInputHandle(WinNT.HANDLE hTouchInput);
	}

	public static final class TOUCHINPUT extends com.sun.jna.Structure {
		public int x; // 1/100 px
		public int y; // 1/100 px
		public WinNT.HANDLE hSource;
		public int dwID;
		public int dwFlags;
		public int dwMask;
		public int dwTime;
		public BaseTSD.ULONG_PTR dwExtraInfo;
		public int cxContact;
		public int cyContact;

		@Override
		protected java.util.List<String> getFieldOrder() {
			return java.util.Arrays.asList(
				"x","y","hSource","dwID","dwFlags","dwMask","dwTime","dwExtraInfo","cxContact","cyContact"
			);
		}
	}

	private static final int WM_TOUCH = 0x0240;

	private static final Map<Integer, Point> activeContacts = new HashMap<>();

	static void main(String[] args) {
		SwingUtilities.invokeLater(() -> {
			JFrame frame = new JFrame("Swing WM_TOUCH Demo");
			frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			JPanel panel = new JPanel() {
				@Override protected void paintComponent(Graphics g) {
					super.paintComponent(g);
					Graphics2D g2 = (Graphics2D) g;
					g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
					g2.setColor(new Color(0x2979FF));
					for (Map.Entry<Integer, Point> e : activeContacts.entrySet()) {
						Point p = e.getValue();
						g2.fillOval(p.x - 20, p.y - 20, 40, 40);
						g2.drawString("id=" + e.getKey(), p.x + 24, p.y);
					}
				}
			};
			panel.setBackground(Color.WHITE);
			frame.setContentPane(panel);
			frame.setSize(800, 600);
			frame.setLocationRelativeTo(null);

			installWmTouch(frame, panel);

			frame.setVisible(true);
		});
	}

	private static void installWmTouch(JFrame frame, JComponent repaintTarget) {
		frame.addWindowListener(new WindowAdapter() {
			@Override public void windowOpened(WindowEvent e) {
				WinDef.HWND hwnd = getHwnd(frame);
				User32Ex.INSTANCE.RegisterTouchWindow(hwnd, 0);
				subclassWindowProc(hwnd, repaintTarget);
			}
			@Override public void windowClosed(WindowEvent e) {
				WinDef.HWND hwnd = getHwnd(frame);
				User32Ex.INSTANCE.UnregisterTouchWindow(hwnd);
			}
		});
	}

    private static WinDef.HWND getHwnd(Window w) {
        Pointer ptr = Native.getComponentPointer(w);
        return new WinDef.HWND(ptr);
    }

	private static void subclassWindowProc(WinDef.HWND hwnd, JComponent repaintTarget) {
		WinUser.WindowProc newProc = new WinUser.WindowProc() {
			@Override public WinDef.LRESULT callback(WinDef.HWND hWnd, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
				if (uMsg == WM_TOUCH) {
					int cInputs = wParam.intValue() & 0xFFFF;
					WinNT.HANDLE hInput = new WinNT.HANDLE(Pointer.createConstant(lParam.longValue()));
					TOUCHINPUT[] inputs = (TOUCHINPUT[]) new TOUCHINPUT().toArray(cInputs);
					if (User32Ex.INSTANCE.GetTouchInputInfo(hInput, cInputs, inputs, inputs[0].size())) {
						for (TOUCHINPUT ti : inputs) {
							int id = ti.dwID;
							int x = Math.round(ti.x / 100.0f);
							int y = Math.round(ti.y / 100.0f);
							boolean isDown = (ti.dwFlags & 0x0002) != 0; // TOUCHEVENTF_DOWN
							boolean isUp = (ti.dwFlags & 0x0004) != 0;   // TOUCHEVENTF_UP
							if (isDown || (!isUp)) {
								activeContacts.put(id, new Point(x, y));
								System.out.println("Touch id=" + id + " x=" + x + " y=" + y);
							}
							if (isUp) {
								activeContacts.remove(id);
								System.out.println("Touch id=" + id + " released");
							}
						}
						System.out.println("Active touches: " + activeContacts.size());
						repaintTarget.repaint();
					}
					User32Ex.INSTANCE.CloseTouchInputHandle(hInput);
					return new WinDef.LRESULT(0);
				}
				return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
			}
		};

		// Subclass: pass function pointer to WindowProc
		Pointer fn = CallbackReference.getFunctionPointer(newProc);
		User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, fn);
	}
}
