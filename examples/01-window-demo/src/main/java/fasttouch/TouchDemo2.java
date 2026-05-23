package fasttouch;

import fasttouch.FastTouch;
import fasttouch.FastTouch.TouchPoint;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * TouchDemo2 - Pure Touch Visualization Demo
 * 
 * Demonstrates FastTouch basic touch tracking with white circles on black background.
 * No gesture recognition - just raw touch point visualization.
 * 
 * @author FastJava Team
 * @version 1.3.0
 */
public class TouchDemo2 extends JFrame {
    
    // Touch tracking
    private final Map<Integer, Point> activeTouches = new HashMap<>();
    private BufferedImage canvas;
    private Graphics2D canvasG;
    private JPanel drawPanel;
    private FastTouch touch;
    
    public TouchDemo2() {
        setTitle("FastTouch Demo 2 - Pure Touch");
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
        
        // Canvas for drawing white circles
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
            System.out.println("[TouchDemo2] FastTouch initialized");
            
            // Add touch listener - draws WHITE circles for fingers
            touch.addListener(point -> {
                SwingUtilities.invokeLater(() -> {
                    // Track active touch position
                    if (point.state != FastTouch.State.UP) {
                        activeTouches.put(point.id, new Point(point.x, point.y));
                    } else {
                        activeTouches.remove(point.id);
                    }
                    
                    // Clear canvas (black background)
                    int w = canvas.getWidth();
                    int h = canvas.getHeight();
                    canvasG.setColor(Color.BLACK);
                    canvasG.fillRect(0, 0, w, h);
                    
                    // Draw WHITE circles for all active touches
                    for (Map.Entry<Integer, Point> entry : activeTouches.entrySet()) {
                        Point pos = entry.getValue();
                        
                        // White circle outline
                        canvasG.setColor(Color.WHITE);
                        canvasG.setStroke(new BasicStroke(3));
                        int size = 80;
                        canvasG.drawOval(pos.x - size/2, pos.y - size/2, size, size);
                        
                        // White center dot
                        canvasG.fillOval(pos.x - 6, pos.y - 6, 12, 12);
                    }
                    
                    drawPanel.repaint();
                });
            });
            
            // Start touch polling
            touch.start();
            System.out.println("[TouchDemo2] FastTouch polling started");
            
        } catch (Exception e) {
            System.out.println("[TouchDemo2] FastTouch init failed: " + e.getMessage());
            e.printStackTrace();
        }
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
            TouchDemo2 demo = new TouchDemo2();
            demo.setVisible(true);
            demo.initFastTouch();
        });
    }
}
