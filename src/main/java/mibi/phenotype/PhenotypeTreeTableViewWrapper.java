package mibi.phenotype;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import mibi.phenotype.PhenotypeHierarchy.PhenotypeNode;

public class PhenotypeTreeTableViewWrapper {


    private TreeTableView<TreeItem<PhenotypeNode>> treeTableView;

    public PhenotypeTreeTableViewWrapper(){
        treeTableView = new TreeTableView<>();

    }


}
