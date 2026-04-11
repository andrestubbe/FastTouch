package examples.multitouch;

import com.sun.jna.CallbackReference;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.win32.W32APIOptions;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Touch-enabled JPanel that handles Windows WM_TOUCH events.
 * Use this instead of regular JPanel when you need examples.multitouch support.
 * IMPORTANT: Disable mouse listeners when using this component.
 */
public class TouchEnabledPanel extends JPanel {
    
    private interface User32Ex extends com.sun.jna.Library {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean RegisterTouchWindow(WinDef.HWND hWnd, int ulFlags);
        boolean UnregisterTouchWindow(WinDef.HWND hWnd);
        boolean GetTouchInputInfo(WinNT.HANDLE hTouchInput, int cInputs, TOUCHINPUT[] pInputs, int cbSize);
        boolean CloseTouchInputHandle(WinNT.HANDLE hTouchInput);
        short GetKeyState(int nVirtKey);
    }
    
    private static final int VK_CONTROL = 0x11;
    private static final int VK_SHIFT = 0x10;
    
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
    private static final int TOUCHEVENTF_DOWN = 0x0002;
    private static final int TOUCHEVENTF_UP = 0x0004;
    private static final int TOUCHEVENTF_MOVE = 0x0001;
    
    // Mouse messages
    private static final int WM_LBUTTONDOWN = 0x0201;
    private static final int WM_LBUTTONUP = 0x0202;
    private static final int WM_MOUSEMOVE = 0x0200;
    private static final int WM_RBUTTONDOWN = 0x0204;
    private static final int WM_RBUTTONUP = 0x0205;
    
    private final TouchManager touchManager;
    private WinUser.WindowProc windowProcRef;
    private Pointer originalWndProcPtr;
    
    public TouchEnabledPanel() {
        this.touchManager = new TouchManager();
        this.touchManager.setTouchListener(new ConsoleTouchListener());
    }
    
    public void setTouchListener(TouchListener listener) {
        touchManager.setTouchListener(listener);
    }
    
    public TouchManager getTouchManager() {
        return touchManager;
    }
    
    /**
     * Call this method to enable touch support for this panel.
     * Should be called after the panel is added to a JFrame.
     */
    public void enableTouchSupport(JFrame frame) {
        
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                WinDef.HWND hwnd = getHwnd(frame);
                User32Ex.INSTANCE.RegisterTouchWindow(hwnd, 0);
                subclassWindowProc(hwnd);
            }
            
