package mibi.segmentation;

import mibi.segmentation.DeepCellToolbox.DeepWatershed;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.proto.framework.SignatureDef;
import org.tensorflow.proto.framework.TensorInfo;
import qupath.ext.tensorflow.TensorFlowTools;
import qupath.lib.analysis.features.ObjectMeasurements.*;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;

import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ops.ImageDataOp;
import qupath.opencv.ops.ImageOp;
import qupath.opencv.ops.ImageOps;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

//TODO: Ignore pooling for now. Focus on getting a working output.
/**
 * This is a Tensorflow DeepCell2D implementation HEAVILY inspired by Pete Bankhead's
 * StarDist2D.java using the tensorflow extension.
 */
public class DeepCell2D {
    private final static Logger logger = LoggerFactory.getLogger(DeepCell2D.class);
    private static final String signature = "serving_default";

    private static String[] pathNames = { "src", "main", "resources", "model", "MultiplexSegmentation" };
    private final String modelPath = new File(String.join(File.pathSeparator, pathNames)).getAbsolutePath();

    private SignatureDef signatureDef;
    private TensorInfo inputTensorInfo;
    private List<TensorInfo> outputTensorInfo;

    private SavedModelBundle dnn;
    private Collection<Compartments> compartments;
    private Collection<Measurements> measurements;

    private boolean ignoreCellOverlaps;
    private boolean measureShape = false;
    private int nThreads = -1;
    private boolean constrainToParent = true;

    private double pixelSize;
    private int tileWidth = 256;
    private int tileHeight = 256;
    private int pad =0;
    private ImageDataOp op;
    private double cellExpansion = Double.NaN;
    private double cellConstrainScale = Double.NaN;
    private static final int defaultTileSize = 256;
    /**
     * Detects Cells objects for the given imageData and roi
     * @param imageData The image to be segmented
     * @param roi The region of interest within the image
     */
    public void detectObjects(ImageData<BufferedImage> imageData, ROI roi){

        var resolution = imageData.getServer().getPixelCalibration();
        if (Double.isFinite(pixelSize) && pixelSize > 0) {
            double downsample = pixelSize / resolution.getAveragedPixelSize().doubleValue();
            resolution = resolution.createScaledInstance(downsample, downsample);
        }
        int tw = tileWidth <= 0 ? defaultTileSize : tileWidth;
        int th = tileHeight <= 0 ? defaultTileSize : tileWidth;

        // applies any of the image operations to get the ROI
        var opServer = ImageOps.buildServer(imageData, op, resolution, tw - pad*2, th - pad*2);

        RegionRequest request;
        if (roi == null)
            request = RegionRequest.createInstance(opServer);
        else
            request = RegionRequest.createInstance(
                    opServer.getPath(),
                    opServer.getDownsampleForResolution(0),
                    roi);

        // Run DeepCell on tiled image
        var tiles = opServer.getTileRequestManager().getTileRequests(request);
        var mask = roi == null ? null : roi.getGeometry();

        // Detect all potential nuclei
        var server = imageData.getServer();
        var cal = server.getPixelCalibration();
        double expansion = cellExpansion / cal.getAveragedPixelSize().doubleValue();
        assert roi != null;
        var plane = roi.getImagePlane();

        if (tiles.size() > 1)
            log("Detecting nuclei for {} tiles", tiles.size());
        else
            log("Detecting nuclei");
        var nuclei = tiles.parallelStream()
                .filter(t -> mask == null || mask.intersects(GeometryTools.createRectangle(t.getImageX(), t.getImageY(), t.getImageWidth(), t.getImageHeight())))
                .flatMap(t -> detectObjectsForTile(op, dnn, imageData, t.getRegionRequest(), tiles.size() > 1, mask).stream())
                .collect(Collectors.toList());
    }

