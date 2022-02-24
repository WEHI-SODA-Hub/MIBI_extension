package qupath.lib.analysis.features;


import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.descriptive.StatisticalSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.imagej.tools.IJTools;
import qupath.imagej.tools.PixelImageIJ;
import qupath.lib.analysis.images.SimpleImage;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathCellObject;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;

public class AdditionalObjectMeasurements extends ObjectMeasurements{
    private final static Logger logger = LoggerFactory.getLogger(AdditionalObjectMeasurements.class);
    public static void addPercentileIntensityMeasurements(
            ImageServer<BufferedImage> server,
            PathCellObject pathObject,
            double downsample,
            List<Double> percentiles,
            Collection<Compartments> compartments) throws IOException {

        var roi = pathObject.getROI();

        int pad = (int)Math.ceil(downsample * 2);
        var request = RegionRequest.createInstance(server.getPath(), downsample, roi)
                .pad2D(pad, pad)
                .intersect2D(0, 0, server.getWidth(), server.getHeight());

        var pathImage = IJTools.convertToImagePlus(server, request);
        var imp = pathImage.getImage();

        Map<String, ImageProcessor> channels = new LinkedHashMap<>();
        var serverChannels = server.getMetadata().getChannels();
        if (server.isRGB() && imp.getStackSize() == 1 && imp.getProcessor() instanceof ColorProcessor) {
            ColorProcessor cp = (ColorProcessor)imp.getProcessor();
            for (int i = 0; i < serverChannels.size(); i++) {
                channels.put(serverChannels.get(i).getName(), cp.getChannel(i+1, null));
            }
        } else {
            assert imp.getStackSize() == serverChannels.size();
            for (int i = 0; i < imp.getStackSize(); i++) {
                channels.put(serverChannels.get(i).getName(), imp.getStack().getProcessor(i+1));
            }
        }

        ByteProcessor bpCell = new ByteProcessor(imp.getWidth(), imp.getHeight());
        bpCell.setValue(1.0);
        var roiIJ = IJTools.convertToIJRoi(roi, pathImage);
        bpCell.fill(roiIJ);


        ByteProcessor bpNucleus = new ByteProcessor(imp.getWidth(), imp.getHeight());
        if (pathObject.getNucleusROI() != null) {
            bpNucleus.setValue(1.0);
            var roiNucleusIJ = IJTools.convertToIJRoi(pathObject.getNucleusROI(), pathImage);
            bpNucleus.fill(roiNucleusIJ);
        }
        measureCellsPercentile(bpNucleus, bpCell, Map.of(1.0, pathObject), channels, compartments, percentiles);

    }

