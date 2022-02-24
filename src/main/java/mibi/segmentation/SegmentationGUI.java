package mibi.segmentation;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.apache.commons.math3.util.Precision;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.projects.ProjectImageEntry;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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

    private HashMap<String, String> nucleiMap = new HashMap<>();
    private HashMap<String, List<String>> membraneMap = new HashMap<>();

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
        modelHBox.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(modelPathLabel, 0, i);
        gridPane.add(modelHBox, 1, i);
        i++;

        var nucleiLabel = createLabel("Select nuclei channel");
        var channels = FXCollections.observableArrayList(
                qupath.getImageData().getServer().getMetadata().getChannels().stream().map(ImageChannel::getName).collect(Collectors.toList())
        );
        nucleiComboBox = new ComboBox<>(channels);
        nucleiComboBox.setMaxWidth(Double.MAX_VALUE);
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
        membraneComboBox.setMaxWidth(Double.MAX_VALUE);
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
        var minNucAreaTextField = createNaturalNumberTextField(scene, 0);
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


        var subtractiveFactorLabel = createLabel("Segmentation Level");
        var segmentationSlider = new Slider();
        segmentationSlider.setMax(10);
        segmentationSlider.setMin(0);
        segmentationSlider.setValue(5);
        segmentationSlider.setShowTickMarks(true);
        segmentationSlider.valueProperty().addListener((obs, oldval, newVal) ->
                segmentationSlider.setValue(Math.round(newVal.doubleValue())));
//        var subtractiveFactorTextField = createNaturalNumberTextField(scene, 5);
//        createSliderTextFieldPair(segmentationSlider, subtractiveFactorTextField);

        var segmentationLabel = createLabel("<< Less segmentation     More segmentation >>");


        gridPane.add(subtractiveFactorLabel,0,i);
