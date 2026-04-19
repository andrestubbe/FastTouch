package fasttouch;

import fasttouch.FastTouch;
import fasttouch.FastTouch.TouchPoint;
import fasttouch.gesture.FastGesture;
import fasttouch.gesture.GestureEvent;
import fasttouch.gesture.GestureListener;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TouchDemo3 - Touch with Gesture Recognition Demo
 * 
 * Demonstrates FastTouch with full gesture support:
 * - Java-side: Tap, Double-Tap, Long-Press, Swipe
 * - Native-side: Pinch (Zoom), Rotate
 * 
 * Enhanced visual feedback:
 * - Tap: Circle pulses larger briefly
 * - Long Press: Circle grows while holding
 * - Rotate: Circle with radius line showing rotation
 * - Pinch: Concentric rings expanding/contracting
 * 
 * @author FastJava Team
 * @version 1.3.0
 */
public class TouchDemo3 extends JFrame {
    
    // Touch tracking
    private final Map<Integer, TouchVisual> activeTouches = new ConcurrentHashMap<>();
    private final Map<Integer, Long> tapEffects = new ConcurrentHashMap<>(); // Tap pulse effects
    private BufferedImage canvas;
    private Graphics2D canvasG;
    private JPanel drawPanel;
    private FastTouch touch;
    private FastGesture gesture;
    private JLabel gestureLabel;
    
    // Gesture visualization state
    private float pinchScale = 1.0f;
    private long pinchEffectEndTime = 0;
    private float rotateAngle = 0.0f;
    private Point rotateCenter = null;
    private long rotateEffectEndTime = 0;
    
    // Visual settings
    private static final int BASE_SIZE = 80;
    private static final int PULSE_SIZE = 120;
    private static final int LONG_PRESS_GROW = 40;
    private static final long EFFECT_DURATION_MS = 500;
    
    /**
     * Represents a touch point with visual state
     */
    private static class TouchVisual {
        final int id;
        float x, y;
        long downTime;
        boolean isLongPress;
        float currentSize;
        
