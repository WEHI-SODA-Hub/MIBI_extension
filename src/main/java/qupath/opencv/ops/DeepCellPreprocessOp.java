package qupath.opencv.ops;

import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;

import static org.bytedeco.opencv.global.opencv_core.CV_16UC1;
import static org.bytedeco.opencv.global.opencv_core.CV_32F;

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

            // convert for clahe compatibility
            mat.convertTo(mat, CV_16UC1);
            clahe.apply(mat, mat);

            // convert for qupath compatibility
            mat.convertTo(mat, CV_32F);

            opencv_core.minMaxLoc(mat, min, max, null, null, null);
            mat.convertTo(mat, mat.type(), 1 / (max.get()-min.get()), -1 * min.get() / (max.get()-min.get()));

        }
        opencv_core.merge(matvec, input);
        return input;

    }
}

