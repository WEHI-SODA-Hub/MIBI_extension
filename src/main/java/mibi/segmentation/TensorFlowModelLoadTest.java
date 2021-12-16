package mibi.segmentation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.bytedeco.opencv.opencv_core.*;
import org.tensorflow.Tensor;
import qupath.ext.tensorflow.TensorFlowTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ops.DeepCellPreprocessOp;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//import static qupath.lib.scripting.QP.getCurrentImageData;


public class TensorFlowModelLoadTest implements Runnable  {
    QuPathGUI quPathGUI;
    public TensorFlowModelLoadTest(QuPathGUI quPathGUI){
        this.quPathGUI = quPathGUI;
    }

    @Override
    public void run() {
        createDialog().show();
    }

    public Stage createDialog(){
        Stage stage = new Stage();
        GridPane grid = new GridPane();
        Button button = new Button("Run");
        button.setOnAction(e -> {

            var imageData = quPathGUI.getImageData();

            var deep = DeepCell2D.builder()
                    .padding(32)
                    .nucleiChannel(21)
                    .membraneChannels(29, 32)
                    .minNucleiArea(100)
                    .measureIntensity()
                    .measureShape()
                    .build();
            var cells = deep.detectObjects(imageData, (ROI) null);
            imageData.getHierarchy().addPathObjects(cells);
        });
        grid.add(button, 0,0);
        stage.setScene(new Scene(grid));
        return stage;
    }
}
