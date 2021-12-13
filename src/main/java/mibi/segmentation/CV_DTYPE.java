package mibi.segmentation;

import java.util.Arrays;
import java.util.stream.IntStream;

import static org.bytedeco.opencv.global.opencv_core.*;

public class CV_DTYPE {




    public enum CV_FLOAT {
        FLOAT32(6, new int[]{CV_32F, CV_32FC1, CV_32FC2, CV_32FC3, CV_32FC4}),
        FLOAT64(15, new int[]{CV_64F, CV_64FC1, CV_64FC2, CV_64FC3, CV_64FC4});

        private int precision = 0;
        private int[] types;

        CV_FLOAT(int precision, int[] types) {
            this.precision = precision;
            this.types = types;
        }

        public int getPrecision() {
            return precision;
        }

        public int[] getTypes() {
            return types;
        }

        public double getResolution() {
            return Math.pow(10, -1 * precision);
        }

        public boolean isType(int type){
            for (int i : getTypes()){
                if (i == type)
                    return true;
            }
            return false;
        }

        public static double getResolution(int type){
           for (CV_FLOAT e : CV_FLOAT.values()){
               if (e.isType(type)){
                   return e.getResolution();
               }
           }
           return -1;
        }

        public static boolean isDType(int type){
            for (CV_FLOAT e : CV_FLOAT.values()){
                if (e.isType(type)){
                    return true;
                }
            }
            return false;
        }

    }

    public enum CV_SHORT {
        SHORT16(new int[] {CV_16UC1, CV_16UC2, CV_16UC3, CV_16UC4});
//        SHORT32(new int[] {CV_32UC1, CV_32UC2, CV_32SC3, CV_32SC4});

        private int[] types;
        CV_SHORT (int[] types){
            this.types = types;
        }

        public int[] getTypes() {
            return types;
        }

        public static boolean isDType(int type){
            for (CV_SHORT e : CV_SHORT.values()){
                for (int i : e.getTypes()){
                    if (i == type)
                        return true;
                }
            }
            return false;
        }

    }
}