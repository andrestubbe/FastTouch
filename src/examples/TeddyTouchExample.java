package examples.multitouch;

import javax.swing.*;
import java.awt.*;

/**
 * Example showing how to use TouchEnabledPanel with your Teddy class.
 * IMPORTANT: Remove mouse listeners when using touch support.
 */
public class TeddyTouchExample {

    static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Teddy Touch Example");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

            // Use TouchEnabledPanel instead of regular JPanel
            TouchEnabledPanel touchPanel = new TouchEnabledPanel();
            touchPanel.setBackground(Color.WHITE);

            // Set up your custom touch listener
            touchPanel.setTouchListener(new TouchListener() {
                @Override
                public void onTouchDown(int touchId, int x, int y) {
                    System.out.println("Teddy touched at: " + x + ", " + y);
                    // Add your Teddy-specific touch handling here
                }

                @Override
                public void onTouchMove(int touchId, int x, int y) {
                    // Handle touch movement for Teddy
                }

                @Override
                public void onTouchUp(int touchId) {
                    System.out.println("Teddy touch released: " + touchId);
                }

                @Override
                public void onTouchCountChanged(int activeTouchCount) {
                    System.out.println("Active touches on Teddy: " + activeTouchCount);
                }
            });

            frame.setContentPane(touchPanel);
            frame.setSize(800, 600);
            frame.setLocationRelativeTo(null);

            // Enable touch support
            touchPanel.enableTouchSupport(frame);

            frame.setVisible(true);
        });
    }
}
