package mibi.phenotype;

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

class AstirCellTypeClassifier<T> extends AbstractObjectClassifier<T>{


    private Collection<PathClass> pathClasses;


    protected AstirCellTypeClassifier(PathObjectFilter filter, Collection<PathClass> pathClasses) {
        super(filter);
        this.pathClasses = pathClasses;

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
