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
    private static final Map<Integer, Point> activeTouches = new HashMap<>();  // Aktive Touch-Positionen
    private static BufferedImage canvas;
    private static Graphics2D canvasG;
    private static JPanel drawPanel;
    private static JTextArea debugLog;
    private static SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");
    
    public static void main(String[] args) throws Exception {
        log("TouchDemo starting...");
        
        // Fenster erstellen - VOLLE BREITE für Zeichenfläche
        log("Creating JFrame...");
        JFrame frame = new JFrame("FastTouch Demo");
        frame.setSize(2000, 1400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        
        // OBEN: Volle Zeichenfläche (2000x1200)
        JPanel drawPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (canvas != null) {
                    g.drawImage(canvas, 0, 0, null);
                }
            }
        };
        drawPanel.setPreferredSize(new Dimension(2000, 1200));  // VOLLE BREITE
        drawPanel.setBackground(Color.WHITE);  // WEISSER Hintergrund
        
        // UNTEN: Debug-Log als Overlay (nicht-interaktiv, durchlässig für Touch)
        debugLog = new JTextArea(8, 80);  // Mehr Zeilen, volle Breite
        debugLog.setEditable(false);
        debugLog.setFocusable(false);  // Wichtig: Kein Fokus, kein Event-Intercept!
        debugLog.setFont(new Font("Monospaced", Font.PLAIN, 20));
        debugLog.setBackground(Color.WHITE);  // WEISSER Hintergrund
        debugLog.setForeground(Color.BLACK);   // SCHWARZER Text
        debugLog.setCaretColor(Color.GREEN);
        JScrollPane scrollPane = new JScrollPane(debugLog);
        scrollPane.setPreferredSize(new Dimension(2000, 200));  // Volle Breite, flach
        scrollPane.setFocusable(false);  // Auch ScrollPane nicht fokussierbar
        scrollPane.setBorder(null);
        
        frame.add(drawPanel, BorderLayout.CENTER);
        frame.add(scrollPane, BorderLayout.SOUTH);  // Unten statt rechts
        frame.setVisible(true);
        log("JFrame visible, title: '" + frame.getTitle() + "'");
        
        // Zeichen-Canvas - VOLLE BREITE, WEISSER Hintergrund
        log("Creating canvas...");
        canvas = new BufferedImage(2000, 1200, BufferedImage.TYPE_INT_ARGB);  // Volle Breite
        canvasG = canvas.createGraphics();
        canvasG.setColor(Color.WHITE);  // WEISS!
        canvasG.fillRect(0, 0, 2000, 1200);
        
        // Weißen Hintergrund sofort anzeigen
        drawPanel.repaint();
        
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
            
            // Track active touch position
            if (point.state != FastTouch.State.UP) {
                activeTouches.put(point.id, new Point(point.x, point.y));
            } else {
                activeTouches.remove(point.id);
                touchColors.remove(point.id);
            }
            
            // Canvas löschen (weiß) und alle aktiven Touches neu zeichnen - KEIN TRAIL!
            canvasG.setColor(Color.WHITE);
            canvasG.fillRect(0, 0, 2000, 1200);
            
            // Alle aktiven Touches als große Kreise zeichnen
            for (Map.Entry<Integer, Point> entry : activeTouches.entrySet()) {
                int tid = entry.getKey();
                Point pos = entry.getValue();
                Color tcolor = touchColors.get(tid);
                if (tcolor == null) continue;
                
                canvasG.setColor(Color.BLACK);
                canvasG.setStroke(new BasicStroke(5));  // Dickerer Ring
                
                // KREIS 3x SO GROSS
                int size = Math.max(60, point.pressure);  // 3x größer (war 20, jetzt 60)
                canvasG.drawOval(pos.x - size/2, pos.y - size/2, size, size);
                
                // Mittelpunkt
                canvasG.fillOval(pos.x - 5, pos.y - 5, 10, 10);
            }
            
            drawPanel.repaint();
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
