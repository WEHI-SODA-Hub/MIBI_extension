package qupath.opencv.ops;

import ij.process.ImageProcessor;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Range;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;
//import org.opencv.core.CvType;
import qupath.imagej.tools.IJTools;
import qupath.imagej.tools.PixelImageIJ;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.OpenCVTools;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.bytedeco.opencv.global.opencv_core.*;

/**
 * Adjustments made to Pete's ImageOps to extract nuclei channel and concatenate membrane channels for DeepCell.
 */
public class DeepCellImageOps {

    public static ImageDataOp buildImageDataOps(ImageData<BufferedImage> imageData, ColorTransform nucleiChannel, ColorTransform... membraneChannels) throws IOException {
        if (imageData == null || nucleiChannel == null || membraneChannels.length == 0)
            return new ImageOps.DefaultImageDataOp(null);
        else {

            List<Mat> mats = new ArrayList<>();
            DeepCellChannelImageDataOp deepCellChannelImageDataOp = new DeepCellChannelImageDataOp(null, nucleiChannel, membraneChannels);

            var img_nuc = imageData.getServer().readBufferedImage(RegionRequest.createInstance(imageData.getServer()));
            Mat mat_nuc = new Mat(img_nuc.getHeight(), img_nuc.getWidth(), opencv_core.CV_32FC1);
            extractChannel(img_nuc, imageData.getServer(), nucleiChannel, mat_nuc);
            var nucleiMedian = getNonZeroMedian(mat_nuc);

            int n = membraneChannels.length;
            var membraneMedians = new double[n];

            Mat concatMembrane = new Mat(img_nuc.getHeight(), img_nuc.getWidth(), opencv_core.CV_32FC1);
            var img_mem = imageData.getServer().readBufferedImage(RegionRequest.createInstance(imageData.getServer()));
            for (int i = 0; i < n; i++) {
                var colTrans = membraneChannels[i];
                Mat mat_mem = new Mat(img_nuc.getHeight(), img_nuc.getWidth(), opencv_core.CV_32FC1);
                extractChannel(img_mem, imageData.getServer(), colTrans, mat_mem);
                membraneMedians[i] = getNonZeroMedian(mat_mem);
                medianNormalise(mat_mem, membraneMedians[i]).copyTo(mat_mem);
                if (i==0){
                    mat_mem.copyTo(concatMembrane);
                } else{
                    opencv_core.add(mat_mem, concatMembrane, concatMembrane);
                }
            }
            var concatMedian = getNonZeroMedian(concatMembrane);

//            String path1 = "/Users/yokote.k/Desktop/MIBI/model_output/input_nuc_.tif";
//            opencv_imgcodecs.imwrite(path1, mat_nuc);

//            String path2 = "/Users/yokote.k/Desktop/MIBI/model_output/input_mem_.tif";
//            opencv_imgcodecs.imwrite(path2, concatMembrane);

//            medianNormalise(mat_nuc, nucleiMedian).copyTo(mat_nuc);
            medianNormalise(concatMembrane, concatMedian).copyTo(concatMembrane);

            mats.add(mat_nuc);
            mats.add(concatMembrane);
            var mat = OpenCVTools.mergeChannels(mats, null);

            String path11 = "/Users/yokote.k/Desktop/MIBI/model_output/input_nuc_median_norm.tif";
            opencv_imgcodecs.imwrite(path11, mat_nuc);

            String path12 = "/Users/yokote.k/Desktop/MIBI/model_output/input_mem_median_norm.tif";
            opencv_imgcodecs.imwrite(path12, concatMembrane);

//            mat = DeepCellPreprocessOp.preprocess().apply(mat);

            deepCellChannelImageDataOp.setInputMat(mat);
            Dialogs.showInfoNotification("", "Is empty: " + mat.empty());


            var matvec = new MatVector();
            opencv_core.split(mat, matvec);

            String path21 = "/Users/yokote.k/Desktop/MIBI/model_output/input_nuc_preproc.tif";
            opencv_imgcodecs.imwrite(path21, matvec.get(0));

            String path22 = "/Users/yokote.k/Desktop/MIBI/model_output/input_mem_preproc.tif";
            opencv_imgcodecs.imwrite(path22, matvec.get(1));

            return deepCellChannelImageDataOp;
        }
    }

    static class DeepCellChannelImageDataOp implements ImageDataOp{

        private ColorTransform[] membraneChannels;
        private ColorTransform nucleiChannel;
        private ImageOp op;