    private boolean cancelRuns = false;
    //TODO: Haven't added in the percentile thresholding which is in deepcell
    private List<PotentialNucleus> detectObjectsForTile(ImageDataOp op,
                                                        SavedModelBundle model,
                                                        ImageData<BufferedImage> imageData,
                                                        RegionRequest regionRequest,
                                                        boolean excludeOnBounds,
                                                        Geometry mask) {

        // TODO: Work out what this is for
        if (Thread.currentThread().isInterrupted())
            cancelRuns = true;
        if (cancelRuns)
            Collections.emptyList();

        // List of potential Nuclei
        List<PotentialNucleus> nuclei;

        // Create a mask around pixels we can use
        var regionMask = GeometryTools.createRectangle(regionRequest.getX(), regionRequest.getY(),
                                                regionRequest.getWidth(), regionRequest.getHeight());
        if (mask == null)
            mask = regionMask;
        else
            mask = GeometryTools.attemptOperation(mask, m -> m.intersection(regionMask));

        // Create a padded request, if we need one
        RegionRequest requestPadded = regionRequest;
        if (pad > 0) {
            double downsample = regionRequest.getDownsample();
            var server = imageData.getServer();
            int x1 = (int)Math.max(0, Math.round(regionRequest.getX() - downsample * pad));
            int y1 = (int)Math.max(0, Math.round(regionRequest.getY() - downsample * pad));
            int x2 = (int)Math.min(server.getWidth(), Math.round(regionRequest.getMaxX() + downsample * pad));
            int y2 = (int)Math.min(server.getHeight(), Math.round(regionRequest.getMaxY() + downsample * pad));
            requestPadded = RegionRequest.createInstance(server.getPath(), downsample, x1, y1, x2-x1, y2-y1);
        }

        try (@SuppressWarnings("unchecked")
         var scope = new PointerScope()) {
            Mat mat;
            try {
                mat = op.apply(imageData, requestPadded);
            } catch (IOException e) {
                logger.error(e.getLocalizedMessage(), e);
                return Collections.emptyList();
            }

            // Calculate image width & height.
            // These need to be consistent with the expected maximum number of pooling operations
            // to avoid shape problems.
//            int expectedPooling = 6; // A generous estimate (usually 3 or 4 expected)
//            int multiple = (int) Math.pow(2, expectedPooling);
//            int tw = (int) Math.ceil(mat.cols() / (double) multiple) * multiple;
//            int th = (int) Math.ceil(mat.rows() / (double) multiple) * multiple;
//
            // Ensure we have a Mat of the right size. If not, create a padding around to make a mat of the correct size
            var padding = ensureSize(mat, tileWidth, tileHeight, opencv_core.BORDER_REFLECT);

            // Convert the mat to a tensor
            var input = TensorFlowTools.convertToTensor(mat);

            // Start a session.
            var sess = model.session();
            List<Tensor> output;
            output = sess.runner()
                    .feed(inputTensorInfo.getName(), input)
                    .fetch(outputTensorInfo.get(0).getName())
                    .fetch(outputTensorInfo.get(1).getName())
                    .fetch(outputTensorInfo.get(2).getName())
                    .fetch(outputTensorInfo.get(3).getName())
                    .run();

            var formattedOutput = formatOutput(output);
            var labelledImages = postProcess(formattedOutput);
        }

        return null;
    }


    // TODO: Check for which compartment user wants
    // This will process the raw outputs of the model into labelled images
    private Object postProcess(HashMap<String, List<Mat>> formattedOutput){

        var deepWatershed = DeepWatershed.builder().build();
        var wholeCellMat = deepWatershed.deepWatershed(formattedOutput.get(wholeCellKey));
        var nuclearMat = deepWatershed.deepWatershed(formattedOutput.get(nuclearKey));

        return null;
    }


    // Formats the raw output of the Mesmer model
    private static final String wholeCellKey = "whole-cell";
    private static final String nuclearKey = "nuclear";

    private HashMap<String, List<Mat>> formatOutput(List<Tensor> output){
        final int expectedOutput = 4;

        if (output.size() != expectedOutput){
            throw new RuntimeException("Expected 4 outputs from model but got "+output.size());
        }

        // Convert all the Tensors to Opencv Mats
        var output1 = TensorFlowTools.convertToMats(output.get(0)).get(0);
        var output2 = TensorFlowTools.convertToMats(output.get(1)).get(0);
        var output3 = TensorFlowTools.convertToMats(output.get(2)).get(0);
        var output4 = TensorFlowTools.convertToMats(output.get(3)).get(0);

        HashMap<String, List<Mat>> map = new HashMap<>();

        var wholeCell = new ArrayList<Mat>();
        wholeCell.add(output1);
        wholeCell.add(output2);
        map.put(wholeCellKey, wholeCell);

        var nuclear = new ArrayList<Mat>();
        nuclear.add(output3);
        nuclear.add(output4);
        map.put(nuclearKey, nuclear);

        return map;
    }

    // Ensures that the tile is the correct size. If not if will add a padding to make it
    // the correct size.
    private static Padding ensureSize(Mat mat, int width, int height, int borderType) {
        int x1 = 0, x2 = 0, y1 = 0, y2 = 0;
        int w = mat.cols();
        int h = mat.rows();

        Padding padding = Padding.empty();

        boolean pad = false;
        if (w < width) {
            x1 = (width - w) / 2;
            x2 = (width - w - x1);
            w = width;
            pad = true;
        }
        if (h < height) {
            y1 = (height - h) / 2;
            y2 = (height - h - y1);
            h = height;
            pad = true;
        }
        if (pad) {
            opencv_core.copyMakeBorder(mat, mat, y1, y2, x1, x2, borderType);
            padding = Padding.getPadding(x1, x2, y1, y2);
        }
        if (w != width && h != height)
            opencv_imgproc.resize(mat, mat, new Size(width, height));
        return padding;
    }


