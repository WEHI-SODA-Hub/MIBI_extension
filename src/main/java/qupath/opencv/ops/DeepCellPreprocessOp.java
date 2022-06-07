package qupath.opencv.ops;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.opencv.tools.OpenCVTools;

import static org.bytedeco.opencv.global.opencv_core.*;

/**
 * Applies the preprocessing steps for Mesmer.
 * Since CLAHE can only be applied to CV_16UC1, we must apply it before the percentile threshold.
 * This may have affects on performance later on.
 */
public class DeepCellPreprocessOp implements ImageOp {

    public static DeepCellPreprocessOp preprocess(){
        return new DeepCellPreprocessOp();
    }


    @Override
    public Mat apply(Mat input) {

        var matvec = new MatVector();
        opencv_core.split(input, matvec);

        CLAHE clahe = opencv_imgproc.createCLAHE();

        // Pointers to store the min and max
        DoublePointer min = new DoublePointer(1);
        DoublePointer max = new DoublePointer(1);

        for (int c = 0; c < matvec.size(); c++) {

            // extract channel
            var mat = matvec.get(c);


            var ip = OpenCVTools.matToImageProcessor(mat);

            int width = ip.getWidth();
            int height = ip.getHeight();

            DescriptiveStatistics ds = new DescriptiveStatistics();

            for (int i=0; i < width; i++){
                for (int j=0; j < height; j++){
                    var val = ip.getf(i,j);
                    if (val > 0 )
                        ds.addValue(ip.getf(i,j));
                }
            }

//            var percentile99dot9 = ds.getPercentile(99.9);
//            // The 100 is ignored. Does not matter for THRESH_TRUNC
//            opencv_imgproc.threshold(mat, mat, percentile99dot9, 255, opencv_imgproc.THRESH_TRUNC);

//            opencv_core.minMaxLoc(mat, min, max, null, null, null);
//            mat.convertTo(mat, mat.type(), 1 / (max.get()), 0);

            // convert for clahe compatibility
            mat.convertTo(mat, CV_16UC1);
            clahe.apply(mat, mat);

            // convert for qupath compatibility
            mat.convertTo(mat, CV_32F);

            opencv_core.minMaxLoc(mat, min, max, null, null, null);
            mat.convertTo(mat, mat.type(), 1 / (max.get()), 0);

//            opencv_imgproc.GaussianBlur(mat, mat, new Size(0,0), 1, 1, BORDER_REFLECT);

            matvec.put(c, mat);
        }
        opencv_core.merge(matvec, input);
        return input;

    }
}

