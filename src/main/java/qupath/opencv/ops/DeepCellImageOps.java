package qupath.opencv.ops;

import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOps;
import qupath.opencv.tools.OpenCVTools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Adjustments made to Pete's ImageOps to extract nuclei channel and concatenate membrane channels for DeepCell.
 */
public class DeepCellImageOps {

    public static ImageDataOp buildImageDataOps(ColorTransform nucleiChannel, ColorTransform... membraneChannels){
        if (nucleiChannel == null || membraneChannels.length == 0)
            return new ImageOps.DefaultImageDataOp(null);
        else
            return new DeepCellChannelImageDataOp(null, nucleiChannel, membraneChannels);
    }

    static class DeepCellChannelImageDataOp implements ImageDataOp{

        private ColorTransform[] membraneChannels;
        private ColorTransform nucleiChannel;
        private ImageOp op;

        DeepCellChannelImageDataOp(ImageOp op, ColorTransform nucleiChannel, ColorTransform... membraneChannels){
            this.op = op;
            this.membraneChannels = membraneChannels.clone();
            this.nucleiChannel = nucleiChannel;
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

            var server = imageData.getServer();

            List<Mat> mats = new ArrayList<>();
            Mat concatMembrane = new Mat();

            Mat matNuc = new Mat(img.getHeight(), img.getWidth(), opencv_core.CV_32FC1);;
            extractChannel(img, server, nucleiChannel, matNuc);
            mats.add(matNuc);

            // Multiply together the membrane channels
            for (var t : membraneChannels) {
                Mat mat = new Mat(img.getHeight(), img.getWidth(), opencv_core.CV_32FC1);
                extractChannel(img, server, t, mat);
                if (concatMembrane.empty()){
                    mat.copyTo(concatMembrane);
                } else{
                    opencv_core.multiply(mat, concatMembrane, concatMembrane);
                }
            }
            mats.add(concatMembrane);
            if (mats.size() != 2){
                // TODO: Something went wrong
                return null;
            }

            var mat = OpenCVTools.mergeChannels(mats, null);
            if (op != null) {
                mat = op.apply(mat);
            }
            return mat;
        }

        private void extractChannel(BufferedImage img, ImageServer<BufferedImage> server, ColorTransform t, Mat mat){
            float[] pixels = null;
            pixels = t.extractChannel(server, img, pixels);
            try (FloatIndexer idx = mat.createIndexer()) {
                idx.put(0L, pixels);
            }
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
                return new DeepCellChannelImageDataOp(ImageOps.Core.sequential(ops), nucleiChannel, membraneChannels);
            var allOps = new ArrayList<ImageOp>();
            allOps.add(op);
            allOps.addAll(Arrays.asList(ops));
            var newOp = ImageOps.Core.sequential(allOps);
            return new DeepCellChannelImageDataOp(newOp, nucleiChannel, membraneChannels);
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
        public boolean updateURIs(Map<URI, URI> map) throws IOException {
            return false;
        }

        @Override
        public Collection<URI> getURIs() throws IOException {
            // TODO Auto-generated method stub
            throw new UnsupportedOperationException("Unimplemented method 'getURIs'");
        }
    }

}