    private boolean doLog = true;
    private void log(String msg, Object... args){
        if (doLog)
            logger.info(msg, args);
        else
            logger.debug(msg, args);
    }


    private static class PotentialNucleus {

        private Geometry geometry;
        private double fullArea;
        private double probability;
        private int classification;

        PotentialNucleus(Geometry geom, double prob, int classification) {
            this.geometry = geom;
            this.probability = prob;
            this.classification = classification;
            this.fullArea = geom.getArea();
        }
        double getProbability() {
            return probability;
        }
        int getClassification() {
            return classification;
        }
    }



    public static class Builder{

        private static String[] pathNames = { "src", "main", "resources", "model", "MultiplexSegmentation" };
        private File modelPath = new File(String.join(File.pathSeparator, pathNames));

        private List<ImageOp> ops = new ArrayList<>();
        private boolean doLog = false;
        private ColorTransform[] channels = new ColorTransform[0];

        private boolean ignoreCellOverlaps = false;

        private boolean constrainToParent = false;
        private int nThreads = -1;
        private List<Measurements> measurements;
        private boolean measureShape = true;
        private List<Compartments> compartments = Arrays.asList(Compartments.values());
        private double cellExpansion;
        private double cellConstrainScale;
        private double pixelSize = Double.NaN;
        private int pad = 32;



        public Builder modelPath(String modelPath){
            this.modelPath = new File(modelPath);
            return this;
        }

        /**
         * Add preprocessing operations, if required.
         * @param ops
         * @return this builder
         */
        public Builder preprocess(ImageOp... ops) {
            for (var op : ops)
                this.ops.add(op);
            return this;
        }

        /**
         * Request that progress is logged. If this is not specified, progress is only logged at the DEBUG level.
         * @return this builder
         */
        public Builder doLog() {
            this.doLog = true;
            return this;
        }


        /**
         * Specify channels. Useful for detecting nuclei for one channel
         * within a multi-channel image, or potentially for trained models that
         * support multi-channel input.
         * @param channels 0-based indices of the channels to use
         * @return this builder
         */
        public Builder channels(int... channels) {
            return channels(Arrays.stream(channels)
                    .mapToObj(ColorTransforms::createChannelExtractor)
                    .toArray(ColorTransform[]::new));
        }

        /**
         * Specify channels by name. Useful for detecting nuclei for one channel
         * within a multi-channel image, or potentially for trained models that
         * support multi-channel input.
         * @param channels 0-based indices of the channels to use
         * @return this builder
         */
        public Builder channels(String... channels) {
            return channels(Arrays.stream(channels)
                    .map(ColorTransforms::createChannelExtractor)
                    .toArray(ColorTransform[]::new));
        }

        /**
         * Define the channels (or color transformers) to apply to the input image.
         * <p>
         * This makes it possible to supply color deconvolved channels, for example.
         * @param channels
         * @return this builder
         */
        public Builder channels(ColorTransform... channels) {
            this.channels = channels.clone();
            return this;
        }

        /**
         * Amount by which to expand detected nuclei to approximate the cell area.
         * Units are the same as for the {@link PixelCalibration} of the input image.
         * <p>
         * Warning! This is rather experimental, relying heavily on JTS and a convoluted method of
         * resolving overlaps using a Voronoi tessellation.
         * <p>
         * In short, be wary.
         * @param distance
         * @return this builder
         */
        public Builder cellExpansion(double distance) {
            this.cellExpansion = distance;
            return this;
        }

        /**
         * Constrain any cell expansion defined using {@link #cellExpansion(double)} based upon
         * the nucleus size. Only meaningful for values &gt; 1; the nucleus is expanded according
         * to the scale factor, and used to define the maximum permitted cell expansion.
         *
         * @param scale
         * @return this builder
         */
        public Builder cellConstrainScale(double scale) {
            this.cellConstrainScale = scale;
            return this;
        }

        /**
         * If true, ignore overlaps when computing cell expansion.
         * @param ignore
         * @return this builder
         */
        public Builder ignoreCellOverlaps(boolean ignore) {
            this.ignoreCellOverlaps = ignore;
            return this;
        }

        /**
         * If true, constrain nuclei and cells to any parent annotation (default is true).
         * @param constrainToParent
         * @return this builder
         */
        public Builder constrainToParent(boolean constrainToParent) {
            this.constrainToParent = constrainToParent;
            return this;
        }

