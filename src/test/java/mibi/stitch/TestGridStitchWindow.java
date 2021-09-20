package mibi.stitch;

import javafx.application.Application;
import javafx.stage.Stage;

public class TestGridStitchWindow extends Application {

    public static void main(String... args){
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        GridStitchWindow window = new GridStitchWindow();
        window.createStage();
        primaryStage = window.getStage();
        primaryStage.show();
    }
}
