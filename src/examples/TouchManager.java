package examples.multitouch;

import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

/**
 * Touch manager that handles Windows WM_TOUCH events and notifies listeners.
 * Use this instead of mouse listeners when examples.multitouch is needed.
 */
public class TouchManager {
    private final Map<Integer, Point> activeContacts = new HashMap<>();
    private TouchListener listener;
    
    public void setTouchListener(TouchListener listener) {
        this.listener = listener;
    }
    
    public void handleTouchEvent(int touchId, int x, int y, boolean isDown, boolean isUp, boolean isMove) {
        if (isDown || isMove) {
            activeContacts.put(touchId, new Point(x, y));
            if (listener != null) {
                if (isDown) {
                    listener.onTouchDown(touchId, x, y);
                } else {
                    listener.onTouchMove(touchId, x, y);
                }
            }
        }
        
        if (isUp) {
            activeContacts.remove(touchId);
            if (listener != null) {
                listener.onTouchUp(touchId);
            }
        }
        
        if (listener != null) {
            listener.onTouchCountChanged(activeContacts.size());
        }
    }
    
    public int getActiveTouchCount() {
        return activeContacts.size();
    }
    
    public Map<Integer, Point> getActiveContacts() {
        return new HashMap<>(activeContacts);
    }
}
