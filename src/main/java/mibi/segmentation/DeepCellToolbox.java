package mibi.segmentation;


import org.bytedeco.opencv.opencv_core.Mat;
import org.tensorflow.Tensor;
import qupath.lib.regions.Padding;

import java.util.List;

public class DeepCellToolbox {




    public static Object deepWatershed(List<Tensor> input, SegmentationArgs args){

        if (input.size() != 2){
            // TODO: throw exception
        }
        return null;
    }






    /**
     * Class for the kwargs in deepcell watershed.
     */
    static class SegmentationArgs{

        private String compartment;
        private double maxima_threshold = 0.1;
        private double maxima_smooth = 0;
        private double interior_threshold = 0.3;
        private double interior_smooth = 2;
        private double small_objects_threshold = 15;
        private double fill_holes_threshold = 15;
        private double radius = 2;

        static class Builder{
            private String compartment;
            private double maxima_threshold = 0.1;
            private double maxima_smooth = 0;
            private double interior_threshold = 0.3;
            private double interior_smooth = 2;
            private double small_objects_threshold = 15;
            private double fill_holes_threshold = 15;
            private double radius = 2;

            public Builder compartment(String compartment){
                this.compartment = compartment;
                return this;
            }

            public Builder maxima_threshold(double maxima_threshold){
                this.maxima_threshold = maxima_threshold;
                return this;
            }

            public Builder maxima_smooth(double maxima_smooth){
                this.maxima_smooth = maxima_smooth;
                return this;
            }

            public Builder interior_threshold(double interior_threshold){
                this.interior_threshold = interior_threshold;
                return this;
            }

            public Builder interior_smooth(double interior_smooth){
                this.interior_smooth = interior_smooth;
                return this;
            }

            public Builder small_objects_threshold(double small_objects_threshold){
                this.small_objects_threshold = small_objects_threshold;
                return this;
            }

            public Builder fill_holes_threshold(double fill_holes_threshold){
                this.fill_holes_threshold = fill_holes_threshold;
                return this;
            }

            public Builder radius(double radius){
                this.radius = radius;
                return this;
            }


            public SegmentationArgs build(){
                SegmentationArgs seg = new SegmentationArgs();
                seg.compartment = compartment;
                seg.maxima_threshold = maxima_threshold;
                seg.maxima_smooth = maxima_smooth;
                seg.interior_threshold = interior_threshold;
                seg.interior_smooth = interior_smooth;
                seg.small_objects_threshold = small_objects_threshold;
                seg.fill_holes_threshold = fill_holes_threshold;
                seg.radius = radius;
                return seg;
            }

        }

    }

}
