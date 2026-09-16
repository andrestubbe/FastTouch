package fasttouch;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * FastTouch Demo — Real-time interactive multi-touch visualization
 * with native Win32 WM_POINTER pressure and coordinate rendering.
 */
public class Demo {

    private static final Map<Integer, Color> touchColors = new HashMap<>();
    private static final Map<Integer, Point> activeTouches = new HashMap<>();
    private static BufferedImage canvas;
    private static Graphics2D canvasG;
    private static JTextArea debugLog;
    private static final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS");

    public static void main(String[] args) throws Exception {
        log("FastTouch Demo starting...");

        // Create main visualization frame
        JFrame frame = new JFrame("FastTouch Demo");
        frame.setSize(1280, 800);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        JPanel drawPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (canvas != null) {
                    g.drawImage(canvas, 0, 0, null);
                }
            }
        };
        drawPanel.setPreferredSize(new Dimension(1280, 620));
        drawPanel.setBackground(Color.BLACK);

        debugLog = new JTextArea(6, 80);
        debugLog.setEditable(false);
        debugLog.setFocusable(false);
        debugLog.setFont(new Font("Monospaced", Font.PLAIN, 14));
        debugLog.setBackground(new Color(20, 20, 20));
        debugLog.setForeground(Color.GREEN);
        JScrollPane scrollPane = new JScrollPane(debugLog);
        scrollPane.setPreferredSize(new Dimension(1280, 180));
        scrollPane.setBorder(null);

        frame.add(drawPanel, BorderLayout.CENTER);
        frame.add(scrollPane, BorderLayout.SOUTH);
        frame.setVisible(true);

        canvas = new BufferedImage(1280, 620, BufferedImage.TYPE_INT_ARGB);
        canvasG = canvas.createGraphics();
        canvasG.setColor(Color.BLACK);
        canvasG.fillRect(0, 0, 1280, 620);
        drawPanel.repaint();

        // Initialize FastTouch
        log("Initializing native FastTouch...");
        FastTouch touch = FastTouch.create(frame);

        boolean available = FastTouch.isTouchAvailable();
        log("Touchscreen Hardware Detected: " + available);
        log("Max Touch Points Supported: " + FastTouch.getMaxTouchPoints());

        touch.addListener(point -> {
            touchColors.putIfAbsent(point.id, getColorForId(point.id));
            Color color = touchColors.get(point.id);

            log(String.format("[TOUCH] ID=%d Pos=(%d,%d) Pressure=%d Size=%dx%d %s",
                point.id, point.x, point.y, point.pressure, point.width, point.height, point.state));

            if (point.state != FastTouch.State.UP) {
                activeTouches.put(point.id, new Point(point.x, point.y));
            } else {
                activeTouches.remove(point.id);
                touchColors.remove(point.id);
            }

            canvasG.setColor(Color.BLACK);
            canvasG.fillRect(0, 0, 1280, 620);

            for (Map.Entry<Integer, Point> entry : activeTouches.entrySet()) {
                int tid = entry.getKey();
                Point pos = entry.getValue();
                Color tcolor = touchColors.get(tid);
                if (tcolor == null) continue;

                canvasG.setColor(tcolor);
                canvasG.setStroke(new BasicStroke(4));
                int size = Math.max(40, point.pressure);
                canvasG.drawOval(pos.x - size / 2, pos.y - size / 2, size, size);
                canvasG.fillOval(pos.x - 6, pos.y - 6, 12, 12);
            }

            drawPanel.repaint();
        });

        touch.start();
        log("Touch event polling active (~120 Hz). Touch the screen or press ESC to exit.");

        frame.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                    touch.stop();
                    System.exit(0);
                }
            }
        });

        while (frame.isVisible()) {
            drawPanel.repaint();
            Thread.sleep(16);
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
            Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.GREEN,
            Color.ORANGE, Color.PINK, Color.RED, Color.WHITE
        };
        return colors[id % colors.length];
    }
}