        /**
         * Specify the number of threads to use for processing.
         * If you encounter problems, setting this to 1 may help to resolve them by preventing
         * multithreading.
         * @param nThreads
         * @return this builder
         */
        public Builder nThreads(int nThreads) {
            this.nThreads = nThreads;
            return this;
        }
        /**
         * Request default intensity measurements are made for all available cell compartments.
         * @return this builder
         */
        public Builder measureIntensity() {
            this.measurements = Arrays.asList(
                    Measurements.MEAN,
                    Measurements.MEDIAN,
                    Measurements.MIN,
                    Measurements.MAX,
                    Measurements.STD_DEV);
            return this;
        }

        /**
         * Request shape measurements are made for the detected cell or nucleus.
         * @return this builder
         */
        public Builder measureShape() {
            this.measureShape = true;
            return this;
        }

        /**
         * Specify the compartments within which intensity measurements are made.
         * Only effective if {@link #measureIntensity()} and {@link #cellExpansion(double)} have been selected.
         * @param compartments cell compartments for intensity measurements
         * @return this builder
         */
        public Builder compartments(Compartments...compartments) {
            this.compartments = Arrays.asList(compartments);
            return this;
        }

        /**
         * Resolution at which the cell detection should be run.
         * The units depend upon the {@link PixelCalibration} of the input image.
         * <p>
         * The default is to use the full resolution of the input image.
         * <p>
         * For an image calibrated in microns, the recommended default is approximately 0.5.
         *
         * @param pixelSize
         * @return this builder
         */
        public Builder pixelSize(double pixelSize) {
            this.pixelSize = pixelSize;
            return this;
        }

        /**
         * Amount to pad tiles to reduce boundary artifacts.
         * @param pad padding in pixels; width and height of tiles will be increased by pad x 2.
         * @return this builder
         */
        public Builder padding(int pad) {
            this.pad = pad;
            return this;
        }


        //TODO: Add the percentile threshold included
        // https://github.com/vanvalenlab/deepcell-toolbox/blob/88fa8e5afd68d6d3b87af703bc79a4d099e8ba17/deepcell_toolbox/processing.py
        // for better results using mesmer

        /**
         * Build the instance of the DeepCell2D.
         * @return
         */
        public DeepCell2D build(){

            var deepCell2D = new DeepCell2D();
            var mergedOps = new ArrayList<>(ops);

            // Check that the folder exists
            if (!modelPath.exists()) {
                throw new IllegalArgumentException("I couldn't find the model file " + modelPath.getAbsolutePath());
            }
            
            // Check that it is in SavedModel format 
            if (modelPath.isFile()) {
                throw new IllegalArgumentException("Only supports SavedModel format");
            }

            // Try to load the model
            SavedModelBundle dnn;
            try{
                dnn = SavedModelBundle.load(modelPath.getAbsolutePath());
                logger.debug("Loaded model {} with Tensorflow", modelPath);
            } catch(Exception e){
                logger.error("Unable to load TensorFlow with reflection - are you sure it is available and on the classpath?");
                logger.error(e.getLocalizedMessage(), e);
                throw new RuntimeException("Unable to load StarDist model from " + modelPath, e);
            }

            // Extract the input and output information
            deepCell2D.signatureDef = dnn.metaGraphDef().getSignatureDefMap().get(DeepCell2D.signature);
            deepCell2D.inputTensorInfo = deepCell2D.signatureDef.getInputsMap()
                    .values()
                    .stream()
                    .filter(Objects::nonNull)
                    .findFirst().orElseThrow();

            deepCell2D.outputTensorInfo = deepCell2D.signatureDef.getOutputsMap()
                    .values()
                    .stream()
                    .filter(Objects::nonNull).collect(Collectors
                            .toCollection(ArrayList::new));

            // Make sure the model has the correct number of outputs
            assert deepCell2D.outputTensorInfo.size() == 4;



            mergedOps.add(ImageOps.Core.ensureType(PixelType.FLOAT32));

            // ImageOps.buildImageDataOp(channels) is the operation to extract the channels
            // Also includes any other image operations the user has specified
            deepCell2D.op = ImageOps.buildImageDataOp(channels)
                    .appendOps(mergedOps.toArray(ImageOp[]::new));

            deepCell2D.dnn = dnn;
            deepCell2D.pixelSize = pixelSize;
            deepCell2D.cellConstrainScale = cellConstrainScale;
            deepCell2D.cellExpansion = cellExpansion;

            deepCell2D.pad = pad;
            deepCell2D.ignoreCellOverlaps = ignoreCellOverlaps;
            deepCell2D.measureShape = measureShape;
            deepCell2D.doLog = doLog;
            deepCell2D.nThreads = nThreads;
            deepCell2D.constrainToParent = constrainToParent;

            deepCell2D.compartments = new LinkedHashSet<>(compartments);
            if (measurements != null)
                deepCell2D.measurements = new LinkedHashSet<>(measurements);
            else
                deepCell2D.measurements = Collections.emptyList();
            return deepCell2D;
        }
    }

}