        TouchVisual(int id, float x, float y) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.downTime = System.currentTimeMillis();
            this.isLongPress = false;
            this.currentSize = BASE_SIZE;
        }
    }
    
    public TouchDemo3() {
        setTitle("FastTouch Demo 3 - Touch + Gestures");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Set window size to 75% of screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.75);
        int height = (int) (screenSize.height * 0.75);
        setSize(width, height);
        setLocation((screenSize.width - width) / 2, (screenSize.height - height) / 2);
        
        // Drawing panel for touch visualization
        drawPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (canvas != null) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.drawImage(canvas, 0, 0, null);
                }
            }
        };
        drawPanel.setPreferredSize(new Dimension(width, height));
        drawPanel.setBackground(Color.BLACK);
        drawPanel.setDoubleBuffered(true);
        
        // Canvas for drawing
        canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        canvasG = canvas.createGraphics();
        canvasG.setColor(Color.BLACK);
        canvasG.fillRect(0, 0, width, height);
        canvasG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        setContentPane(drawPanel);
    }
    
    private void initFastTouch() {
        try {
            touch = FastTouch.create(this);
            System.out.println("[TouchDemo3] FastTouch initialized");
            
            // Add touch listener with visual effects
            touch.addListener(point -> {
                SwingUtilities.invokeLater(() -> {
                    long now = System.currentTimeMillis();
                    
                    if (point.state == FastTouch.State.DOWN) {
                        activeTouches.put(point.id, new TouchVisual(point.id, point.x, point.y));
                    } else if (point.state == FastTouch.State.MOVE) {
                        TouchVisual tv = activeTouches.get(point.id);
                        if (tv != null) {
                            tv.x = point.x;
                            tv.y = point.y;
                        }
                    } else if (point.state == FastTouch.State.UP) {
                        activeTouches.remove(point.id);
                    }
                    
                    drawScene();
                });
            });
            
            // Start animation timer for smooth visual effects
            Timer animTimer = new Timer(16, e -> drawScene()); // ~60fps
            animTimer.start();
            
            touch.start();
            System.out.println("[TouchDemo3] FastTouch polling started");
            
        } catch (Exception e) {
            System.out.println("[TouchDemo3] FastTouch init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initFastGesture() {
        try {
            if (touch == null) {
                System.out.println("[TouchDemo3] Cannot init gestures - FastTouch not available");
                return;
            }
            
            gesture = FastGesture.create(touch);
            System.out.println("[TouchDemo3] FastGesture initialized");
            
            // Create gesture display label
            gestureLabel = new JLabel("Gestures: none", SwingConstants.CENTER);
            gestureLabel.setForeground(Color.GREEN);
            gestureLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
            gestureLabel.setBounds(10, 10, 350, 30);
            drawPanel.setLayout(null);
            drawPanel.add(gestureLabel);
            
            // Add gesture listener with visual effects
            gesture.addListener(new GestureListener() {
                @Override
                public void onGesture(GestureEvent e) {
                    SwingUtilities.invokeLater(() -> {
                        long now = System.currentTimeMillis();
                        
                        switch (e.type) {
                            case TAP:
                                gestureLabel.setText("TAP at " + (int)e.x + "," + (int)e.y);
                                System.out.println("[GESTURE] Tap at (" + (int)e.x + "," + (int)e.y + ")");
                                // Trigger pulse effect on nearest touch
                                triggerTapEffect(e.x, e.y);
                                break;
                            case DOUBLE_TAP:
                                gestureLabel.setText("DOUBLE TAP!");
                                System.out.println("[GESTURE] Double tap");
                                triggerTapEffect(e.x, e.y);
                                break;
                            case LONG_PRESS:
                                gestureLabel.setText("LONG PRESS at " + (int)e.x + "," + (int)e.y);
                                System.out.println("[GESTURE] Long press");
                                // Mark nearest touch as long press (grows)
                                markLongPress(e.x, e.y);
                                break;
                            case SWIPE:
                                gestureLabel.setText("SWIPE " + e.direction + " " + (int)e.velocity + "px/s");
                                System.out.println("[GESTURE] Swipe " + e.direction);
                                break;
                            case PINCH:
                                pinchScale = e.scale;
                                pinchEffectEndTime = now + EFFECT_DURATION_MS;
                                gestureLabel.setText(String.format("PINCH scale=%.2f", e.scale));
                                System.out.printf("[GESTURE] Pinch scale=%.2f%n", e.scale);
                                break;
                            case ROTATE:
                                rotateAngle = e.rotation;
                                rotateCenter = new Point((int)e.x, (int)e.y);
                                rotateEffectEndTime = now + EFFECT_DURATION_MS;
                                gestureLabel.setText(String.format("ROTATE %.1f°", e.rotation));
                                System.out.printf("[GESTURE] Rotate %.1f°%n", e.rotation);
                                break;
                        }
                    });
                }
            });
            
            System.out.println("[TouchDemo3] FastGesture listener registered");
            
        } catch (Exception e) {
            System.out.println("[TouchDemo3] FastGesture init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void triggerTapEffect(float x, float y) {
        // Find nearest touch and add pulse effect
        TouchVisual nearest = findNearestTouch(x, y);
        if (nearest != null) {
            tapEffects.put(nearest.id, System.currentTimeMillis());
        }
    }
    
    private void markLongPress(float x, float y) {
        TouchVisual nearest = findNearestTouch(x, y);
        if (nearest != null) {
            nearest.isLongPress = true;
        }
    }
    
    private TouchVisual findNearestTouch(float x, float y) {
        TouchVisual nearest = null;
        float minDist = Float.MAX_VALUE;
        for (TouchVisual tv : activeTouches.values()) {
            float dist = (float) Math.hypot(tv.x - x, tv.y - y);
            if (dist < minDist) {
                minDist = dist;
                nearest = tv;
            }
        }
        return nearest;
    }
    
    private void drawScene() {
        int w = canvas.getWidth();
        int h = canvas.getHeight();
        long now = System.currentTimeMillis();
        
        // Clear canvas
        canvasG.setColor(Color.BLACK);
        canvasG.fillRect(0, 0, w, h);
        
        // Draw pinch effect (concentric rings)
        if (now < pinchEffectEndTime) {
            drawPinchEffect(w / 2, h / 2, now);
        }
        
        // Draw rotate effect (circle with radius line)
        if (now < rotateEffectEndTime && rotateCenter != null) {
            drawRotateEffect(rotateCenter.x, rotateCenter.y, rotateAngle);
        }
        
        // Draw touch circles with effects
        for (TouchVisual tv : activeTouches.values()) {
            drawTouchCircle(tv, now);
        }
        
        // Clean up expired tap effects
        tapEffects.entrySet().removeIf(e -> now - e.getValue() > EFFECT_DURATION_MS);
        
        drawPanel.repaint();
    }
    
    private void drawTouchCircle(TouchVisual tv, long now) {
        float x = tv.x;
        float y = tv.y;
        int size = BASE_SIZE;
        
        // Calculate size based on effects
        Long tapTime = tapEffects.get(tv.id);
        if (tapTime != null) {
            long elapsed = now - tapTime;
            if (elapsed < 200) {
                // Pulse up
                float progress = elapsed / 200.0f;
                size = BASE_SIZE + (int)((PULSE_SIZE - BASE_SIZE) * (float)Math.sin(progress * Math.PI));
            }
        }
        
        if (tv.isLongPress) {
            // Grow for long press
            long holdTime = now - tv.downTime;
            int grow = Math.min((int)(holdTime / 10), LONG_PRESS_GROW);
            size += grow;
        }
        
        // Draw main circle
        canvasG.setColor(Color.WHITE);
        canvasG.setStroke(new BasicStroke(3));
        canvasG.drawOval((int)(x - size/2), (int)(y - size/2), size, size);
        
        // Draw center dot
        canvasG.fillOval((int)(x - 6), (int)(y - 6), 12, 12);
        
        // Draw size indicator for long press
        if (tv.isLongPress && size > BASE_SIZE) {
            canvasG.setColor(Color.CYAN);
            canvasG.setStroke(new BasicStroke(2));
            canvasG.drawOval((int)(x - size/2), (int)(y - size/2), size, size);
        }
    }
    
    private void drawPinchEffect(int cx, int cy, long now) {
        float progress = 1.0f - Math.min(1.0f, (now - (pinchEffectEndTime - EFFECT_DURATION_MS)) / (float)EFFECT_DURATION_MS);
        
        // Draw concentric rings that expand/contract
        canvasG.setColor(new Color(0, 255, 0, (int)(128 * progress)));
        canvasG.setStroke(new BasicStroke(2));
        
        int rings = 3;
        for (int i = 0; i < rings; i++) {
            float baseRadius = 50 + i * 40;
            float radius = baseRadius * pinchScale * (0.8f + 0.2f * progress);
            int alpha = (int)(100 * progress * (1.0f - i / (float)rings));
            canvasG.setColor(new Color(0, 255, 0, Math.max(0, alpha)));
            canvasG.drawOval((int)(cx - radius), (int)(cy - radius), (int)(radius * 2), (int)(radius * 2));
        }
    }
    
    private void drawRotateEffect(int cx, int cy, float angle) {
        float radius = 60;
        
        // Draw circle
        canvasG.setColor(Color.YELLOW);
        canvasG.setStroke(new BasicStroke(3));
        canvasG.drawOval((int)(cx - radius), (int)(cy - radius), (int)(radius * 2), (int)(radius * 2));
        
        // Draw center dot
        canvasG.fillOval(cx - 5, cy - 5, 10, 10);
        
        // Draw radius line showing rotation
        double rad = Math.toRadians(angle);
        int endX = (int)(cx + radius * Math.cos(rad));
        int endY = (int)(cy + radius * Math.sin(rad));
        
        canvasG.setColor(Color.RED);
        canvasG.setStroke(new BasicStroke(4));
        canvasG.drawLine(cx, cy, endX, endY);
        
        // Draw arc showing rotation amount
        canvasG.setColor(new Color(255, 255, 0, 80));
        int arcSize = (int)(radius * 2);
        int startAngle = 0;
        int arcAngle = (int)angle;
        canvasG.fillArc((int)(cx - radius), (int)(cy - radius), arcSize, arcSize, startAngle, arcAngle);
    }
    
    public static void main(String[] args) {
        // Disable UI scaling
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            TouchDemo3 demo = new TouchDemo3();
            demo.setVisible(true);
            demo.initFastTouch();
            demo.initFastGesture();
        });
    }
}