//        gridPane.add(subtractiveFactorTextField, 1,i);
        i++;

        gridPane.add(segmentationSlider, 0, i, 2, 1);
        i++;

        var segmentationBox = createHBox(segmentationLabel);
        segmentationBox.setAlignment(Pos.CENTER);
        segmentationLabel.setAlignment(Pos.CENTER);
        gridPane.add(segmentationBox, 0, i, 2,1);
        i++;

        var separator4 = new Separator();
        separator4.setOrientation(Orientation.HORIZONTAL);
        separator4.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(separator4, 0, i, 2, 1);
        i++;



        var runButton = new Button("Run Segmentation");
        runButton.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(runButton, 0, i, 2, 1);
        i++;

        runButton.setOnAction(e -> {
            buildDeepCell(
                    folder.get(),
                    Integer.parseInt(minNucAreaTextField.getText()),
                    Integer.parseInt(cellExpansionTextField.getText()),
                    Double.parseDouble(cellConstrainScaleTextField.getText()),
                    Integer.parseInt(paddingTextField.getText()),
                    measurementsCheckBox.isSelected(),
                    shapeMeasurementsCheckBox.isSelected(),
                    Integer.parseInt(nThreadsTextField.getText()),
                    segmentationSlider.getValue()
            );

            try {
                var deepCell = builder.build();
                var imageData = qupath.getImageData();
                var pathObjects = qupath.getImageData().getHierarchy().getSelectionModel().getSelectedObjects();
                deepCell.detectObjects(imageData, pathObjects);
                Dialogs.showInfoNotification(TITLE, "Done!");
            }catch(RuntimeException exception){
                Dialogs.showErrorNotification(TITLE, exception);
            }


        });



        var runForProjectButton = new Button("Run Segmentation for Current Project");
        runForProjectButton.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(runForProjectButton, 0, i, 2, 1);
        i++;

        runForProjectButton.setOnAction(e -> {

            if (qupath.getProject() == null){
                Dialogs.showErrorMessage(TITLE, "Could not find a project!");
                return;
            }

            if (Dialogs.showConfirmDialog(TITLE, "Are you sure you want to run for the entire project? This may take a very long time")){
                buildDeepCell(
                        folder.get(),
                        Integer.parseInt(minNucAreaTextField.getText()),
                        Integer.parseInt(cellExpansionTextField.getText()),
                        Double.parseDouble(cellConstrainScaleTextField.getText()),
                        Integer.parseInt(paddingTextField.getText()),
                        measurementsCheckBox.isSelected(),
                        shapeMeasurementsCheckBox.isSelected(),
                        Integer.parseInt(nThreadsTextField.getText()),
                        segmentationSlider.getValue()
                );
                var deepCell = builder.build();


                for (var entry : qupath.getProject().getImageList()) {
                    try {
                        var imageData = entry.readImageData();
                        var pathObjects = imageData.getHierarchy().getSelectionModel().getSelectedObjects();
                        deepCell.detectObjects(imageData, pathObjects);

                        entry.saveImageData(imageData);
                    } catch (IOException ioException) {
                        Dialogs.showErrorMessage(TITLE, "Could not read "+entry.getImageName());
                    } catch (RuntimeException runtimeException){
                        Dialogs.showErrorNotification(TITLE, "Runtime exception for image " + entry.getImageName() + " " + runtimeException);
                    }
                }
                Dialogs.showInfoNotification(TITLE, "Done! Please open and close the current image to see cells!");
            }
        });

        var separator5 = new Separator();
        separator5.setOrientation(Orientation.HORIZONTAL);
        separator5.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(separator5, 0, i, 2, 1);
        i++;

        var refreshButton = new Button("Refresh window");
        refreshButton.setOnAction(e -> {
            changed(null, qupath.getImageData(), qupath.getImageData());
        });
        refreshButton.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(refreshButton, 0, i, 2, 1);

        stage.setScene(scene);
    }


    private void buildDeepCell(File folder, int minNucArea, int cellExpansion, double cellConstrainScale, int padding,
    boolean measurements, boolean shapeMeasurements, int nThreads, double subtractiveFactor){
        if (builder == null){
            builder = DeepCell2D.builder();
        }
        builder
                .modelPath(folder)
                .nucleiChannel(nucleiComboBox.getValue())
                .membraneChannels(membraneList)
                .minNucleiArea(minNucArea)
                .cellExpansion(cellExpansion)
                .cellConstrainScale(cellConstrainScale)
                .padding(padding);

        if (measurements){
            builder.measureIntensity();
        }
        if (shapeMeasurements){
            builder.measureShape();
        }

        if (nThreads != 1){
            builder.nThreads(nThreads);
        }

        // Kind of a funky way to do it. Controls the amount of segmentation.
        // Max subtractive factor is 0.9 and min is 0. Piecewise linear function.
        if (Precision.equals(subtractiveFactor, 0)){
            builder.subtractiveFactor(0);
        } else if (subtractiveFactor < 5){
            builder.subtractiveFactor(0.0002 * subtractiveFactor);
        } else {
            builder.subtractiveFactor(0.2 * (0.9 - 0.001) * subtractiveFactor + 2 * 0.001 - 0.9);
        }
        builder.percentile(95);
        builder.percentile(90);
        builder.percentile(80);
        builder.percentile(70);

    }


    @Override
    public void changed(ObservableValue<? extends ImageData<BufferedImage>> observableValue,
                        ImageData<BufferedImage> oldIm, ImageData<BufferedImage> newIm) {
        if (newIm !=null && nucleiComboBox != null && membraneComboBox != null) {
            if (oldIm != null) {
                nucleiMap.put(oldIm.getServerPath(), nucleiComboBox.getValue());
                membraneMap.put(oldIm.getServerPath(), membraneList);
            }

            var channels = FXCollections.observableArrayList(
                    newIm.getServer().getMetadata().getChannels().stream().map(ImageChannel::getName)
                            .collect(Collectors.toList())
            );

            nucleiComboBox.setItems(channels);
            membraneComboBox.setItems(channels);

            membraneList = membraneMap.getOrDefault(newIm.getServerPath(), new ArrayList<>());
            nucleiComboBox.setValue(nucleiMap.getOrDefault(newIm.getServerPath(), null));

            String text = membraneList.toString();
            if (membraneList.size() > 0) {
                membraneTextField.setText(text.substring(1, text.length() - 1));
                membraneComboBox.setValue(membraneList.get(membraneList.size()-1));
            }else
                membraneTextField.setText("");
        } else if(newIm == null && nucleiComboBox != null && membraneComboBox != null){
            nucleiComboBox.setItems(FXCollections.observableArrayList());
            membraneComboBox.setItems(FXCollections.observableArrayList());
            nucleiComboBox.setValue(null);
            membraneComboBox.setValue(null);
            membraneTextField.setText("");
        }
    }


}
