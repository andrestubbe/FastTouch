package examples.multitouch;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.input.RotateEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.input.ZoomEvent;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class JavaFxTouchDemo extends Application {
	@Override public void start(Stage stage) {
		Pane root = new Pane();
		root.setOnTouchPressed((TouchEvent e) -> {
			System.out.println("touch down: " + e.getTouchCount());
		});
		root.setOnTouchMoved((TouchEvent e) -> {
			// consume to avoid warnings
			if (e != null && e.getEventType() != null) {}
		});
		root.setOnZoom((ZoomEvent e) -> {
			System.out.println("zoom factor: " + e.getZoomFactor());
		});
		root.setOnRotate((RotateEvent e) -> {
			System.out.println("rotate angle: " + e.getAngle());
		});

		stage.setScene(new Scene(root, 800, 600));
		stage.setTitle("JavaFX Multitouch Demo");
		stage.show();
	}

	static void main(String[] args) { launch(args); }
}


