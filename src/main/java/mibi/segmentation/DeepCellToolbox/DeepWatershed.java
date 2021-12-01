package mibi.segmentation.DeepCellToolbox;


import org.bytedeco.opencv.opencv_core.Mat;

import java.util.List;

public class DeepWatershed {


    private static final String[] possibleAlgos = new String[]{"h_maxima", "peak_local_max"};

    private double radius;
    private double maxima_threshold;
    private double interior_threshold;
    private double maxima_smooth;
    private double interior_smooth;
    private double maxima_index;
    private double interior_index;
    private double label_erosion;
    private double small_objects_threshold;
    private double fill_holes_threshold;
    private String maxima_algorithm;

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
        private double radius = 2;

        private int maxima_index = 0;
        private int interior_index = 1;

        private double label_erosion = 0;
        // Will ignore pixel_expansion
//        private double pixel_expansion;
        private String maxima_algorithm = "h_maxima";



        public Builder radius(double radius){
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
        private Builder h_maxima_algorithm(String maxima_algorithm){
            this.maxima_algorithm = maxima_algorithm;
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

            if (this.maxima_algorithm == possibleAlgos[0] || this.maxima_algorithm == possibleAlgos[1]){
                deepWatershed.maxima_algorithm = this.maxima_algorithm;
            } else {
                throw new RuntimeException("Unrecognised maxima algorithm " + this.maxima_algorithm);
            }
            return deepWatershed;
        }
    }
}
