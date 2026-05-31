# FastTouch API Reference

## 1. FastTouch

The core class responsible for handling native `WM_POINTER` input and delivering multi-touch events directly to the JVM without AWT overhead.

### Initialization

```java
public static FastTouch create(javax.swing.JFrame frame)
```
Hooks into the given JFrame's native window procedure. The frame **must** be visible (`setVisible(true)`) and have a title before calling this.

```java
public void start()
```
Starts processing the touch events. Once started, any physical touches on the window will be delivered to registered listeners.

### Event Listening

```java
public void addListener(TouchListener listener)
```
Registers a callback to receive `TouchPoint` data. Note: The callbacks are invoked from the native polling thread. Use `SwingUtilities.invokeLater()` if updating UI components.

### `TouchPoint` Data Class

An immutable record containing all properties of a physical touch:

- `int id`: The unique tracking ID for the finger (0-9).
- `int x, y`: Window-relative coordinates in pixels.
- `int pressure`: The contact pressure, normalized from 0 to 255. (If the hardware doesn't support pressure, this safely defaults to 128 or 255).
- `int width, height`: The physical contact area bounds (ellipse axes) in pixels.
- `State state`: `DOWN` (initial contact), `MOVE` (drag), or `UP` (finger lifted).
- `long timestamp`: System event time in milliseconds.

---

## 2. FastGesture

A utility class that mathematically translates a stream of raw `TouchPoint` data into high-level gestures (Panning, Pinch-to-Zoom, and Rotation) including physics-based 1-finger torque.

### Initialization

```java
public FastGesture(GestureListener listener, java.util.function.Predicate<TouchPoint> hitTest)
```
- `listener`: The callback interface receiving gesture updates.
- `hitTest`: A predicate used to validate if a touch point is allowed to interact with the object. If all active fingers pass the hit test, the gesture is allowed.

### `GestureListener` Interface

```java
void onPan(float dx, float dy, float x, float y);
```
Fired when 1 finger is dragging. 
- `dx, dy`: The translation delta since the last frame.
- `x, y`: The absolute coordinate of the touch point (useful for calculating physical torque/lever arm).

```java
void onPinchStart(float centerX, float centerY);
```
Fired the exact moment a second finger touches the screen. Provides the centroid of the two fingers.

```java
void onPinchUpdate(float scale, float angleDelta, float distance, float centerX, float centerY);
```
Fired during a 2-finger pinch/rotate gesture.
- `scale`: The current zoom scale factor *relative* to the start of the gesture (starts at 1.0).
- `angleDelta`: The rotational delta (in degrees) since the *last frame*. Add this to your current rotation.
- `distance`: The absolute distance between the two fingers in pixels.
- `centerX, centerY`: The absolute centroid of the two fingers.

```java
void onPinchEnd();
```
Fired when one or both fingers are lifted, terminating the 2-finger gesture.

### Usage Example

Instead of managing state manually, you simply feed all current touches to the gesture engine in your render/update loop:

```java
// Feed all active touches (Map<Integer, TouchPoint>) to the gesture engine
fastGesture.update(activeTouches);
```
