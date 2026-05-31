package fasttouch;

import java.awt.geom.Point2D;
import java.util.Map;
import fasttouch.FastTouch.TouchPoint;

/**
 * High-level Gesture Recognizer for FastTouch.
 * Processes raw touch maps and emits Pan, Pinch, and Rotate events.
 */
public class FastGesture {

    private float initialPinchDistance = 0f;
    private float currentPinchDistance = 0f;
    private float currentPinchScale = 1.0f;
    private float currentRotateAngle = 0.0f;
    
    private Point2D.Float gestureCenter = new Point2D.Float();
    private Point2D.Float lastTouchCenter = null;
    private int lastTouchCount = 0;
    private boolean isGestureActive = false;
    
    private final GestureListener listener;
    private java.util.function.Predicate<TouchPoint> hitTest;

    public interface GestureListener {
        void onPan(float dx, float dy, float x, float y);
        void onPinchStart(float centerX, float centerY);
        void onPinchUpdate(float scale, float angleDelta, float distance, float centerX, float centerY);
        void onPinchEnd();
    }

    public FastGesture(GestureListener listener, java.util.function.Predicate<TouchPoint> hitTest) {
        this.listener = listener;
        this.hitTest = hitTest;
    }

    /**
     * Processes the current active touches and triggers gesture events.
     * @param touches Map of active touches. Only touches relevant to the target element should be passed.
     */
    public void update(Map<Integer, TouchPoint> touches) {
        if (touches == null || touches.isEmpty()) {
            resetState();
            return;
        }

        // Calculate center of all touches
        float cx = 0, cy = 0;
        boolean allHit = true;
        for (TouchPoint tp : touches.values()) {
            cx += tp.x;
            cy += tp.y;
            if (hitTest != null && !hitTest.test(tp)) {
                allHit = false;
            }
        }
        cx /= touches.size();
        cy /= touches.size();

        // Handle Panning
        if (lastTouchCenter != null && lastTouchCount == touches.size() && (allHit || isGestureActive)) {
            float dx = cx - lastTouchCenter.x;
            float dy = cy - lastTouchCenter.y;
            if (listener != null && (dx != 0 || dy != 0)) {
                listener.onPan(dx, dy, cx, cy);
            }
        }

        // Handle Pinch / Rotate (requires exactly 2 fingers)
        if (touches.size() == 2) {
            TouchPoint[] pts = touches.values().toArray(new TouchPoint[0]);

            // Ensure consistent order to prevent 180-degree angle flips
            if (pts[0].id > pts[1].id) {
                TouchPoint tmp = pts[0];
                pts[0] = pts[1];
                pts[1] = tmp;
            }

            float dx = pts[1].x - pts[0].x;
            float dy = pts[1].y - pts[0].y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);
            float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

            if (lastTouchCount != 2) {
                // Just started 2-finger gesture
                if (allHit) {
                    initialPinchDistance = distance;
                    currentPinchDistance = distance;
                    currentRotateAngle = angle;
                    gestureCenter.setLocation(cx, cy);
                    isGestureActive = true;
                    
                    if (listener != null) {
                        listener.onPinchStart(cx, cy);
                    }
                }
            } else if (isGestureActive && initialPinchDistance > 0) {
                // Continuing gesture
                float scale = distance / initialPinchDistance;

                // Calculate rotation delta handling wrap-around
                float angleDelta = angle - currentRotateAngle;
                if (angleDelta > 180) angleDelta -= 360;
                if (angleDelta < -180) angleDelta += 360;

                currentPinchScale = scale;
                currentPinchDistance = distance;
                currentRotateAngle = angle;
                gestureCenter.setLocation(cx, cy);

                if (listener != null) {
                    listener.onPinchUpdate(scale, angleDelta, distance, cx, cy);
                }
            }
        } else if (isGestureActive) {
            // End gesture if finger count changes
            isGestureActive = false;
            currentPinchScale = 1.0f;
            currentPinchDistance = 0f;
            if (listener != null) {
                listener.onPinchEnd();
            }
        }

        lastTouchCenter = new Point2D.Float(cx, cy);
        lastTouchCount = touches.size();
    }
    
    public void reset() {
        resetState();
    }

    private void resetState() {
        if (isGestureActive && listener != null) {
            listener.onPinchEnd();
        }
        lastTouchCenter = null;
        lastTouchCount = 0;
        isGestureActive = false;
        currentPinchScale = 1.0f;
        currentRotateAngle = 0.0f;
        currentPinchDistance = 0f;
    }
    
    public boolean isGestureActive() {
        return isGestureActive;
    }
    
    public float getCurrentPinchDistance() {
        return currentPinchDistance;
    }
    
    public float getCurrentRotateAngle() {
        return currentRotateAngle;
    }
    
    public float getCurrentPinchScale() {
        return currentPinchScale;
    }
    
    public Point2D.Float getGestureCenter() {
        return gestureCenter;
    }
}
