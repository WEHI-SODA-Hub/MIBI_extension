package mibi.segmentation.DeepCellToolbox;


import com.mchange.v1.db.sql.UnsupportedTypeException;
import ij.process.FloatProcessor;
import mibi.segmentation.CV_FLOAT;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;
import ijopencv.opencv.MatImagePlusConverter;
import qupath.opencv.tools.ProcessingCV;

import inra.ijpb.morphology.Reconstruction;

import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;


/**
 * Used:
 * https://github.com/joheras/IJ-OpenCV
 * https://imagej.net/plugins/morpholibj
 *
 */

public class DeepWatershed {

    enum MaximaAlgorithms {
        PEAK_LOCAL_MAX,
        H_MAX
    }

    private int radius;
    private double maxima_threshold;
    private double interior_threshold;
    private double maxima_smooth;
    private double interior_smooth;
    private double maxima_index;
    private double interior_index;
    private double label_erosion;
    private double small_objects_threshold;
    private double fill_holes_threshold;
    private int pixel_expansion;
    private MaximaAlgorithms maxima_algorithm;

    public Object deepWatershed(List<Mat> input){

        if (input.size() != 2){
            throw new RuntimeException("Expected 2 inputs but got "+input.size());
        }

        var maxima = input.get(0);
        var interior = input.get(1);

        if (maxima.rows() != interior.rows() || maxima.cols() != interior.cols()) {
            throw new RuntimeException("The inputs must have the same dimensions");
        }

        if(maxima.dims() != 4 || interior.dims() != 4){
            throw new RuntimeException("The inputs do not have the correct dimensions");
        }


        // TODO: Not entirely sure whether to have a similar loop as the "line 155 for maxima, interior in zip(maximas, interiors):"
        // TODO: in the deep_watershed.py

        if (maxima_smooth > 0) {
            opencv_imgproc.GaussianBlur(maxima, maxima, new Size(0, 0), maxima_smooth, maxima_smooth, BORDER_REFLECT);
        }
        if (interior_smooth > 0) {
            opencv_imgproc.GaussianBlur(interior, interior, new Size(0, 0), interior_smooth, interior_smooth, BORDER_REFLECT);
        }

        if (pixel_expansion > 0){

            // TODO: Currently no 3D support. Only 2D hence default is square
            Mat element = opencv_imgproc.getStructuringElement(MORPH_RECT,
                                            new  Size(2*pixel_expansion + 1, 2*pixel_expansion+1));
            opencv_imgproc.dilate(interior, interior, element);
        }


        // TODO: Currently no sophisticated Local Maxima exists within OpenCV nor QuPath. Stick with the simple method
        //  done by Pete. This may not be great for results. Probably have to change

        Mat localMaxima = new Mat();
        Mat strel = opencv_imgproc.getStructuringElement(MORPH_ELLIPSE, new Size(radius, radius));
        opencv_imgproc.dilate(interior, localMaxima, strel);
        compare(interior, localMaxima, localMaxima, CMP_EQ);

        opencv_imgproc.dilate(
                localMaxima,
                localMaxima,
                opencv_imgproc.getStructuringElement(MORPH_ELLIPSE, new Size(radius * 2, radius * 2)));


        return null;
    }



    public static void hMaxima(Mat image, Mat localMaxima, double h, int radius, double maxVal) throws UnsupportedTypeException {

        if (!CV_FLOAT.isDtype(image.type())){
            throw new UnsupportedTypeException("Image not of type FLOAT32 or FLOAT64");
        }


        var resolution = new Mat();
        System.out.println(CV_FLOAT.getResolutionForDType(image.type()));
        opencv_core.multiply(opencv_core.abs(image).asMat(),
                new Mat(image.size(), image.type(),  new Scalar(2 * CV_FLOAT.getResolutionForDType(image.type()))),
                resolution
        );

        var shifted_im = new Mat();
        opencv_core.subtract(image, resolution, image);
        opencv_core.subtract(image, new Mat(image.size(), image.type(), new Scalar(h)), shifted_im);


        var rec_img = new Mat();
        image.copyTo(rec_img);

        var marker = makeFloatProcessor(shifted_im);
        var mask = makeFloatProcessor(rec_img);
        var reconstructed = Reconstruction.reconstructByDilation(marker, mask);

        rec_img = toMat((FloatProcessor) reconstructed);




//        imReconstruct(shifted_im, image, rec_img, radius);
//        ProcessingCV.morphologicalReconstruction(shifted_im, rec_img);



        String reconstructedPath = "/Users/yokote.k/Desktop/MIBI/model_output/reconstructed_imageJ.tiff";
        opencv_imgcodecs.imwrite(reconstructedPath, rec_img);

        var res_img = new Mat();
        opencv_core.subtract(image, rec_img, res_img);

        String subtractedPath = "/Users/yokote.k/Desktop/MIBI/model_output/subtracted.tiff";
        opencv_imgcodecs.imwrite(subtractedPath, res_img);

        opencv_imgproc.threshold(res_img, localMaxima, h, maxVal, CV_THRESH_BINARY);

        res_img.deallocate();
        shifted_im.deallocate();
        rec_img.deallocate();
        resolution.deallocate();
    }