        private Mat inputMat;
        DeepCellChannelImageDataOp(ImageOp op, ColorTransform nucleiChannel, ColorTransform... membraneChannels){
            this.op = op;
            this.membraneChannels = membraneChannels.clone();
            this.nucleiChannel = nucleiChannel;
        }

        DeepCellChannelImageDataOp(ImageOp op, ColorTransform nucleiChannel, Mat inputMat, ColorTransform... membraneChannels){
            this.op = op;
            this.membraneChannels = membraneChannels.clone();
            this.nucleiChannel = nucleiChannel;
            this.inputMat = inputMat.clone();
        }

        /**
         * Extracts the nuclei channel from the image and concatenates the membrane channels by element-wise
         * multiplication.
         * @param imageData
         * @param regionRequest
         * @return
         * @throws IOException
         */
        @Override
        public Mat apply(ImageData<BufferedImage> imageData, RegionRequest regionRequest) throws IOException {
            BufferedImage img;
            if (op == null)
                img = imageData.getServer().readBufferedImage(regionRequest);
            else
                img = ServerTools.getPaddedRequest(imageData.getServer(), regionRequest, op.getPadding());

            Range rowRange = new Range(regionRequest.getMinY(), regionRequest.getMaxY());
            Range colRange = new Range(regionRequest.getMinX(), regionRequest.getMaxX());
            Mat mat = new Mat(regionRequest.getHeight(), regionRequest.getWidth(), opencv_core.CV_32FC1);
            inputMat.apply(rowRange, colRange).copyTo(mat);
            mat = DeepCellPreprocessOp.preprocess().apply(mat);
            return mat;
        }



        @Override
        public boolean supportsImage(ImageData<BufferedImage> imageData) {
            if (!nucleiChannel.supportsImage(imageData.getServer()))
                return false;

            for (var t : membraneChannels) {
                if (!t.supportsImage(imageData.getServer()))
                    return false;
            }
            return true;
        }

        @Override
        public List<ImageChannel> getChannels(ImageData<BufferedImage> imageData) {
            var channels = new ArrayList<ImageChannel>();
            channels.add(ImageChannel.getInstance(nucleiChannel.getName(), null));
            channels.addAll(
                    Arrays.stream(membraneChannels)
                                    .map(c -> ImageChannel.getInstance(c.getName(), null))
                                    .collect(Collectors.toList())
                    );
            if (op == null)
                return channels;
            else
                return op.getChannels(channels);
        }

        @Override
        public ImageDataOp appendOps(ImageOp... ops) {
            if (ops.length == 0)
                return this;
            if (this.op == null)
                return new DeepCellChannelImageDataOp(ImageOps.Core.sequential(ops), nucleiChannel, inputMat, membraneChannels);
            var allOps = new ArrayList<ImageOp>();
            allOps.add(op);
            allOps.addAll(Arrays.asList(ops));
            var newOp = ImageOps.Core.sequential(allOps);
            return new DeepCellChannelImageDataOp(newOp, nucleiChannel, inputMat, membraneChannels);
        }

        @Override
        public PixelType getOutputType(PixelType pixelType) {
            if (op == null)
                return pixelType;
            return op.getOutputType(pixelType);
        }

        @Override
        public Collection<URI> getUris() throws IOException {
            return null;
        }

        // Not used
        @Override
        public boolean updateUris(Map<URI, URI> map) throws IOException {
            return false;
        }


        public void setInputMat(Mat inputMat) {
            this.inputMat = inputMat.clone();
        }

    }

    private static void extractChannel(BufferedImage img, ImageServer<BufferedImage> server, ColorTransform t, Mat mat){
        float[] pixels = null;
        pixels = t.extractChannel(server, img, null);
        try (FloatIndexer idx = mat.createIndexer()) {
            idx.put(0L, pixels);
        }
    }

    public static Mat medianNormalise(Mat mat, double median){


        // need to error handle
        // TODO: Something went wrong
        assert median != 0;

        Scalar scalar = new Scalar(median);
        Mat medianMat = new Mat(mat.rows(), mat.cols(), opencv_core.CV_32FC1, scalar);

        opencv_core.divide(mat, medianMat, mat, 1, CV_32FC1);
        return mat;
    }


    public static double getNonZeroMedian(Mat mat){

        ImageProcessor ip = OpenCVTools.matToImageProcessor(mat);

        var width = ip.getWidth();
        var height = ip.getHeight();

        DescriptiveStatistics ds = new DescriptiveStatistics();
        for (int x=0; x<width; x++){
            for(int y=0; y<height; y++){
                var val = ip.getf(x,y);
                if (val > 0){
                    ds.addValue(val);
                }
            }
        }
        return ds.getPercentile(50);
    }




}
