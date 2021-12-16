package mibi.segmentation;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static mibi.gui.NodeTools.*;

public class SegmentationGUI implements Runnable, ChangeListener<ImageData<BufferedImage>> {

    private Stage stage;
    private final String TITLE = "DeepCell";
    private QuPathGUI qupath;

    private DeepCell2D.Builder builder;

    private List<String> membraneList = new ArrayList<>();
    private ComboBox<String> nucleiComboBox;
    private ComboBox<String> membraneComboBox;
    private TextField membraneTextField;

    private HashMap<ImageData<BufferedImage>, String> nucleiMap = new HashMap<>();
    private HashMap<ImageData<BufferedImage>, List<String>> membraneMap = new HashMap<>();

    public SegmentationGUI(QuPathGUI qupath){
        this.qupath = qupath;
        qupath.getViewer().imageDataProperty().addListener(this);
    }


    @Override
    public void run() {

        var viewer = qupath.getViewer();

        if (viewer == null){
            Dialogs.showErrorMessage(TITLE, "Viewer is empty. Please open an image.");
            return;
        }
        if (qupath.getImageData() == null){
            Dialogs.showErrorMessage(TITLE, "Image data is empty");
            return;
        }
        if (stage == null){
            createStage();
            stage.initOwner(qupath.getStage());;
        }
        stage.show();
    }


