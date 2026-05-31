package fasttouch.demos;

import fasttouch.FastTouch;

public class ConsoleDemo {
    public static void main(String[] args) throws Exception {
        // 1. Force the console window title using ANSI escape codes
        String windowTitle = "FastTouch Console Demo " + System.currentTimeMillis();
        System.out.print("\033]0;" + windowTitle + "\007");
        
        // Wait a moment for Windows to process the title change
        Thread.sleep(200);
        
        System.out.println("==========================================");
        System.out.println("FastTouch Console Demo");
        System.out.println("==========================================\n");
        System.out.println("Attempting to hook into Console Window...");
        
        // 2. Initialize FastTouch by finding the console window
        FastTouch touch = FastTouch.create(windowTitle);
        
        System.out.println("\nSUCCESS! Touch Engine attached to terminal!");
        System.out.println("Please touch the console window...");
        
        // 3. Register a Touch Listener
        touch.addListener(point -> {
            // We use ANSI to move the cursor down below the header and print the event
            // Note: Since multi-touch fires very fast, we just print the stream
            if (point.state == FastTouch.State.DOWN) {
                System.out.printf("[DOWN] ID: %d | Pos: %d,%d | Pressure: %d | Size: %dx%d\n", 
                    point.id, point.x, point.y, point.pressure, point.width, point.height);
            } else if (point.state == FastTouch.State.UP) {
                System.out.printf("  [UP] ID: %d\n", point.id);
            }
        });
        
        // 4. Start polling and keep the thread alive
        touch.start();
        
        while (true) {
            Thread.sleep(100);
        }
    }
}
