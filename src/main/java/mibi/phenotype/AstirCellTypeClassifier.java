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


    private Function<PathObject, PathClass> function;
    private Collection<PathClass> pathClasses;


    protected AstirCellTypeClassifier(PathObjectFilter filter, Function<PathObject, PathClass> function, Collection<PathClass> pathClasses) {
        super(filter);
        this.function = function;
        this.pathClasses = pathClasses;

    }

    @Override
    public Collection<PathClass> getPathClasses() {
        return null;
    }

    @Override
    public int classifyObjects(ImageData<T> imageData, Collection<? extends PathObject> collection, boolean b) {
        return 0;
    }

    @Override
    public Map<String, Integer> getMissingFeatures(ImageData<T> imageData, Collection<? extends PathObject> collection) {
        return null;
    }


    public static class AstirCellTypeClassifierBuilder{

        private PathObjectFilter filter = PathObjectFilter.DETECTIONS_ALL;
        private String measurementName;

        public AstirCellTypeClassifierBuilder filter(PathObjectFilter filter){
            this.filter = filter;
            return this;
        }


    }



}
