package mibi.phenotype;

import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.objects.PathObjectFilter;

import java.util.HashMap;

public class ClassifyByAstirBuilder<T> {


    private PathObjectFilter filter = PathObjectFilter.DETECTIONS_ALL;
    private HashMap<String, String> measurementMap;
    private PhenotypeHierarchy phenotypeHierarchy;

    /**
     * Constructor.
     * @param measurementMap maps the measurement which should be used for each marker
     */
    public ClassifyByAstirBuilder(HashMap<String, String> measurementMap) {
        this.measurementMap = measurementMap;
    }

    /**
     * Define the filter used to identify objects compatible with this classifier.
     * @param filter
     * @return this builder
     */
    public ClassifyByAstirBuilder<T> filter(PathObjectFilter filter) {
        this.filter = filter;
        return this;
    }

    public ClassifyByAstirBuilder<T> measurementMap(HashMap<String, String> measurementMap){
        this.measurementMap = measurementMap;
        return this;
    }

    public ClassifyByAstirBuilder<T> updateMeasurement(String marker, String measurement){
        measurementMap.put(marker, measurement);
        return this;
    }

    public ClassifyByAstirBuilder<T> removeMarker(String marker){
        measurementMap.remove(marker);
        return this;
    }

    public ClassifyByAstirBuilder<T> phenotypeHierarchy(PhenotypeHierarchy phenotypeHierarchy){
        this.phenotypeHierarchy = phenotypeHierarchy;
        return this;
    }

    /**
     * Build the classifier defined by the parameters of this builder.
     * @return the object classifier
     */
    public ObjectClassifier<T> build() {
        if (phenotypeHierarchy == null)
            throw new IllegalArgumentException("No Hierarchy given!");

        if (measurementMap == null)
            throw new IllegalArgumentException("No Measurements given!");

        if (measurementMap.isEmpty())
            throw new IllegalArgumentException("Measurements are empty!");

        if (phenotypeHierarchy.isEmpty())
            throw new IllegalArgumentException("Hierarchy is empty!");


        return new AstirCellTypeClassifier<>(filter, phenotypeHierarchy);
    }
}
