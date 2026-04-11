package demo;

import fasttouch.FastTouch;
import fasttouch.FastTouch.TouchPoint;

import javax.swing.JFrame;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * FastTouch Demo - Zeichnet Touch-Points auf den Bildschirm
 */
public class TouchDemo {
    
    private static final Map<Integer, Color> touchColors = new HashMap<>();
    private static BufferedImage canvas;
    private static Graphics2D canvasG;
    
    public static void main(String[] args) throws Exception {
        System.out.println("[DEBUG] TouchDemo starting...");
        
        // Fenster erstellen
        System.out.println("[DEBUG] Creating JFrame...");
        JFrame frame = new JFrame("FastTouch Demo - Berühre den Bildschirm!");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        System.out.println("[DEBUG] JFrame visible, title: " + frame.getTitle());
        
        // Zeichen-Canvas
        System.out.println("[DEBUG] Creating canvas...");
        canvas = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        canvasG = canvas.createGraphics();
        canvasG.setColor(Color.BLACK);
        canvasG.fillRect(0, 0, 800, 600);
        
        // Touch initialisieren
        System.out.println("[DEBUG] Initialisiere FastTouch...");
        FastTouch touch = FastTouch.create(frame);
        System.out.println("[DEBUG] FastTouch created successfully");
        
        // Prüfe Verfügbarkeit
        System.out.println("[DEBUG] Checking touch availability...");
        boolean available = FastTouch.isTouchAvailable();
        System.out.println("[DEBUG] Touch verfügbar: " + available);
        System.out.println("[DEBUG] Max Touch Points: " + FastTouch.getMaxTouchPoints());
        
        if (!available) {
            System.err.println("[DEBUG] WARNUNG: Kein Touchscreen erkannt!");
        } else {
            System.out.println("[DEBUG] Touchscreen detected - ready for input");
        }
        
        // Touch-Listener für Malen
        System.out.println("[DEBUG] Adding touch listener...");
        touch.addListener(point -> {
            // Farbe pro Touch-ID zuweisen
            touchColors.putIfAbsent(point.id, getColorForId(point.id));
            Color color = touchColors.get(point.id);
            
            System.out.println("[TOUCH] " + point + " color=" + color);
            
            // Nur bei DOWN oder MOVE zeichnen
            if (point.state != FastTouch.State.UP) {
                canvasG.setColor(color);
                
                // Kreisgröße basierend auf Pressure
                int size = Math.max(10, point.pressure / 5);
                canvasG.fillOval(point.x - size/2, point.y - size/2, size, size);
                
                // Frame neu zeichnen (einfache Version ohne FastGraphics)
                frame.repaint();
            }
            
            // Cleanup bei UP
            if (point.state == FastTouch.State.UP) {
                touchColors.remove(point.id);
            }
        });
        
        // Touch-Polling starten
        touch.start();
        System.out.println("Touch-Polling gestartet. Berühre den Bildschirm!");
        System.out.println("ESC zum Beenden");
        
        // Tastatur-Listener für Beenden
        frame.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    touch.stop();
                    System.exit(0);
                }
            }
        });
        
        // Einfaches Zeichnen mit Java2D (langsam aber funktioniert)
        while (frame.isVisible()) {
            java.awt.Graphics g = frame.getGraphics();
            if (g != null) {
                g.drawImage(canvas, 0, 0, null);
                g.dispose();
            }
            Thread.sleep(16); // ~60 FPS
        }
    }
    
    private static Color getColorForId(int id) {
        Color[] colors = {
            Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW,
            Color.CYAN, Color.MAGENTA, Color.ORANGE, Color.PINK,
            Color.WHITE, Color.LIGHT_GRAY
        };
        return colors[id % colors.length];
    }
}
