package fasttouch;

import fasttouch.FastTouch;
import fasttouch.FastTouch.TouchPoint;
import fasttheme.FastTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FastTouch Demo - GC-optimierte Version mit FastGesture
 */
public class TouchDemo extends JPanel {

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");

        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("FastTouch Demo - FPS: ...");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setIconImage(createRoundIcon());

            TouchDemo demo = new TouchDemo(frame);
            frame.add(demo);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.addNotify();

            try {
                long hwnd = FastTheme.getWindowHandle(frame);
                if (hwnd != 0) {
                    FastTheme.setTitleBarDarkMode(hwnd, true);
                    FastTheme.setTitleBarColor(hwnd, 0, 0, 0);
                    FastTheme.setTitleBarTextColor(hwnd, 255, 255, 255);
                    FastTheme.setWindowTransparency(hwnd, 224);
                }
            } catch (Throwable e) {
                System.err.println("FastTheme dark mode failed: " + e.getMessage());
            }

            frame.setVisible(true);
            demo.initFastTouch(frame);
        });
    }

    private static final int WIDTH = 1173;
    private static final int HEIGHT = 610;
    private static final int TARGET_FPS = 120;
    private static final int TOUCH_BASE_SIZE = 60;

    private static final Color[] TOUCH_ALPHA_COLORS = new Color[256];
    static {
        for (int a = 0; a < 256; a++) {
            TOUCH_ALPHA_COLORS[a] = new Color(255, 255, 255, a);
        }
    }

    private final Map<Integer, TouchPoint> activeTouches = new ConcurrentHashMap<>();
    private FastTouch touch;
    private FastGesture fastGesture;
    private final JFrame parentFrame;
    private String currentGestureText = "";

    // Box properties
    private float boxX = 400;
    private float boxY = 300;
    private final float boxWidth = 200;
    private final float boxHeight = 200;

    private float baseBoxScale = 1.0f;
    private float targetBoxScale = 1.0f;
    private float currentBoxScale = 1.0f;

    private float baseBoxRotation = 0.0f;
    private float targetBoxRotation = 0.0f;
    private float currentBoxRotation = 0.0f;

    // Reusable drawing objects
    private final Ellipse2D.Float touchCircle = new Ellipse2D.Float();
    private final Rectangle2D.Float boxRect = new Rectangle2D.Float();
    private final BasicStroke boxStroke = new BasicStroke(
            1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{2f, 2f}, 0.0f
    );
    private final AffineTransform reuseTransform = new AffineTransform();
    private final AffineTransform reuseInverse = new AffineTransform();

    public TouchDemo(JFrame parentFrame) {
        this.parentFrame = parentFrame;
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);
        setDoubleBuffered(true);

        fastGesture = new FastGesture(new FastGesture.GestureListener() {
            @Override
            public void onPan(float dx, float dy, float x, float y) {
                // 1. Calculate the lever arm R from the center of the box to the touch point
                float rx = x - boxX;
                float ry = y - boxY;

                // 2. Cross product representing torque (R x D)
                float cross = rx * dy - ry * dx;

                // 3. Rotation delta (tune the friction factor to feel right)
                float physicsFactor = 0.001f; // Mehr Trägheit (Inertia) = weniger Rotation pro Pixel
                float angleDelta = cross * physicsFactor;

                // 4. Calculate exactly how much the rotation would move the point
                double rad = Math.toRadians(angleDelta);
                double cos = Math.cos(rad);
                double sin = Math.sin(rad);

                float rxPrime = (float) (rx * cos - ry * sin);
                float ryPrime = (float) (rx * sin + ry * cos);

                // 5. Apply the rotation and correct the translation so the box sticks to the finger!
                boxX += dx + rx - rxPrime;
                boxY += dy + ry - ryPrime;
                targetBoxRotation += angleDelta;
            }

            @Override
            public void onPinchStart(float cx, float cy) {
                baseBoxScale = targetBoxScale;
                baseBoxRotation = targetBoxRotation;
            }

            @Override
            public void onPinchUpdate(float scale, float angleDelta, float distance, float cx, float cy) {
                targetBoxScale = Math.max(0.2f, Math.min(5.0f, baseBoxScale * scale));
                targetBoxRotation += angleDelta;
            }

            @Override
            public void onPinchEnd() {
                baseBoxScale = targetBoxScale;
                baseBoxRotation = targetBoxRotation;
                currentGestureText = "";
            }
        }, tp -> isPointInBox(tp.x, tp.y));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawBox(g2d);

        synchronized (activeTouches) {
            drawTouches(g2d);
        }
    }

    private void startRenderLoop() {
        Thread t = new Thread(() -> {
            long lastFpsTime = System.nanoTime();
            int frames = 0;
            long frameTimeTarget = 1_000_000_000L / TARGET_FPS;
            long lastRenderTime = System.nanoTime();

            while (true) {
                long nowLoop = System.nanoTime();
                if (nowLoop - lastRenderTime < frameTimeTarget) {
                    java.util.concurrent.locks.LockSupport.parkNanos(1_000_000);
                    continue;
                }
                lastRenderTime = nowLoop;

                if (touch != null) {
                    touch.poll();
                }

                try {
                    synchronized (activeTouches) {
                        fastGesture.update(activeTouches);
                    }

                    currentBoxScale += (targetBoxScale - currentBoxScale) * 0.15f;
                    currentBoxRotation += (targetBoxRotation - currentBoxRotation) * 0.08f; // Slower lerp for more inertia feeling

                    repaint();
                    frames++;

                    long now = System.nanoTime();
                    if (now - lastFpsTime >= 1_000_000_000L) {
                        updateFPS(frames);
                        frames = 0;
                        lastFpsTime = now;
                    }
                } catch (Throwable t1) {
                    t1.printStackTrace();
                }
            }
        }, "FastTouch-RenderLoop");

        t.setDaemon(true);
        t.start();
    }

    private void drawBox(Graphics2D g2d) {
        AffineTransform old = g2d.getTransform();
        g2d.translate(boxX, boxY);
        g2d.rotate(Math.toRadians(currentBoxRotation));

        // Scale the geometry, not the Graphics2D, to keep stroke 1px thick
        float scaledW = boxWidth * currentBoxScale;
        float scaledH = boxHeight * currentBoxScale;

        boxRect.x = -scaledW / 2f;
        boxRect.y = -scaledH / 2f;
        boxRect.width = scaledW;
        boxRect.height = scaledH;

        g2d.setColor(Color.WHITE);
        g2d.setStroke(boxStroke);
        g2d.draw(boxRect);

        g2d.setTransform(old);
    }

    private void drawTouches(Graphics2D g) {
        for (TouchPoint tp : activeTouches.values()) {
            int w = Math.max(TOUCH_BASE_SIZE, tp.width);
            int h = Math.max(TOUCH_BASE_SIZE, tp.height);

            // Windows gives ~128 (512/4) when pressure is not supported.
            // If we get exactly 128 or 0, we assume no pressure hardware and use fully opaque white.
            int alpha = tp.pressure;
            if (alpha == 128 || alpha == 0) {
                alpha = 255;
            }
            alpha = Math.max(50, Math.min(255, alpha));

//            g.setColor(TOUCH_ALPHA_COLORS[alpha]);
            g.setColor(Color.WHITE);

            touchCircle.x = tp.x - w / 2f;
            touchCircle.y = tp.y - h / 2f;
            touchCircle.width = w;
            touchCircle.height = h;

            g.fill(touchCircle);
        }
    }

    private void updateFPS(int fps) {
        String suffix = currentGestureText;
        SwingUtilities.invokeLater(() ->
                parentFrame.setTitle("FastTouch Demo - FPS: " + fps + suffix)
        );
    }

    private void initFastTouch(JFrame frame) {
        touch = FastTouch.create(frame);
        touch.setGestureEnabled(true);

        touch.addListener(point -> {
            synchronized (activeTouches) {
                if (point.state == FastTouch.State.DOWN || point.state == FastTouch.State.MOVE) {
                    activeTouches.put(point.id, point);
                } else if (point.state == FastTouch.State.UP) {
                    activeTouches.remove(point.id);
                }
            }
        });

        touch.start();
        startRenderLoop();

        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    touch.stop();
                    System.exit(0);
                }
            }
        });
    }

    private static BufferedImage createRoundIcon() {
        BufferedImage icon = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Float(4, 4, 56, 56));
        g.dispose();
        return icon;
    }

    private boolean isPointInBox(float x, float y) {
        reuseTransform.setToIdentity();
        reuseTransform.translate(boxX, boxY);
        reuseTransform.rotate(Math.toRadians(currentBoxRotation));
        reuseTransform.scale(currentBoxScale, currentBoxScale);

        try {
            reuseInverse.setTransform(reuseTransform);
            reuseInverse.invert();

            Point2D.Float src = new Point2D.Float(x, y);
            Point2D.Float dst = new Point2D.Float();
            reuseInverse.transform(src, dst);

            return dst.x >= -boxWidth / 2f && dst.x <= boxWidth / 2f &&
                    dst.y >= -boxHeight / 2f && dst.y <= boxHeight / 2f;

        } catch (NoninvertibleTransformException e) {
            return false;
        }
    }
}
