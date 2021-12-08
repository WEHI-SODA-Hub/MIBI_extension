package mibi.segmentation.DeepCellToolbox;


import ij.process.*;
import inra.ijpb.binary.conncomp.FloodFillComponentsLabeling;
import inra.ijpb.watershed.MarkerControlledWatershedTransform2D;
import mibi.segmentation.CV_DTYPE;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.javacpp.ShortPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

import inra.ijpb.morphology.Reconstruction;

import java.util.ArrayList;
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
    private double max_value = 1;

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

    private double subtractive_factor = 0.001;

    /**
     * Deep watershed segmentation in deepcell_toolbox, however, with some changes.
     * @param input
     * @param result
     * @return
     */
    public void deepWatershed(List<Mat> input, Mat result){

        // Check that we have two images
        if (input.size() != 2){
            throw new RuntimeException("Expected 2 inputs but got "+input.size());
        }

        // extract the two images
        var maxima = input.get(0);
        var interior = input.get(1);

        if (!CV_DTYPE.CV_FLOAT.isDType(maxima.type()) || !CV_DTYPE.CV_FLOAT.isDType(interior.type())){
            throw new RuntimeException("One or both of the images are not of type. Must be CV_32F or CV_64F. \n"
            + "Images are of the type: " + maxima.type() + ", " + interior.type()
            );
        }

        // Check that images have same dimensions
        if (maxima.rows() != interior.rows() || maxima.cols() != interior.cols()) {
            throw new RuntimeException("The inputs must have the same dimensions");
        }

        // TODO: Not entirely sure whether to have a similar loop as the "line 155 for maxima, interior
        //  in zip(maximas, interiors):" in the deep_watershed.py

        // Blur the images if required
        if (maxima_smooth > 0) {
            opencv_imgproc.GaussianBlur(
                    maxima,
                    maxima,
                    new Size(0, 0), maxima_smooth, maxima_smooth, BORDER_REFLECT);
        }
        if (interior_smooth > 0) {
            opencv_imgproc.GaussianBlur(
                    interior,
                    interior,
                    new Size(0, 0), interior_smooth, interior_smooth, BORDER_REFLECT);
        }


        if (pixel_expansion > 0){
            // TODO: Currently no 3D support. Only 2D hence default is square
            opencv_imgproc.dilate(
                    interior,
                    interior,
                    opencv_imgproc.getStructuringElement(
                            MORPH_RECT,
                            new  Size(2*pixel_expansion + 1, 2*pixel_expansion+1)));
        }

        // local maxima
        Mat local_max = new Mat();
        hMaxima(maxima, local_max, maxima_threshold, max_value, true);

        // mask for the area which is definitely a cell
        double new_interior_threshold = interior_threshold;
        Mat mask = new Mat();
        opencv_imgproc.threshold(interior, mask, new_interior_threshold, max_value, CV_THRESH_BINARY);

        // Run watershed
        watershed(interior, local_max, result, mask);

        // Deallocate memory
        local_max.deallocate();
        mask.deallocate();
    }

    // method for calling the MorphlibJ watershed
    private void watershed(Mat input, Mat maxima, Mat result, Mat mask){

        // multiply input by -1
        opencv_core.multiply(input, new Mat(input.size(), input.type(), new Scalar(-1)), input);

        var input_ij = makeFloatProcessor(input);
        var maxima_ij = makeFloatProcessor(maxima);
        var mask_ij = makeFloatProcessor(mask);

        // create labels
        var marker_ij = new FloodFillComponentsLabeling().computeLabels(maxima_ij);

        // apply watershed
        var watershedTransform = new MarkerControlledWatershedTransform2D(input_ij, marker_ij, mask_ij).applyWithPriorityQueueAndDams();
        toMat((ShortProcessor) watershedTransform).copyTo(result);
    }

    // Method which calculates the H maxima of the image
    private void hMaxima(Mat image, Mat localMaxima, double h, double maxVal, boolean safe) {

        if (!CV_DTYPE.CV_FLOAT.isDType(image.type())){
            //TODO: Catch wrong input image
            return;
        }

        // resolution of the image type
        var resolution = new Mat();
        var res_scalar = 2 * CV_DTYPE.CV_FLOAT.getResolution(image.type());
        opencv_core.multiply(opencv_core.abs(image).asMat(),
                new Mat(image.size(), image.type(),  new Scalar(res_scalar)),
                resolution
        );

        // Shifted image to be reconstructed
        var shifted_im = new Mat();
        opencv_core.subtract(image, resolution, image);
        opencv_core.subtract(image, new Mat(image.size(), image.type(), new Scalar(h)), shifted_im);

        // Morphological Reconstruction
        var rec_img = new Mat();
        image.copyTo(rec_img);
        var marker = makeFloatProcessor(shifted_im);
        var mask = makeFloatProcessor(rec_img);
        var reconstructed = Reconstruction.reconstructByDilation(marker, mask);
        rec_img = toMat((FloatProcessor) reconstructed);

        // Local Maxima
        var res_img = new Mat();
        opencv_core.subtract(image, rec_img, res_img);

        // TODO: Potentially dodgey. No reason this should work but it does
        if (safe){
            h = h - h * subtractive_factor;
        }

        // get the maximas
        opencv_imgproc.threshold(res_img, localMaxima, h, maxVal, CV_THRESH_BINARY);

        // Deallocate memory
        res_img.deallocate();
        shifted_im.deallocate();
        rec_img.deallocate();
        resolution.deallocate();
    }

    /**
     * Builder
     * @return Builder
     */
    public static Builder builder(){
        return new Builder();
    }

    /**
     * Builder for DeepWatershed
     */
    public static class Builder{

        private String compartment;
        private double maxima_threshold = 0.1;
        private double maxima_smooth = 0;
        private double interior_threshold = 0.3;
        private double interior_smooth = 2;
        private double small_objects_threshold = 15;
        private double fill_holes_threshold = 15;
        private int radius = 2;

        private double subtractive_factor;

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
        private Builder subtractive_factor(double subtractive_factor){
            this.subtractive_factor = subtractive_factor;
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


    /**
     * Directly copied from https://github.com/joheras/IJ-OpenCV
     * @param ip
     * @return
     */
    public static Mat toMat(ImageProcessor ip) {
        Mat mat = null;
        if (ip instanceof ShortProcessor) {
            mat = toMat((ShortProcessor) ip);
        } else if (ip instanceof FloatProcessor) {
            mat = toMat((FloatProcessor) ip);
        } else {
            throw new IllegalArgumentException("cannot convert to Mat: " + ip);
        }
        return mat;
    }

    private static Mat toMat(FloatProcessor cp) {
        final int w = cp.getWidth();
        final int h = cp.getHeight();
        final float[] pixels = (float[]) cp.getPixels();
        return new Mat(h, w, opencv_core.CV_32FC1, new FloatPointer(pixels));
    }

    private static Mat toMat(ShortProcessor sp) {
        final int w = sp.getWidth();
        final int h = sp.getHeight();
        final short[] pixels = (short[]) sp.getPixels();
        return new Mat(h, w, opencv_core.CV_16UC1, new ShortPointer(pixels));
    }

    /**
     * Converts Mat to imageJ ImageProcessor
     * @param mat
     * @return
     */
    public static ImageProcessor toImageProcessor(Mat mat) {
        final int type = mat.type();
        ImageProcessor result = null;

        if (CV_DTYPE.CV_SHORT.isDType(type)) {	// signed short image
            result = makeShortProcessor(mat);
        } else if (CV_DTYPE.CV_FLOAT.isDType(type)) {	// float image
            result = makeFloatProcessor(mat);
        } else {
            throw new IllegalArgumentException("cannot convert Mat of type " + type);
        }
        return result;
    }

    private static ShortProcessor makeShortProcessor(Mat mat) {

        final int w = mat.cols();
        final int h = mat.rows();
        ShortProcessor sp = new ShortProcessor(w, h);
        ShortPointer sptr = new ShortPointer(mat.data());
        sptr.get((short[]) sp.getPixels());
        sptr.close();
        return sp;
    }

    private static FloatProcessor makeFloatProcessor(Mat mat) {

        final int w = mat.cols();
        final int h = mat.rows();
        FloatProcessor fp = new FloatProcessor(w, h);
        FloatPointer fptr = new FloatPointer(mat.data());
        fptr.get((float[]) fp.getPixels());
        fptr.close();
        return fp;
    }
}