    private void createStage(){

        stage = new Stage();
        stage.resizableProperty().set(false);
        stage.setTitle(TITLE);

        int i=0;

        GridPane gridPane = createGridPane();
        var scene = new Scene(gridPane);

        var modelPathLabel = createLabel("Select model folder");
        var modelPathChoose = new Button("Choose model");
        var modelTextField = new TextField();
        modelTextField.setEditable(false);
        modelTextField.setMaxWidth(Double.MAX_VALUE);
        AtomicReference<File> folder = new AtomicReference<>();
        modelPathChoose.setOnAction(e -> {
                    folder.set(Dialogs.promptForDirectory(null));
                    if (folder.get() != null)
                        modelTextField.setText(folder.get().getAbsolutePath());
                    }
                );
        HBox modelHBox = createHBox(modelTextField, modelPathChoose);
        gridPane.add(modelPathLabel, 0, i);
        gridPane.add(modelHBox, 1, i);
        i++;

        var nucleiLabel = createLabel("Select nuclei channel");
        var channels = FXCollections.observableArrayList(
                qupath.getImageData().getServer().getMetadata().getChannels().stream().map(ImageChannel::getName).collect(Collectors.toList())
        );
        nucleiComboBox = new ComboBox<>(channels);
        gridPane.add(nucleiLabel, 0, i);
        gridPane.add(nucleiComboBox, 1, i);
        i++;

        var membraneLabel = createLabel("Select membrane channels");
        membraneTextField = new TextField();
        if (membraneList.size() > 0) {
            String text = membraneList.toString();
            membraneTextField.setText(text.substring(1, text.length()-1));
        }
        membraneTextField.setEditable(false);
        membraneComboBox = new ComboBox<>(channels);
        Button add = new Button("+");
        Button minus = new Button("-");

        add.setOnAction((event) -> {
            var selected = membraneComboBox.getValue();
            if (!membraneList.contains(selected)){
                membraneList.add(selected);
                String text = membraneList.toString();
                membraneTextField.setText(text.substring(1, text.length() - 1));
            }
        });

        minus.setOnAction((event) ->{
            var selected = membraneComboBox.getValue();
            if (membraneList.contains(selected)){
                membraneList.remove(selected);
                String text = membraneList.toString();
                membraneTextField.setText(text.substring(1, text.length()-1));
            };
        });
        HBox cell = createHBox(membraneComboBox, add, minus);

        gridPane.add(membraneLabel, 0,i);
        gridPane.add(cell, 1,i);
        i++;

        var membraneListLabel = createLabel("Selected membrane channels");
        gridPane.add(membraneListLabel, 0, i);
        gridPane.add(membraneTextField, 1, i);
        i++;

        var separator = new Separator();
        separator.setOrientation(Orientation.HORIZONTAL);
        separator.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(separator, 0, i, 2, 1);
        i++;

        var measurementsLabel = createLabel("Include marker measurements");
        var measurementsCheckBox = createCheckBox(true);
        gridPane.add(measurementsLabel, 0,i);
        gridPane.add(measurementsCheckBox, 1,i);
        i++;

        var shapeMeasurementsLabel = createLabel("Include shape measurements");
        var shapeMeasurementsCheckBox = createCheckBox(true);
        gridPane.add(shapeMeasurementsLabel, 0,i);
        gridPane.add(shapeMeasurementsCheckBox, 1,i);
        i++;


        var separator2 = new Separator();
        separator2.setOrientation(Orientation.HORIZONTAL);
        separator2.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(separator2, 0, i, 2, 1);
        i++;


        var minNucAreaLabel = createLabel("Minimum nuclei area (px^2)");
        var minNucAreaTextField = createNaturalNumberTextField(scene, 100);
        gridPane.add(minNucAreaLabel, 0, i);
        gridPane.add(minNucAreaTextField, 1, i);
        i++;

        var cellExpansionLabel = createLabel("Cell Expansion");
        var cellExpansionTextField = createNaturalNumberTextField(scene, 3);
        gridPane.add(cellExpansionLabel, 0,i);
        gridPane.add(cellExpansionTextField, 1,i);
        i++;

        var cellConstrainScaleLabel = createLabel("Cell Constrain Scale");
        var cellConstrainScaleTextField = createPositiveRealTextField(scene, 1);
        gridPane.add(cellConstrainScaleLabel, 0,i);
        gridPane.add(cellConstrainScaleTextField, 1,i);
        i++;

        var paddingLabel = createLabel("Padding around tiles");
        var paddingTextField = createNaturalNumberTextField(scene, 64);
        gridPane.add(paddingLabel,0,i);
        gridPane.add(paddingTextField, 1,i);
        i++;

        var subtractiveFactorLabel = createLabel("Subtractive Factor");
        var subtractiveFactorTextField = createPositiveRealTextField(scene, 0.001);
        gridPane.add(subtractiveFactorLabel,0,i);
        gridPane.add(subtractiveFactorTextField, 1,i);
        i++;


        var nThreadsLabel = createLabel("Number of Threads");
        var nThreadsTextField = createNaturalNumberTextField(scene, 1);
        gridPane.add(nThreadsLabel, 0,i );
        gridPane.add(nThreadsTextField, 1,i);
        i++;

        var separator3 = new Separator();
        separator3.setOrientation(Orientation.HORIZONTAL);
        separator3.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(separator3, 0, i, 2, 1);
        i++;


        var runButton = new Button("Run Segmentation");
        runButton.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(runButton, 0, i, 2, 1);
        i++;

        runButton.setOnAction(e -> {
            if (builder == null){
                builder = DeepCell2D.builder();
            }
            builder
                    .modelPath(folder.get())
                    .nucleiChannel(nucleiComboBox.getValue())
                    .membraneChannels(membraneList)
                    .minNucleiArea(Integer.parseInt(minNucAreaTextField.getText()))
                    .cellExpansion(Integer.parseInt(cellExpansionTextField.getText()))
                    .cellConstrainScale(Double.parseDouble(cellConstrainScaleTextField.getText()))
                    .padding(Integer.parseInt(paddingTextField.getText()));

            if (measurementsCheckBox.isSelected()){
                builder.measureIntensity();
            }
            if (shapeMeasurementsCheckBox.isSelected()){
                builder.measureShape();
            }

            if (!nThreadsTextField.getText().equals("1")){
                builder.nThreads(Integer.parseInt(nThreadsTextField.getText()));
            }

            try {
                var deepCell = builder.build();
                var imageData = qupath.getImageData();
                var pathObjects = qupath.getImageData().getHierarchy().getSelectionModel().getSelectedObjects();
                deepCell.detectObjects(imageData, pathObjects);
            }catch(RuntimeException exception){
                Dialogs.showErrorNotification(TITLE, exception);
            }


        });

        var refreshButton = new Button("Refresh window");
        refreshButton.setOnAction(e -> {
            changed(null, qupath.getImageData(), qupath.getImageData());
        });
        refreshButton.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(refreshButton, 0, i, 2, 1);

        stage.setScene(scene);
    }


    @Override
    public void changed(ObservableValue<? extends ImageData<BufferedImage>> observableValue,
                        ImageData<BufferedImage> oldIm, ImageData<BufferedImage> newIm) {
        if (nucleiComboBox != null && membraneComboBox != null) {

            nucleiMap.put(oldIm, nucleiComboBox.getValue());
            membraneMap.put(oldIm, membraneList);

            var channels = FXCollections.observableArrayList(
                    newIm.getServer().getMetadata().getChannels().stream().map(ImageChannel::getName).collect(Collectors.toList())
            );

            nucleiComboBox.setItems(channels);
            membraneComboBox.setItems(channels);
            membraneTextField.setText("");

            var nucleiNew = nucleiMap.getOrDefault(newIm, null);
            var membraneNew = membraneMap.getOrDefault(newIm, null);

            if (nucleiNew != null){
                nucleiComboBox.setValue(nucleiNew);
            }
            if (membraneNew != null){
                String text = membraneNew.toString();
                membraneTextField.setText(text.substring(1, text.length()-1));
                membraneList = membraneNew;
            }
        }
    }


}
