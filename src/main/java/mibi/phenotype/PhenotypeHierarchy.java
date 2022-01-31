package mibi.phenotype;

import java.util.*;

/**
 * A tree structure for Phenotype Hierarchy
 */
public class PhenotypeHierarchy {

    private PhenotypeNode root;

    public PhenotypeHierarchy(){
        root = new PhenotypeNode();
    }

    public PhenotypeNode getRoot(){
        return root;
    }

    public void setRoot(PhenotypeNode node){
       this.root = node;
    }

    public List<PhenotypeNode> getAllChildren(){
        List<PhenotypeNode> list = new ArrayList<>();
        getChildren(root, list);
        return list;
    }

    public void getChildren(PhenotypeNode node, List<PhenotypeNode> list){
        list.addAll(node.getChildren());
        node.getChildren().forEach(c -> getChildren(c, list));
    }

    public static class PhenotypeNode{
        private PhenotypeNode parent;
        private List<PhenotypeNode> children;
        private List<String> markers;
        private String name;

        public PhenotypeNode(){
            this.children = new ArrayList<>();
            this.markers = new ArrayList<>();
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
    }
}
