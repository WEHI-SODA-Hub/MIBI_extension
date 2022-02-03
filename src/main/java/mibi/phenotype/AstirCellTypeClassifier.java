package mibi.phenotype;

import mibi.phenotype.celltype.TypeRecognitionNet;
import org.tensorflow.Tensor;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.lib.classifiers.object.AbstractObjectClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectFilter;
import qupath.lib.objects.classes.PathClass;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public class AstirCellTypeClassifier<T> extends AbstractObjectClassifier<T>{


    private PhenotypeHierarchy phenotypeHierarchy;
    private int G;
    private int C;

    private Object dType;

    private TypeRecognitionNet recognitionNet;

    public AstirCellTypeClassifier(PathObjectFilter filter, PhenotypeHierarchy phenotypeHierarchy) {
        super(filter);
        this.phenotypeHierarchy = phenotypeHierarchy;

        this.C = phenotypeHierarchy.getNClasses();
        this.G = phenotypeHierarchy.getNMarkers();

        recognitionNet = new TypeRecognitionNet(C, G);

        Tensor
    }

    @Override
    public Collection<PathClass> getPathClasses() {
        return null;
    }

    @Override
    public int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> pathObjects, boolean resetExistingClass) {
        return 0;
    }

    @Override
    public Map<String, Integer> getMissingFeatures(ImageData<T> imageData, Collection<? extends PathObject> collection) {
        return null;
    }

}