            @Override
            public void windowClosed(WindowEvent e) {
                WinDef.HWND hwnd = getHwnd(frame);
                User32Ex.INSTANCE.UnregisterTouchWindow(hwnd);
                if (originalWndProcPtr != null && windowProcRef != null) {
                    User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, originalWndProcPtr);
                }
            }
        });
    }
    
    private WinDef.HWND getHwnd(Window w) {
        Pointer ptr = Native.getComponentPointer(w);
        return new WinDef.HWND(ptr);
    }
    
    private void subclassWindowProc(WinDef.HWND hwnd) {
        final JComponent self = this;
        windowProcRef = new WinUser.WindowProc() {
            @Override
            public WinDef.LRESULT callback(WinDef.HWND hWnd, int uMsg, WinDef.WPARAM wParam, WinDef.LPARAM lParam) {
                if (uMsg == WM_TOUCH) {
                    int cInputs = wParam.intValue() & 0xFFFF;
                    WinNT.HANDLE hInput = new WinNT.HANDLE(Pointer.createConstant(lParam.longValue()));
                    TOUCHINPUT[] inputs = (TOUCHINPUT[]) new TOUCHINPUT().toArray(cInputs);
                    
                    if (User32Ex.INSTANCE.GetTouchInputInfo(hInput, cInputs, inputs, inputs[0].size())) {
                        for (TOUCHINPUT ti : inputs) {
                            int id = ti.dwID;
                            int x = Math.round(ti.x / 100.0f);
                            int y = Math.round(ti.y / 100.0f);
                            boolean isDown = (ti.dwFlags & TOUCHEVENTF_DOWN) != 0;
                            boolean isUp = (ti.dwFlags & TOUCHEVENTF_UP) != 0;
                            boolean isMove = (ti.dwFlags & TOUCHEVENTF_MOVE) != 0;
                            
                            touchManager.handleTouchEvent(id, x, y, isDown, isUp, isMove);
                        }
                        repaint();
                    }
                    User32Ex.INSTANCE.CloseTouchInputHandle(hInput);
                    return new WinDef.LRESULT(0);
                }
                
                // Handle mouse events - convert to Swing events
                // RegisterTouchWindow blocks mouse events, so we intercept them here
                if (uMsg == WM_LBUTTONDOWN || uMsg == WM_LBUTTONUP || uMsg == WM_MOUSEMOVE || 
                    uMsg == WM_RBUTTONDOWN || uMsg == WM_RBUTTONUP) {
                    // lParam contains client coordinates (relative to window client area)
                    int x = (int) (lParam.longValue() & 0xFFFF);
                    int y = (int) ((lParam.longValue() >> 16) & 0xFFFF);
                    
                    int button = 0;
                    int id = 0;
                    if (uMsg == WM_LBUTTONDOWN) {
                        button = MouseEvent.BUTTON1;
                        id = MouseEvent.MOUSE_PRESSED;
                    } else if (uMsg == WM_LBUTTONUP) {
                        button = MouseEvent.BUTTON1;
                        id = MouseEvent.MOUSE_RELEASED;
                    } else if (uMsg == WM_RBUTTONDOWN) {
                        button = MouseEvent.BUTTON3;
                        id = MouseEvent.MOUSE_PRESSED;
                    } else if (uMsg == WM_RBUTTONUP) {
                        button = MouseEvent.BUTTON3;
                        id = MouseEvent.MOUSE_RELEASED;
                    } else if (uMsg == WM_MOUSEMOVE) {
                        // Check if a button is pressed to determine if it's a drag
                        if ((wParam.intValue() & 0x0001) != 0 || (wParam.intValue() & 0x0002) != 0) {
                            id = MouseEvent.MOUSE_DRAGGED;
                        } else {
                            id = MouseEvent.MOUSE_MOVED;
                        }
                    }
                    
                    if (id != 0) {
                        final int finalX = x;
                        final int finalY = y;
                        final int finalId = id;
                        final int finalButton = button;
                        
                        // Get current key states directly (more reliable than wParam)
                        final short ctrlState = User32Ex.INSTANCE.GetKeyState(VK_CONTROL);
                        final short shiftState = User32Ex.INSTANCE.GetKeyState(VK_SHIFT);
                        final boolean ctrlDown = (ctrlState & 0x8000) != 0;
                        final boolean shiftDown = (shiftState & 0x8000) != 0;
                        
                        final int modifiers = (uMsg == WM_MOUSEMOVE ? 0 : finalButton) | 
                            ((wParam.intValue() & 0x0001) != 0 ? MouseEvent.BUTTON1_DOWN_MASK : 0) |
                            ((wParam.intValue() & 0x0002) != 0 ? MouseEvent.BUTTON3_DOWN_MASK : 0) |
                            (shiftDown ? MouseEvent.SHIFT_DOWN_MASK : 0) |
                            (ctrlDown ? MouseEvent.CTRL_DOWN_MASK : 0);
                        
                        SwingUtilities.invokeLater(() -> {
                            MouseEvent swingEvent = new MouseEvent(
                                self,
                                finalId,
                                System.currentTimeMillis(),
                                modifiers,
                                finalX,
                                finalY,
                                1,
                                false,
                                finalButton
                            );
                            self.dispatchEvent(swingEvent);
                        });
                    }
                    
                    // Still call DefWindowProc to allow normal processing
                    return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
                }
                
                return User32.INSTANCE.DefWindowProc(hWnd, uMsg, wParam, lParam);
            }
        };
        
        Pointer fn = CallbackReference.getFunctionPointer(windowProcRef);
        originalWndProcPtr = User32.INSTANCE.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, fn);
    }
}