    /**
     * Make cell measurements based on labelled images.
     * All compartments are measured where possible (nucleus, cytoplasm, membrane and full cell).
     *
     * @param ipNuclei labelled image representing nuclei
     * @param ipCells labelled image representing cells
     * @param pathObjects cell objects mapped to integer values in the labelled images
     * @param channels channels to measure, mapped to the name to incorporate into the measurements for that channel
     * @param compartments the cell compartments to measure
     * @param measurements requested measurements to make
     */
    private static void measureCellsPercentile(
            ImageProcessor ipNuclei, ImageProcessor ipCells,
            Map<? extends Number, ? extends PathObject> pathObjects,
            Map<String, ImageProcessor> channels,
            Collection<Compartments> compartments,
            List<Double> measurements) {

        var array = mapToArray(pathObjects);
//		PathObjectTools.constrainCellByScaledNucleus(cell, nucleusScaleFactor, keepMeasurements)
        int width = ipNuclei.getWidth();
        int height = ipNuclei.getHeight();
        ImageProcessor ipMembrane = new FloatProcessor(width, height);
        ImageProcessor ipCytoplasm = ipCells.duplicate();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float cell = ipCells.getf(x, y);
                float nuc = ipNuclei.getf(x, y);
                if (nuc != 0f)
                    ipCytoplasm.setf(x, y, 0f);
                if (cell == 0f)
                    continue;
                // Check 4-neighbours to decide if we're at the membrane
                if ((y >= 1 && ipCells.getf(x, y-1) != cell) ||
                        (y < height-1 && ipCells.getf(x, y+1) != cell) ||
                        (x >= 1 && ipCells.getf(x-1, y) != cell) ||
                        (x < width-1 && ipCells.getf(x+1, y) != cell))
                    ipMembrane.setf(x, y, cell);
            }
        }

        var imgNuclei = new PixelImageIJ(ipNuclei);
        var imgCells = new PixelImageIJ(ipCells);
        var imgCytoplasm = new PixelImageIJ(ipCytoplasm);
        var imgMembrane = new PixelImageIJ(ipMembrane);


        for (var entry : channels.entrySet()) {
            var img = new PixelImageIJ(entry.getValue());
            if (compartments.contains(Compartments.NUCLEUS))
                measureObjectsPercentile(img, imgNuclei, array, entry.getKey().trim() + ": " + "Nucleus", measurements);
            if (compartments.contains(Compartments.CYTOPLASM))
                measureObjectsPercentile(img, imgCytoplasm, array, entry.getKey().trim() + ": " + "Cytoplasm", measurements);
            if (compartments.contains(Compartments.MEMBRANE))
                measureObjectsPercentile(img, imgMembrane, array, entry.getKey().trim() + ": " + "Membrane", measurements);
            if (compartments.contains(Compartments.CELL))
                measureObjectsPercentile(img, imgCells, array, entry.getKey().trim() + ": " + "Cell", measurements);
        }

    }


    private static PathObject[] mapToArray(Map<? extends Number, ? extends PathObject> pathObjects) {
        Number[] labels = new Number[pathObjects.size()];
        int n = 0;
        long maxLabel = 0L;
        int invalidLabels = 0;
        for (var label : pathObjects.keySet()) {
            long lab = label.longValue();
            if (lab < 0 || lab != label.doubleValue() || lab >= Integer.MAX_VALUE) {
                invalidLabels++;
            } else {
                labels[n] = label;
                maxLabel = Math.max(lab, maxLabel);
                n++;
            }
        }

        if (invalidLabels > 0) {
            logger.warn("Only {}/{} labels are integer values >= 0 and < Integer.MAX_VALUE, the rest will be discarded!",
                    n, pathObjects.size());
        }

        PathObject[] array = new PathObject[n];
        for (var label : labels) {
            array[label.intValue()-1] = pathObjects.get(label);
        }
        return array;
    }

//	/**
//	 * Measure objects within the specified image, adding them to the corresponding measurement lists.
//	 * @param img intensity values to measure
//	 * @param imgLabels labels corresponding to objects
//	 * @param pathObjects map between label values and objects
//	 * @param baseName base name to include when adding measurements (e.g. the channel name)
//	 * @param measurements requested measurements
//	 */
//	private static void measureObjects(
//			SimpleImage img, SimpleImage imgLabels,
//			Map<? extends Number, ? extends PathObject> pathObjects,
//			String baseName, Collection<Measurements> measurements) {
//
//		measureObjects(img, imgLabels, mapToArray(pathObjects), baseName, measurements);
//	}

    /**
     * Measure objects within the specified image, adding them to the corresponding measurement lists.
     * @param img intensity values to measure
     * @param imgLabels labels corresponding to objects
     * @param pathObjects array of objects, where array index for an object is 1 less than the label in imgLabels
     * @param baseName base name to include when adding measurements (e.g. the channel name)
     * @param percentile requested measurements
     */
    private static void measureObjectsPercentile(
            SimpleImage img, SimpleImage imgLabels,
            PathObject[] pathObjects,
            String baseName, List<Double> measurements) {

        // Initialize stats
        int n = pathObjects.length;
        DescriptiveStatistics[] allStats = new DescriptiveStatistics[n];
        for (int i = 0; i < n; i++)
            allStats[i] = new DescriptiveStatistics(DescriptiveStatistics.INFINITE_WINDOW);

        // Compute statistics
        int width = img.getWidth();
        int height = img.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int label = (int)imgLabels.getValue(x, y);
                if (label <= 0 || label > n)
                    continue;
                float val = img.getValue(x, y);
                allStats[label-1].addValue(val);
            }
        }

        // Add measurements
        for (var percentile : measurements) {
            for (int i = 0; i < n; i++) {
                var pathObject = pathObjects[i];
                if (pathObject == null)
                    continue;
                var stats = allStats[i];
                try (var ml = pathObject.getMeasurementList()) {
                    ml.putMeasurement(baseName + ": Percentile: " + percentile, stats.getPercentile(percentile));

                }
            }
        }
    }

}
