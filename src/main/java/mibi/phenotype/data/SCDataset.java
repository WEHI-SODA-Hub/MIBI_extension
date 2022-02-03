package mibi.phenotype.data;

import javafx.util.Pair;
import mibi.phenotype.PhenotypeHierarchy;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
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

        Pair<String, DescriptiveStatistics>[] descriptiveStats = new Pair[fullMarkerMeasurements.size()];
        for (String measurement : fullMarkerMeasurements){

        }
        for (var cell : cells){
             cell.getMeasurementList().getMeasurementValue()
        }
    }

    public Tensor constructMarkerMat(){
        return null;
    }

    public Tensor fixDesign(){
        return null;
    }

    private List<String> createFullMeasurements(Collection<String> markers, )
}
