package mibi.segmentation;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.bytedeco.opencv.opencv_core.Mat;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

//import static qupath.lib.scripting.QP.getCurrentImageData;
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
            int tileWidth = 256;
            int tileHeight = 256;
            ROI roi = null;

            var padding = Padding.symmetric(0);
            List<ImageOp> ops = new ArrayList<>();
            List<ImageOp> mergedOps = new ArrayList<>(ops);
            ImageDataOp op;

            var imageData = quPathGUI.getImageData();
            var resolution = imageData.getServer().getPixelCalibration();
            mergedOps.add(ImageOps.Core.ensureType(PixelType.FLOAT32));

            var channels_ind = new String[] {"2021-08-04_11MH0691_stitched-nuclei.tif", "2021-08-04_11MH0691_stitched-panck.tiff"};
            var channels = Arrays.stream(channels_ind)
                    .map(ColorTransforms::createChannelExtractor)
                    .toArray(ColorTransforms.ColorTransform[]::new);

            op = ImageOps.buildImageDataOp(channels)
                    .appendOps(mergedOps.toArray(ImageOp[]::new));

            var opServer = ImageOps.buildServer(imageData, op, resolution, tileWidth, tileHeight);
            var request = RegionRequest.createInstance(opServer);
            var tiles = opServer.getTileRequestManager().getTileRequests(request);
            var mask = roi == null ? null : roi.getGeometry();

            try {
                Mat mat = op.apply(imageData, request);
                Tensor tensor = TensorFlowTools.convertToTensor(mat);
                String modelPath = "/Users/yokote.k/Desktop/MIBI/MultiplexSegmentation";

                var model = SavedModelBundle.load(modelPath);
                Session sess = model.session();

                var out = sess.runner().feed("serving_default_input_0:0", tensor).fetch("StatefulPartitionedCall:0").fetch("StatefulPartitionedCall:1").fetch("StatefulPartitionedCall:2").fetch("StatefulPartitionedCall:3").run();
//                var model = TensorFlowTools.createDnnModel(modelPath);
//                var output = model.getPredictionFunction().predict(tensor);
                System.out.println(out.get(0).shape());
                System.out.println(out.get(1).shape());
                System.out.println(out.get(2).shape());
                System.out.println(out.get(3).shape());
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }

        });
        grid.add(button, 0,0);
        stage.setScene(new Scene(grid));
        return stage;
    }
}
