package demo;

import fasttouch.FastTouch;
import fasttouch.FastTouch.TouchPoint;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * FastTouch Demo - Zeichnet Touch-Points auf den Bildschirm
 * MIT Debug-Log JTextArea
 */
public class TouchDemo {
    
    private static final Map<Integer, Color> touchColors = new HashMap<>();
    private static BufferedImage canvas;
    private static Graphics2D canvasG;
    private static JTextArea debugLog;
    private static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    
    public static void main(String[] args) throws Exception {
        log("TouchDemo starting...");
        
        // Fenster erstellen
        log("Creating JFrame...");
        JFrame frame = new JFrame("FastTouch Demo");
        frame.setSize(1000, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        // Links: Zeichen-Panel
        JPanel drawPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (canvas != null) {
                    g.drawImage(canvas, 0, 0, null);
                }
            }
        };
        drawPanel.setPreferredSize(new Dimension(800, 600));
        drawPanel.setBackground(Color.BLACK);
        
        // Rechts: Debug-Log
        debugLog = new JTextArea(30, 30);
        debugLog.setEditable(false);
        debugLog.setFont(new Font("Monospaced", Font.PLAIN, 10));
        debugLog.setBackground(Color.DARK_GRAY);
        debugLog.setForeground(Color.GREEN);
        JScrollPane scrollPane = new JScrollPane(debugLog);
        scrollPane.setPreferredSize(new Dimension(200, 600));
        
        frame.add(drawPanel, BorderLayout.CENTER);
        frame.add(scrollPane, BorderLayout.EAST);
        frame.setVisible(true);
        log("JFrame visible, title: '" + frame.getTitle() + "'");
        
        // Zeichen-Canvas
        log("Creating canvas...");
        canvas = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        canvasG = canvas.createGraphics();
        canvasG.setColor(Color.BLACK);
        canvasG.fillRect(0, 0, 800, 600);
        
        // Touch initialisieren
        log("Initialisiere FastTouch...");
        FastTouch touch = FastTouch.create(frame);
        log("FastTouch created successfully");
        
        // Prüfe Verfügbarkeit
        log("Checking touch availability...");
        boolean available = FastTouch.isTouchAvailable();
        log("Touch verfügbar: " + available);
        log("Max Touch Points: " + FastTouch.getMaxTouchPoints());
        
        if (!available) {
            log("WARNUNG: Kein Touchscreen erkannt!");
        } else {
            log("Touchscreen detected - ready for input");
        }
        
        // Touch-Listener für Malen
        log("Adding touch listener...");
        touch.addListener(point -> {
            // Farbe pro Touch-ID zuweisen
            touchColors.putIfAbsent(point.id, getColorForId(point.id));
            Color color = touchColors.get(point.id);
            
            log("[TOUCH] id=" + point.id + " pos=(" + point.x + "," + point.y + ") " + 
                "pressure=" + point.pressure + " size=" + point.width + "x" + point.height + " " + point.state);
            
            // Nur bei DOWN oder MOVE zeichnen
            if (point.state != FastTouch.State.UP) {
                canvasG.setColor(color);
                
                // Kreisgröße basierend auf Pressure
                int size = Math.max(10, point.pressure / 5);
                canvasG.fillOval(point.x - size/2, point.y - size/2, size, size);
                
                // Panel neu zeichnen
                drawPanel.repaint();
            }
            
            // Cleanup bei UP
            if (point.state == FastTouch.State.UP) {
                touchColors.remove(point.id);
            }
        });
        
        // Touch-Polling starten
        touch.start();
        log("Touch-Polling gestartet. Berühre den Bildschirm!");
        log("ESC zum Beenden");
        
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
        
        // Animation loop
        while (frame.isVisible()) {
            drawPanel.repaint();
            Thread.sleep(16); // ~60 FPS
        }
    }
    
    private static void log(String msg) {
        String time = timeFormat.format(new Date());
        String line = "[" + time + "] " + msg;
        if (debugLog != null) {
            SwingUtilities.invokeLater(() -> {
                debugLog.append(line + "\n");
                debugLog.setCaretPosition(debugLog.getDocument().getLength());
            });
        }
        System.out.println(line);
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
