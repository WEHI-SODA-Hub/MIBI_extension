package mibi;

import mibi.segmentation.TensorFlowModelLoadTest;
import mibi.stitch.GridStitchWindow;


import org.controlsfx.control.action.Action;
import qupath.lib.gui.ActionTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.tools.MenuTools;

public class MIBIExtension implements QuPathExtension {
    @Override
    public void installExtension(QuPathGUI qupath) {
        Action stitchWindow = ActionTools.createAction(new GridStitchWindow(), "Stitching");
        MenuTools.addMenuItems(
                qupath.getMenu("Extensions>MIBI Extension", true),
                stitchWindow);

        Action segmentationWindow = ActionTools.createAction(new TensorFlowModelLoadTest(qupath), "Segmentation");
        MenuTools.addMenuItems(
                qupath.getMenu("Extensions>MIBI Extension", true),
                segmentationWindow);
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
