package mibi;

import mibi.phenotype.QuantileGUI;

import org.controlsfx.control.action.Action;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

public class MIBIExtension implements QuPathExtension {
    @Override
    public void installExtension(QuPathGUI qupath) {
        Action quantileWindow = ActionTools.createAction(new QuantileGUI(qupath), "Quantile");
        MenuTools.addMenuItems(
                qupath.getMenu("Extensions>MIBI Extension", true),
                quantileWindow
        );
    }

    @Override
    public String getName() {
        return "MIBI Extension";
    }

    @Override
    public String getDescription() {
        return "A pipeline to process images obtained from MIBI scope";
    }
}
