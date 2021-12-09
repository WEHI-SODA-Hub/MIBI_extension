package mibi.segmentation;
import com.mchange.v1.db.sql.UnsupportedTypeException;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import mibi.segmentation.DeepCellToolbox.DeepWatershed;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;
import org.opencv.highgui.HighGui;
import org.opencv.imgcodecs.Imgcodecs;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import qupath.ext.tensorflow.TensorFlowTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.dnn.DnnModel;
import qupath.opencv.dnn.DnnTools;
import org.tensorflow.SavedModelBundle;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.OpenCVTools;
import qupath.opencv.tools.ProcessingCV;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//import static qupath.lib.scripting.QP.getCurrentImageData;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static qupath.lib.gui.scripting.QPEx.getCurrentImageData;

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

            String modelPath = "/Users/yokote.k/Desktop/MIBI/MultiplexSegmentation";
            var imageData = quPathGUI.getImageData();
            var deepCell = DeepCell2D.builder().modelPath(modelPath).channels(22, 0).padding(0).build();
            deepCell.detectObjects(imageData, null);

        });
        grid.add(button, 0,0);
        stage.setScene(new Scene(grid));
        return stage;
    }
}
