package mibi.segmentation;

import java.util.Arrays;

import static org.bytedeco.opencv.global.opencv_core.*;

public abstract class CV_FLOAT {


    public static double getResolutionForDType(int dtype){
        if (FLOAT32.isDtype(dtype)){
            return FLOAT32.resolution;
        } else if (FLOAT64.isDtype(dtype)){
            return FLOAT64.resolution;
        }
        return 0;
    }

    public static boolean isDtype(int type){
        return Arrays.stream(FLOAT32.opencvDtypes).anyMatch(e -> e == type) ||
                Arrays.stream(FLOAT64.opencvDtypes).anyMatch(e -> e == type);
    }

    public static class FLOAT32 extends CV_FLOAT {
        public static final int precision = 6;
        public static final int[] opencvDtypes = new int[]{CV_32F, CV_32FC1, CV_32FC2, CV_32FC3, CV_32FC4};
        public static final double resolution = Math.pow(10, -1 * precision);
    }

    public static class FLOAT64 extends CV_FLOAT {
        public static final int precision = 15;
        public static final int[] opencvDtypes = new int[]{CV_64F, CV_64FC1, CV_64FC2, CV_64FC3, CV_64FC4};
        public static final double resolution = Math.pow(10, -1 * precision);
    }

}
