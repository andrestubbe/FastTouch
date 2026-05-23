package fasttheme;

import fasttouch.FastTouch;
import fasttouch.FastTouch.TouchPoint;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class WindowDemo extends JFrame {
    
    // Touch tracking
    private final Map<Integer, Point> activeTouches = new HashMap<>();
    private BufferedImage canvas;
    private Graphics2D canvasG;
    private JPanel drawPanel;
    private FastTouch touch;
    
    // Real detection values (populated via JNI)
    private int systemDPI = 96;
    private String systemTheme = "Unknown";
    private int systemRefresh = 60;
    private String systemResolution = "Unknown";
    
    public WindowDemo() {
        // No decorations from Swing - we use native
        setTitle("FastTouch Demo");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        
        // Set window size to 75% of screen
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screenSize.width * 0.75);
        int height = (int) (screenSize.height * 0.75);
        setSize(width, height);
        setLocation((screenSize.width - width) / 2, (screenSize.height - height) / 2);
        
        // Drawing panel for touch visualization - DOUBLE BUFFERED
        drawPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (canvas != null) {
                    Graphics2D g2d = (Graphics2D) g;
                    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                    g2d.drawImage(canvas, 0, 0, null);
                }
            }
        };
        drawPanel.setPreferredSize(new Dimension(width, height));
        drawPanel.setBackground(Color.BLACK);
        drawPanel.setDoubleBuffered(true); // Enable double buffering
        
        // Canvas for drawing white circles
        canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        canvasG = canvas.createGraphics();
        canvasG.setColor(Color.BLACK);
        canvasG.fillRect(0, 0, width, height);
        // Enable anti-aliasing for smooth circles
        canvasG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        setContentPane(drawPanel);
        
        // Set custom icon (white circle with black dot)
        setIconImage(createWindowIcon());
    }
    
    private BufferedImage createWindowIcon() {
        int size = 32;
        int center = size / 2;
        int radius = 14;
        int dotRadius = 4;
        
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        
        // Transparent background
        g2.setComposite(AlphaComposite.Clear);
        g2.fillRect(0, 0, size, size);
        g2.setComposite(AlphaComposite.SrcOver);
        
        // White circle
        g2.setColor(Color.WHITE);
        g2.fillOval(center - radius, center - radius, radius * 2, radius * 2);
        
        // Black dot in center
        g2.setColor(Color.BLACK);
        g2.fillOval(center - dotRadius, center - dotRadius, dotRadius * 2, dotRadius * 2);
        
        g2.dispose();
        return image;
    }
    
    private void detectSystemValues() {
        try {
            systemResolution = FastTheme.getSystemResolution();
            systemDPI = FastTheme.getSystemDPI();
            systemTheme = FastTheme.isSystemDarkMode() ? "Dark" : "Light";
            systemRefresh = FastTheme.getSystemRefreshRate();
        } catch (Exception e) {
            // Fallback values
            systemResolution = "Unknown";
            systemDPI = 96;
            systemTheme = "Unknown";
            systemRefresh = 60;
        }
    }
    
    @Override
    public void setVisible(boolean visible) {
        if (visible && !isVisible()) {
            super.setVisible(true);
            
            // IMMEDIATELY apply native styling using FastTheme API
            long hwnd = FastTheme.getWindowHandle(this);
            System.out.println("[DEBUG] HWND = 0x" + Long.toHexString(hwnd));
            if (hwnd != 0) {
                // Detect real system values first
                detectSystemValues();
                
                // 80% transparency (204/255)
                boolean t1 = FastTheme.setWindowTransparency(hwnd, 204);
                System.out.println("[DEBUG] Transparency set: " + t1);
                
                // Titlebar BLACK (RGB 0, 0, 0)
                boolean t2 = FastTheme.setTitleBarColor(hwnd, 0, 0, 0);
                System.out.println("[DEBUG] TitleBar color set: " + t2);
                
                // White text
                boolean t3 = FastTheme.setTitleBarTextColor(hwnd, 255, 255, 255);
                System.out.println("[DEBUG] TitleBar text set: " + t3);
                
                // Dark mode
                boolean t4 = FastTheme.setTitleBarDarkMode(hwnd, true);
                System.out.println("[DEBUG] Dark mode set: " + t4);
                
                // Set custom icon (white circle with black dot)
                boolean iconOk = false; // setWindowIcon not in FastTheme API yet
                
                // Window styling applied successfully
                
                // Initialize FastTouch for touch input
                initFastTouch();
            }
        } else {
            super.setVisible(visible);
        }
    }
    
    private void initFastTouch() {
        try {
            touch = FastTouch.create(this);
            System.out.println("[DEBUG] FastTouch initialized");
            
            // Add touch listener - draws WHITE circles for fingers (thread-safe)
            touch.addListener(point -> {
                // Execute on EDT for thread-safe UI updates
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
                        int size = 80; // Fixed size for visibility
                        canvasG.drawOval(pos.x - size/2, pos.y - size/2, size, size);
                        
                        // White center dot
                        canvasG.fillOval(pos.x - 6, pos.y - 6, 12, 12);
                    }
                    
                    drawPanel.repaint();
                });
            });
            
            // Start touch polling
            touch.start();
            System.out.println("[DEBUG] FastTouch polling started");
            
        } catch (Exception e) {
            System.out.println("[DEBUG] FastTouch init failed: " + e.getMessage());
        }
    }
    
    public static void main(String[] args) {
        // Disable UI scaling - coordinates must match native touch exactly
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        // No Swing LAF - we want native
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            new WindowDemo().setVisible(true);
        });
    }
}
