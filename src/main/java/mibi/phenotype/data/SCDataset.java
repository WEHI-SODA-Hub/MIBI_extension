package mibi.phenotype.data;

import javafx.util.Pair;
import mibi.phenotype.PhenotypeHierarchy;
import org.apache.commons.math3.analysis.function.Asin;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.analysis.function.Asinh;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;
import qupath.lib.objects.PathObject;

import java.util.Collection;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.TreeSet;
import mibi.phenotype.PhenotypeHierarchy.PhenotypeNode;

public class SCDataset {

    private Object dType ;

    private PhenotypeHierarchy phenotypeHierarchy;

    private TreeSet<String> markers;

    private TreeSet<PhenotypeNode> cellTypes;

    private TreeSet<String> fullMarkerMeasurements;

    private Tensor markerMat;

    private final static double PERCENTILE = 0.95;

    private final static double COFACTOR = 5;

    // TODO: default to cpu. not sure how to do gpu
    private Object device;

    public SCDataset(Collection<PathObject> cells, PhenotypeHierarchy phenotypeHierarchy){

        this.phenotypeHierarchy = phenotypeHierarchy;

        this.markers = phenotypeHierarchy.getAllMarkers();
        this.cellTypes = phenotypeHierarchy.getAllChildren();
        this.fullMarkerMeasurements = phenotypeHierarchy.getFullMeasurements();


        if (cellTypes.size() <= 1){

        }

        markerMat = constructMarkerMat();

        if (cells.size() < 1){

        }
        DescriptiveStatistics[] normalisedData = normaliseData(cells);

    }

    public Tensor constructMarkerMat(){
        return null;
    }

    public Tensor fixDesign(){
        return null;
    }

//    private List<String> createFullMeasurements(Collection<String> markers, )

    /**
     * Winsorise and arcsinh
     * @param cells
     */
    public DescriptiveStatistics[] normaliseData(Collection<PathObject> cells){
        DescriptiveStatistics[] descriptiveStats = new DescriptiveStatistics[fullMarkerMeasurements.size()];
        Asinh asinh = new Asinh();
        int col = 0;
        for (String measurement : fullMarkerMeasurements){
            descriptiveStats[col] = new DescriptiveStatistics(cells.size());

            for (var cell : cells) {
                descriptiveStats[col].addValue(asinh.value(cell.getMeasurementList().getMeasurementValue(measurement) / COFACTOR));
            }

            double upperLimit = descriptiveStats[col].getPercentile(PERCENTILE);
            double lowerLimit = descriptiveStats[col].getPercentile(1 - PERCENTILE);
            double[] values = descriptiveStats[col].getValues();

            for (int i=0; i < values.length; i++){
                if (values[i] < lowerLimit){
                    values[i] = lowerLimit;
                } else if (values[i] > upperLimit){
                    values[i] = upperLimit;
                }
            }
            descriptiveStats[col] = new DescriptiveStatistics(values);
            col++;
        }
        return descriptiveStats;
    }
}
