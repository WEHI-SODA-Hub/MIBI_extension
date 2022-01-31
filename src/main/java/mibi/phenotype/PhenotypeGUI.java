package mibi.phenotype;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import qupath.lib.images.ImageData;

import java.awt.image.BufferedImage;

public class PhenotypeGUI implements Runnable, ChangeListener<ImageData<BufferedImage>> {
    @Override
    public void run() {

    }

    public void createStage(){

    }

    @Override
    public void changed(ObservableValue<? extends ImageData<BufferedImage>> observableValue,
                        ImageData<BufferedImage> imageData, ImageData<BufferedImage> t1) {

    }
}
