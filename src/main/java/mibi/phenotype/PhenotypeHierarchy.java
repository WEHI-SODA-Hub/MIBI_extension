package mibi.phenotype;

import javafx.util.Pair;
import qupath.lib.analysis.features.ObjectMeasurements.Measurements;
import qupath.lib.analysis.features.ObjectMeasurements.Compartments;

import java.util.*;

/**
 * A tree structure for Phenotype Hierarchy
 */
public class PhenotypeHierarchy {

    //TODO: Need check to see if newly added phenotypes have markers from this hashmap
    private HashMap<String, Pair<Compartments, Measurements>> markerInformation;

    private PhenotypeNode root;

    public PhenotypeHierarchy(HashMap<String, Pair<Compartments, Measurements>> markerInformation){
        this.markerInformation = markerInformation;
        root = new PhenotypeNode();
    }

    public PhenotypeNode getRoot(){
        return root;
    }

    public void setRoot(PhenotypeNode node){
       this.root = node;
    }

    public TreeSet<PhenotypeNode> getAllChildren(){
        TreeSet<PhenotypeNode> list = new TreeSet<>();
        getChildren(root, list);
        return list;
    }

    private void getChildren(PhenotypeNode node, TreeSet<PhenotypeNode> list){
        list.addAll(node.getChildren());
        node.getChildren().forEach(c -> getChildren(c, list));
    }

    public TreeSet<String> getAllMarkers(){
        TreeSet<String> markers = new TreeSet<>();
        getMarkers(root, markers);
        return markers;
    }

    private void getMarkers(PhenotypeNode node, TreeSet<String> markers){
        node.getChildren().forEach(c -> {
            markers.addAll(c.getMarkers());
            getMarkers(c, markers);
        });
    }

    public TreeSet<String> getFullMeasurements(){
        TreeSet<String> fullMeasurements = new TreeSet<>();
        getAllMarkers().forEach(t -> {
            Pair<Compartments, Measurements> pair = markerInformation.get(t);
            var compartment = pair.getKey();
            var measurement = pair.getValue();

            StringBuilder sb = new StringBuilder();

            sb.append(t);
            sb.append(": ");

            if (compartment == Compartments.CELL){
                sb.append("Cell");
                sb.append(": ");
            } else if (compartment == Compartments.CYTOPLASM){
                sb.append("Cytoplasm");
                sb.append(": ");
            } else if (compartment == Compartments.MEMBRANE) {
                sb.append("Membrane");
                sb.append(": ");
            } else if (compartment == Compartments.NUCLEUS){
                sb.append("Nucleus");
                sb.append(": ");
            } else {
                throw new RuntimeException("No compartment found for " + t);
            }
            sb.append(measurement.name());
            fullMeasurements.add(sb.toString());
        });

        return fullMeasurements;
    }

    public boolean isEmpty(){
        return !root.hasChildren();
    }

    //TODO: Implement way to save marker dict has yaml
    public void saveYAMLFile(){

    }

    public int getNClasses(){
        int i=0;
        count(root, i);
        return i;
    }

    public void count(PhenotypeNode node, int i){
        i++;
        for (var c : node.getChildren()){
            count(c, i);
        }
    }

    public int getNMarkers(){
        return getAllMarkers().size();
    }

    public static class PhenotypeNode implements Comparable<PhenotypeNode>{
        private PhenotypeNode parent;
        private List<PhenotypeNode> children;
        private List<String> markers;
        private String name;

        public PhenotypeNode(){
            this.children = new ArrayList<>();
            this.markers = new ArrayList<>();
        }

        public boolean hasChildren(){
            return children.size() > 0;
        }

        public PhenotypeNode(String name, PhenotypeNode parent, List<String> newMarkers){
            this.parent = parent;
            this.name = name;
            this.children = new ArrayList<>();

            this.markers = new ArrayList<>(parent.getMarkers());
            markers.addAll(newMarkers);
        }

        public PhenotypeNode getParent() {
            return parent;
        }



        public void setParent(PhenotypeNode parent) {
            this.parent = parent;
        }

        public List<PhenotypeNode> getChildren() {
            return children;
        }

        public void setChildren(List<PhenotypeNode> children) {
            this.children = children;
        }

        public void addChildren(PhenotypeNode... children){
            this.children.addAll(Arrays.asList(children));
        }

        public void addChild(String name, List<String> newMarkers){
            PhenotypeNode child = new PhenotypeNode(name, this, newMarkers);
            this.addChildren(child);
        }

        public List<String> getMarkers() {
            return markers;
        }

        public void setMarkers(List<String> markers) {
            this.markers = markers;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFullName(){
            return parent == null ?  getName() : parent.getFullName() + ": " + getName();
        }

        @Override
        public int compareTo(PhenotypeNode o) {
            return this.getFullName().compareTo(o.getFullName());
        }
    }
}
