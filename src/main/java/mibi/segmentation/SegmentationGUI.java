package mibi.segmentation;

import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class SegmentationGUI implements Runnable {

    private Stage stage;
    private final String TITLE = "DeepCell Cell Segmentation";


    @Override
    public void run() {
        if (stage == null){
            createStage();
        }
        stage.show();
    }


    private void createStage(){

        stage = new Stage();
        stage.resizableProperty().set(false);
        stage.setTitle(TITLE);

        GridPane gridPane = new GridPane();




    }
}
