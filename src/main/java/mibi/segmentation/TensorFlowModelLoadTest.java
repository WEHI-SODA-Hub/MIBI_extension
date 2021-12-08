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
            mergedOps.add(DeepCellPreprocessOp.preprocess());

            var channels_ind = new String[] {"dsDNA", "Beta-Tubulin"};
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
//                System.out.println();
                Mat mat = op.apply(imageData, request);
                Tensor tensor = TensorFlowTools.convertToTensor(mat);
                String modelPath = "/Users/yokote.k/Desktop/MIBI/MultiplexSegmentation";

                var model = SavedModelBundle.load(modelPath);
                Session sess = model.session();

                var out = sess.runner().feed("serving_default_input_0:0", tensor)
                        .fetch("StatefulPartitionedCall:0")
                        .fetch("StatefulPartitionedCall:1")
                        .fetch("StatefulPartitionedCall:2")
                        .fetch("StatefulPartitionedCall:3")
                        .run();

                var maximaC = TensorFlowTools.convertToMats(out.get(0)).get(0);
                var interiorC = TensorFlowTools.convertToMats(out.get(1)).get(0);
                extractChannel(interiorC, interiorC, 1);

                var maximaN = TensorFlowTools.convertToMats(out.get(2)).get(0);
                var interiorN = TensorFlowTools.convertToMats(out.get(3)).get(0);
                extractChannel(interiorN, interiorN, 1);


                List<Mat> wholeCellList = new ArrayList<>();
                wholeCellList.add(maximaC);
                wholeCellList.add(interiorC);

                List<Mat> nuclearList = new ArrayList<>();
                nuclearList.add(maximaN);
                nuclearList.add(interiorN);


                Mat labelledImWholeCell = new Mat();
                var deepWatershed = DeepWatershed.builder().build();
                deepWatershed.deepWatershed(wholeCellList, labelledImWholeCell);

                Mat labelledImNuclear = new Mat();
                deepWatershed.deepWatershed(nuclearList, labelledImNuclear);

//                // Blur
//                // Format interior
//                extractChannel(interior, interior, 1);
//                GaussianBlur(interior, interior, new Size(0,0), 2, 2, BORDER_REFLECT);
//
//                // Get the imreconstruct
//                int radius = 2;
//                Mat local_max = new Mat();
////                DeepWatershed.hMaxima(maxima, local_max, 0.1, 1, true);
//
//                String localMaxPath = "/Users/yokote.k/Desktop/MIBI/model_output/local_max.tiff";
//                opencv_imgcodecs.imwrite(localMaxPath, local_max);
//
//                Mat mask1 = new Mat();
//                opencv_imgproc.threshold(interior, mask1, 0.3, 1, CV_THRESH_BINARY);
//
//
//
////                DeepWatershed.watershed(interior, local_max, labelledIm, mask1);

                String cellsPath = "/Users/yokote.k/Desktop/MIBI/model_output/cells.tiff";
                opencv_imgcodecs.imwrite(cellsPath, labelledImWholeCell);

                String nuclearPath = "/Users/yokote.k/Desktop/MIBI/model_output/nuclei.tiff";
                opencv_imgcodecs.imwrite(nuclearPath, labelledImNuclear);

//                Mat channel1 = new Mat();
//                interior.copyTo(channel1);
//                Mat channel2 = new Mat();
//                interior.copyTo(channel2);
//                MatVector matvec = new MatVector();
//                matvec.put(channel1, channel2, interior);
//                opencv_core.merge(matvec, interior);


//                opencv_imgproc.watershed(interior, labels);
//                ProcessingCV.doWatershed(interior, labels, true);

//                String labelsPath = "/Users/yokote.k/Desktop/MIBI/model_output/labels.tiff";
//                opencv_imgcodecs.imwrite(labelsPath, labels);

//                opencv_imgcodecs.imwrite(subtractedPath, subtracted);
//                opencv_imgcodecs.imwrite(reconstructedPath , maxima);


//                Mat blurredMaxima = new Mat();
//                Mat blurredInterior = new Mat();
//
//                double interior_smooth = 2;
//
//
////                opencv_imgproc.GaussianBlur(maxima, blurredMaxima, new Size(0,0), maxima_smooth, maxima_smooth, BORDER_REFLECT);
//                opencv_imgproc.GaussianBlur(interior, blurredInterior, new Size(0,0), interior_smooth, interior_smooth, BORDER_REFLECT);
//
//                System.out.println(blurredInterior.channels());

//                System.out.println(output0.size());


            } catch (IOException ioException) {
                ioException.printStackTrace();
            }

        });
        grid.add(button, 0,0);
        stage.setScene(new Scene(grid));
        return stage;
    }
}
