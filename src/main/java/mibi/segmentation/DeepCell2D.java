package mibi.segmentation;

import ij.gui.Roi;
import ij.process.ImageProcessor;
import mibi.segmentation.DeepCellToolbox.DeepWatershed;
import org.bytedeco.javacpp.PointerScope;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.index.strtree.STRtree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.proto.framework.SignatureDef;
import org.tensorflow.proto.framework.TensorInfo;

import qupath.ext.tensorflow.TensorFlowTools;
import qupath.imagej.processing.RoiLabeling;
import qupath.imagej.tools.IJTools;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.lib.analysis.features.ObjectMeasurements.*;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.*;
import qupath.lib.images.servers.ColorTransforms.ColorTransform;
import qupath.lib.objects.CellTools;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ops.*;
import qupath.opencv.tools.OpenCVTools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.bytedeco.opencv.global.opencv_core.extractChannel;

//TODO: add in logger
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
    private boolean measureShape;
    private int nThreads = -1;
    private boolean constrainToParent = true;

    private double pixelSize;
    private int minNucleiArea;
    private int tileWidth = 256;
    private int tileHeight = 256;
    private int pad =0;
    private ImageDataOp op;
    private double cellExpansion;
    private double cellConstrainScale = Double.NaN;
    private static final int defaultTileSize = 256;

    private static int iter=0;
    /**
     * Detects Cells objects for the given imageData and roi
     * @param imageData The image to be segmented
     * @param roi The region of interest within the image
     */
    public List<PathObject> detectObjects(ImageData<BufferedImage> imageData, ROI roi){

        var resolution = imageData.getServer().getPixelCalibration();
        if (Double.isFinite(pixelSize) && pixelSize > 0) {
            double downsample = pixelSize / resolution.getAveragedPixelSize().doubleValue();
            resolution = resolution.createScaledInstance(downsample, downsample);
        }
        int tw = tileWidth <= 0 ? defaultTileSize : tileWidth;
        int th = tileHeight <= 0 ? defaultTileSize : tileWidth;

        // applies any of the image operations to get the ROI
        var opServer = ImageOps.buildServer(
                imageData,
                op,
                resolution,
                tw - pad*2,
                th - pad*2);

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

        roi = ROIs.createRectangleROI(0,0,imageData.getServer().getWidth(), imageData.getServer().getHeight(), ImagePlane.getDefaultPlane());
        var plane = roi.getImagePlane();

        if (tiles.size() > 1)
            log("Detecting nuclei for {} tiles", tiles.size());
        else
            log("Detecting nuclei");



        List<PotentialCell> allCells = new ArrayList<>();

        for (TileRequest t: tiles) {
            if (mask == null || mask.intersects(GeometryTools.createRectangle(t.getImageX(), t.getImageY(), t.getImageWidth(), t.getImageHeight()))) {

                allCells.addAll(Objects.requireNonNull(detectObjectsForTile(
                        op,
                        dnn,
                        imageData,
                        t.getRegionRequest(),
                        tiles.size() > 1,
                        mask
                )));


            }
        }
        allCells = filterCells(allCells);

        // Filter by size
        if (minNucleiArea > 0){
            allCells = allCells.stream().filter(c -> c.getNucleus().getArea() > minNucleiArea).collect(Collectors.toList());
        }

        // TODO: is cell expansion even necessary??
        List<PathObject> cells = allCells.stream().map(t -> PathObjects.createCellObject(
                GeometryTools.geometryToROI(t.getMembrane(), plane),
                GeometryTools.geometryToROI(t.getNucleus(), plane),
                null,
                null)
        ).collect(Collectors.toList());




        // There should be no cell overlaps TODO: add in cell expansion

        // Add shape measurements, if needed
        if (measureShape)
            cells.parallelStream().forEach(c -> ObjectMeasurements.addShapeMeasurements(c, cal));

        // Add intensity measurements, if needed
        if (!cells.isEmpty() && !measurements.isEmpty()) {
            log("Making measurements");
            var stains = imageData.getColorDeconvolutionStains();
            var builder = new TransformedServerBuilder(server);
            if (stains != null) {
                List<Integer> stainNumbers = new ArrayList<>();
                for (int s = 1; s <= 3; s++) {
                    if (!stains.getStain(s).isResidual())
                        stainNumbers.add(s);
                }
                builder.deconvolveStains(stains, stainNumbers.stream().mapToInt(i -> i).toArray());
            }

            var server2 = builder.build();
            double downsample = resolution.getAveragedPixelSize().doubleValue() / cal.getAveragedPixelSize().doubleValue();

            cells.parallelStream().forEach(cell -> {
                try {
                    ObjectMeasurements.addIntensityMeasurements(server2, cell, downsample, measurements, compartments);
                } catch (IOException e) {
                    log(e.getLocalizedMessage(), e);
                }
            });

        }

        log("Detected {} cells", cells.size());

        return CellTools.constrainCellOverlaps(cells);
    }



    private boolean cancelRuns = false;
    //TODO: Haven't added in the percentile thresholding which is in deepcell
    private List<PotentialCell> detectObjectsForTile(ImageDataOp op,
                                      SavedModelBundle model,
                                      ImageData<BufferedImage> imageData,
                                      RegionRequest regionRequest,
                                      boolean excludeOnBounds,
                                      Geometry mask
    ) {

        // TODO: Work out what this is for
        if (Thread.currentThread().isInterrupted())
            cancelRuns = true;
        if (cancelRuns)
            Collections.emptyList();

        // List of potential Nuclei
        List<PotentialCell> nuclei;

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
                return null;
            }

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

            Mat matCell = new Mat();
            Mat matNuclear = new Mat();
            postProcess(formattedOutput, matCell, matNuclear);

            List<PotentialCell> cells = null;
            try {
                cells = convertLabelsToCells(
                        imageData.getServer(),
                        matCell,
                        matNuclear,
                        requestPadded,
                        padding
                );
            } catch(IOException e){
                e.printStackTrace();
            }

            return cells;
        }
    }

    // TODO: Check for which compartment user wants
    // This will process the raw outputs of the model into labelled images
    private void postProcess(HashMap<String, List<Mat>> formattedOutput, Mat matCell, Mat matNuclear){

        var deepWatershed = DeepWatershed.builder().build();

        deepWatershed.deepWatershed(formattedOutput.get(wholeCellKey), matCell);
        deepWatershed.deepWatershed(formattedOutput.get(nuclearKey), matNuclear);
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
        var maximaCell = TensorFlowTools.convertToMats(output.get(0)).get(0);
        var interiorCell = TensorFlowTools.convertToMats(output.get(1)).get(0);
        var maximaNuclear = TensorFlowTools.convertToMats(output.get(2)).get(0);
        var interiorNuclear = TensorFlowTools.convertToMats(output.get(3)).get(0);

        // Extract the necessary channel from interiors.
        opencv_core.extractChannel(interiorCell, interiorCell, 1);
        opencv_core.extractChannel(interiorNuclear, interiorNuclear, 1);

        HashMap<String, List<Mat>> map = new HashMap<>();

        var wholeCell = new ArrayList<Mat>();
        wholeCell.add(maximaCell);
        wholeCell.add(interiorCell);
        map.put(wholeCellKey, wholeCell);

        var nuclear = new ArrayList<Mat>();
        nuclear.add(maximaNuclear);
        nuclear.add(interiorNuclear);
        map.put(nuclearKey, nuclear);

        return map;
    }

    // TODO: Include "downsample" as a variable
    // TODO: FIX THIS!!!!!!!!
    private List<PotentialCell> convertLabelsToCells(
            ImageServer<BufferedImage> server,
            Mat cellLabels,
            Mat nucleiLabels,
            RegionRequest request,
            Padding padding
    ) throws IOException {

        Mat croppedCell = new Mat();
        Mat croppedNuclei = new Mat();

        cropImage(cellLabels, croppedCell, padding);
        cropImage(nucleiLabels, croppedNuclei, padding);

        var pathImage = IJTools.convertToImagePlus(server, request);
        ImageProcessor cellProcessor = DeepWatershed.toImageProcessor(croppedCell);
        ImageProcessor nucleiProcessor = DeepWatershed.toImageProcessor(croppedNuclei);

        var cal = pathImage.getPixelCalibration();

        var factory = GeometryTools.getDefaultFactory();
        var precisionModel = factory.getPrecisionModel();

        int n_cell = (int) cellProcessor.getStatistics().max;
        int n_nuclei = (int) nucleiProcessor.getStatistics().max;
        ArrayList<Roi> roisIJ_membrane;
        ArrayList<Roi> roisIJ_nuclei;
        try {
            roisIJ_membrane = new ArrayList<>(Arrays.asList(RoiLabeling.labelsToConnectedROIs(cellProcessor, n_cell)));
            roisIJ_nuclei = new ArrayList<>(Arrays.asList(RoiLabeling.labelsToConnectedROIs(nucleiProcessor, n_nuclei)));
        }
        catch(NullPointerException e){
            System.out.println("Null");
            return null;
        }
            // The detections
            List<PotentialCell> detections = new ArrayList<>();
            var cpt = 0;

            // Iterate through the membrane rois
            for (var nucl : roisIJ_nuclei) {
                if (nucl == null) {
                    continue;
                }

//                roi.setLocation(roi.getXBase() + request.getX() * request.getDownsample(), roi.getYBase() + request.getY() * request.getDownsample());
                var roi_nuclei = IJTools.convertToROI(
                        nucl,
                        0,
                        0,
                        request.getDownsample(),
                        request.getPlane());

                AtomicReference<ROI> roi_membrane = new AtomicReference<>();
                AtomicReference<Double> min_diff_X = new AtomicReference<>(Double.MAX_VALUE);
                AtomicReference<Double> min_diff_Y = new AtomicReference<>(Double.MAX_VALUE);

                roisIJ_membrane.forEach(roi -> {
                            if (roi != null) {

//                                nucl.setLocation(nucl.getXBase() + request.getX() * request.getDownsample(), nucl.getYBase() + request.getY() * request.getDownsample());
                                var membrane = IJTools.convertToROI(
                                        roi,
                                        0,
                                        0,
                                        request.getDownsample(),
                                        request.getPlane());

                                var X = membrane.getCentroidX();
                                var Y = membrane.getCentroidY();
                                var diff_X = X - (roi_nuclei.getCentroidX());
                                var diff_Y = Y - (roi_nuclei.getCentroidY());

                                if (Math.abs(diff_X) < 2 && Math.abs(diff_Y) < 2){
                                    roi_membrane.set(membrane);
                                }

                                if ((Math.abs(diff_X) < min_diff_X.get()) && (Math.abs(diff_Y) < min_diff_Y.get())) {
                                    //&&  {
//                                    roi_nuclei.set(nuclei);
                                    min_diff_X.set(Math.abs(diff_X));
                                    min_diff_Y.set(Math.abs(diff_Y));
                                }

                            }
                        }
                );

                Geometry geomCell;
                // TODO: Log the error for this membrane
                if (roi_membrane.get() == null) {
                    geomCell = CellTools.estimateCellBoundary(
                            GeometryTools.roiToGeometry(roi_nuclei),
                            cellExpansion * 2,
                            1);
                } else{
                    roisIJ_membrane.remove(roi_membrane.get());
                    geomCell = CellTools.estimateCellBoundary(
                            GeometryTools.roiToGeometry(roi_membrane.get()),
                            cellExpansion,
                            1);
                }
                int z = 0;
                int t = 0;
                var plane = ImagePlane.getPlane(z, t);
                var roiCell = GeometryTools.geometryToROI(geomCell, plane);
                var detection = new PotentialCell(roiCell.translate(request.getX(), request.getY()).getGeometry(),
                        roi_nuclei.translate(request.getX(), request.getY()).getGeometry());

                detections.add(detection);
            }



            // Exclude anything that overlaps the right/bottom boundary of a region

            var iter = detections.iterator();
            while (iter.hasNext()) {
                var n = iter.next();
                var env = n.membrane.getEnvelopeInternal();
                if (env.getMaxX() >= request.getMaxX()
                        || env.getMaxY() >= request.getMaxY()
                        || env.getMinX() <= request.getMinX()
                        || env.getMinY() <= request.getMinY()
                ) {
                    if (env.getMinX() > 0 && env.getMinY() > 0 && env.getMaxX() < server.getWidth() && env.getMaxY() < server.getHeight())
                        iter.remove();
                }
            }


            return filterCells(detections);
//            return detections;
    }


    private void cropImage(Mat src, Mat dst, Padding padding){
        var x1 = padding.getX1();
        var x2 = padding.getX2();
        var y1 = padding.getY1();
        var y2 = padding.getY2();
        OpenCVTools.crop(src, x1, y1, src.cols()-x2-x1, src.rows()-y2-y1).copyTo(dst);
    }

    private List<PotentialCell> filterCells(List<PotentialCell> potentialCells){
        // Sort in descending order comparing the total area of the cell
        potentialCells.sort(Comparator.comparingDouble(PotentialCell::getFullArea).reversed());

        // Create array of nuclei to keep & to skip
        var cells = new LinkedHashSet<PotentialCell>();
        var skippedCells = new HashSet<PotentialCell>();
        int skipErrorCount = 0;

        // Create a spatial cache to find overlaps more quickly
        // (Because of later tests, we don't need to update envelopes even though geometries may be modified)
        Map<PotentialCell, Envelope> envelopes = new HashMap<>();
        var tree = new STRtree();


        for (var cell : potentialCells) {
            var env = cell.getMembrane().getEnvelopeInternal();
            envelopes.put(cell, env);
            tree.insert(env, cell);
        }

        for (var cell : potentialCells) {
            if (skippedCells.contains(cell))
                continue;

            cells.add(cell);
            var envelope = envelopes.get(cell);

            // We have done this for intersecting membranes
            @SuppressWarnings("unchecked")
            var overlaps = (List<PotentialCell>)tree.query(envelope);
            for (var cell2 : overlaps) {
                if (cell2 == cell || skippedCells.contains(cell2) || cells.contains(cell2))
                    continue;

                // If we have an overlap, retain the higher-probability nucleus only (i.e. the one we met first)
                // Try to refine other nuclei
                try {
                    var env = envelopes.get(cell2);
                    if (envelope.intersects(env) && cell.nucleus.intersects(cell2.nucleus)) {
                        // Retain the nucleus only if it is not fragmented, or less than half its original area
                        var difference = cell2.nucleus.difference(cell.nucleus);
                        if (difference instanceof Polygon && difference.getArea() > cell2.nucleus.getArea() / 2.0)
                            cell2.nucleus = difference;
                        else {
                            skippedCells.add(cell2);
                        }
                    }
                } catch (Exception e) {
                    skippedCells.add(cell2);
                    skipErrorCount++;
                }

            }
        }
        if (skipErrorCount > 0) {
            int skipCount = skippedCells.size();
            logger.warn("Skipped {} nucleus detection(s) due to error in resolving overlaps ({}% of all skipped)",
                    skipErrorCount, GeneralTools.formatNumber(skipErrorCount*100.0/skipCount, 1));
        }
        return new ArrayList<>(cells);
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


    private static class PotentialCell {

        private Geometry nucleus;
        private Geometry membrane;
        private double fullArea;

        PotentialCell(Geometry membrane, Geometry nucleus) {
            this.nucleus = nucleus;
            this.membrane = membrane;
            if (membrane != null) {
                this.fullArea = membrane.getArea();
            }
        }

        double getFullArea(){
            return fullArea;
        }

        Geometry getNucleus(){return nucleus; }
        Geometry getMembrane(){return membrane; }

        void setNucleus(Geometry nucleus){
            this.nucleus = nucleus;
        }

        void setMembrane(Geometry membrane){
            this.membrane = membrane;
        }

    }

    public static Builder builder(){
        return new Builder();
    }

    public static class Builder{

        private static String[] pathNames = { "src", "main", "resources", "model", "MultiplexSegmentation" };
        private File modelPath = new File(String.join(File.separator, pathNames));

        private List<ImageOp> ops = new ArrayList<>();
        private boolean doLog = false;

        private ColorTransform nucleiChannel;
        private ColorTransform[] membraneChannels = new ColorTransform[0];

        private boolean ignoreCellOverlaps = false;

        private int minNucleiArea = -1;


        private boolean constrainToParent = false;
        private int nThreads = -1;
        private List<Measurements> measurements;
        private boolean measureShape = true;
        private List<Compartments> compartments = Arrays.asList(Compartments.values());
        private double cellExpansion = 3;
        private double cellConstrainScale;
        private double pixelSize = Double.NaN;
        private int pad = 64;



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

        public Builder nucleiChannel(int channel){
            this.nucleiChannel = ColorTransforms.createChannelExtractor(channel);
            return this;
        }

        public Builder nucleiChannel(String channel){
            this.nucleiChannel = ColorTransforms.createChannelExtractor(channel);
            return this;
        }



        /**
         * Specify channels. Useful for detecting nuclei for one channel
         * within a multi-channel image, or potentially for trained models that
         * support multi-channel input.
         * @param channels 0-based indices of the channels to use
         * @return this builder
         */
        public Builder membraneChannels(int... channels) {
            return membraneChannels(Arrays.stream(channels)
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
        public Builder membraneChannels(String... channels) {
            return membraneChannels(Arrays.stream(channels)
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
        public Builder membraneChannels(ColorTransform... channels) {
            this.membraneChannels = channels.clone();
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

        /**
         * Minimum nuclei area in pixel^2
         * @param minNucleiArea
         * @return
         */
        public Builder minNucleiArea(int minNucleiArea){
            this.minNucleiArea = minNucleiArea;
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
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(TensorInfo::getName))
                    .collect(Collectors
                            .toCollection(ArrayList::new));
            // Make sure the model has the correct number of outputs
            assert deepCell2D.outputTensorInfo.size() == 4;



            mergedOps.add(ImageOps.Core.ensureType(PixelType.FLOAT32));
            mergedOps.add(DeepCellPreprocessOp.preprocess());
            // ImageOps.buildImageDataOp(channels) is the operation to extract the channels
            // Also includes any other image operations the user has specified
            deepCell2D.op = DeepCellImageOps.buildImageDataOps(nucleiChannel, membraneChannels)
                    .appendOps(mergedOps.toArray(ImageOp[]::new));

            deepCell2D.dnn = dnn;
            deepCell2D.pixelSize = pixelSize;
            deepCell2D.cellConstrainScale = cellConstrainScale;
            deepCell2D.cellExpansion = cellExpansion;

            deepCell2D.minNucleiArea = minNucleiArea;
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