    public static void imReconstruct(Mat marker, Mat mask, Mat reconstructed, int radius){
        Mat strel = opencv_imgproc.getStructuringElement(MORPH_ELLIPSE, new Size(radius * 2+1, radius * 2+1));
        Mat diff = new Mat();
        Mat adjustedMarker = new Mat();
        marker.copyTo(adjustedMarker);


        while (true){
            opencv_imgproc.dilate(adjustedMarker, reconstructed, strel);
            bitwise_and(reconstructed, mask, reconstructed);

            // Stopping criterion
            bitwise_xor(reconstructed, adjustedMarker, diff);
            if(countNonZero(diff) == 0) {
                diff.deallocate();
                adjustedMarker.deallocate();
                return;
            }
            reconstructed.copyTo(adjustedMarker);
        }

    }


    public static Builder builder(){
        return new Builder();
    }

    public static class Builder{

        private String compartment;
        private double maxima_threshold = 0.1;
        private double maxima_smooth = 0;
        private double interior_threshold = 0.3;
        private double interior_smooth = 2;
        private double small_objects_threshold = 15;
        private double fill_holes_threshold = 15;
        private int radius = 2;

        private int maxima_index = 0;
        private int interior_index = 1;

        private double label_erosion = 0;

        private int pixel_expansion = 0;
        private MaximaAlgorithms maxima_algorithm = MaximaAlgorithms.H_MAX;

        public static Builder builder(){
            return new Builder();
        }

        public Builder radius(int radius){
            this.radius = radius;
            return this;
        }

        public Builder maxima_threshold(double maxima_threshold){
            this.maxima_threshold = maxima_threshold;
            return this;
        }

        public Builder interior_threshold(double interior_threshold){
            this.interior_threshold = interior_threshold;
            return this;
        }

        public Builder maxima_smooth(double maxima_smooth){
            this.maxima_smooth = maxima_smooth;
            return this;
        }

        public Builder interior_smooth(double interior_smooth){
            this.interior_smooth = interior_smooth;
            return this;
        }

        public Builder maxima_index(int maxima_index){
            this.maxima_index = maxima_index;
            return this;
        }

        public Builder interior_index(int interior_index){
            this.interior_index = interior_index;
            return this;
        }

        public Builder label_erosion(double label_erosion){
            this.label_erosion = label_erosion;
            return this;
        }

        public Builder small_objects_threshold(double small_objects_threshold){
            this.small_objects_threshold = small_objects_threshold;
            return this;
        }
        private Builder fill_holes_threshold(double fill_holes_threshold){
            this.fill_holes_threshold = fill_holes_threshold;
            return this;
        }
        private Builder h_maxima_algorithm(MaximaAlgorithms maxima_algorithm){
            this.maxima_algorithm = maxima_algorithm;
            return this;
        }
        private Builder pixel_expansion(int pixel_expansion){
            this.pixel_expansion = pixel_expansion;
            return this;
        }

        public DeepWatershed build(){
            var deepWatershed = new DeepWatershed();
            deepWatershed.radius = this.radius;
            deepWatershed.maxima_threshold = this.maxima_threshold;
            deepWatershed.interior_threshold = this.interior_threshold;
            deepWatershed.maxima_smooth = this.maxima_smooth;
            deepWatershed.interior_smooth = this.interior_smooth;
            deepWatershed.maxima_index = this.maxima_index;
            deepWatershed.interior_index = this.interior_index;
            deepWatershed.label_erosion = this.label_erosion;
            deepWatershed.small_objects_threshold = this.small_objects_threshold;
            deepWatershed.fill_holes_threshold = this.fill_holes_threshold;
            deepWatershed.pixel_expansion = pixel_expansion;
            deepWatershed.maxima_algorithm = this.maxima_algorithm;
            return deepWatershed;
        }
    }

    private static FloatProcessor makeFloatProcessor(Mat mat) {
        if (mat.type() != opencv_core.CV_32FC1) {
            throw new IllegalArgumentException("wrong Mat type: " + mat.type());
        }
        final int w = mat.cols();
        final int h = mat.rows();
        FloatProcessor fp = new FloatProcessor(w, h);
        FloatPointer fptr = new FloatPointer(mat.data());
        fptr.get((float[]) fp.getPixels());
        fptr.close();
        return fp;
    }

    public static Mat toMat(FloatProcessor cp) {
        final int w = cp.getWidth();
        final int h = cp.getHeight();
        final float[] pixels = (float[]) cp.getPixels();
        return new Mat(h, w, opencv_core.CV_32FC1, new FloatPointer(pixels));
    }
}
