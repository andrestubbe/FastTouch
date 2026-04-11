package examples.multitouch;


/**
 * Touch listener interface for handling examples.multitouch events.
 * Provides callbacks for touch down, move, up, and active touch count changes.
 */
public interface TouchListener {
    void onTouchDown(int touchId, int x, int y);
    void onTouchMove(int touchId, int x, int y);
    void onTouchUp(int touchId);
    void onTouchCountChanged(int activeTouchCount);
}

/**
 * Simple touch listener implementation that prints to console.
 */
class ConsoleTouchListener implements TouchListener {
    @Override
    public void onTouchDown(int touchId, int x, int y) {
        System.out.println("Touch id=" + touchId + " x=" + x + " y=" + y);
    }
    
    @Override
    public void onTouchMove(int touchId, int x, int y) {
        System.out.println("Touch id=" + touchId + " move x=" + x + " y=" + y);
    }
    
    @Override
    public void onTouchUp(int touchId) {
        System.out.println("Touch id=" + touchId + " released");
    }
    
    @Override
    public void onTouchCountChanged(int activeTouchCount) {
        System.out.println("Active touches: " + activeTouchCount);
    }
}

