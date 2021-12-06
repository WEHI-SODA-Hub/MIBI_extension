package mibi.segmentation.DeepCellToolbox;


import org.bytedeco.opencv.opencv_core.Mat;
//import org.bytedeco.opencv.presets.opencv_imgproc;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Size;

import java.util.List;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_ELLIPSE;
import static org.bytedeco.opencv.global.opencv_imgproc.MORPH_RECT;

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
}
