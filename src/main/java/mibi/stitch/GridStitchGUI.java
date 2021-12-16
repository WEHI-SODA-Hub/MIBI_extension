package mibi.stitch;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.macro.Interpreter;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import static ij.IJ.runMacroFile;
import static ij.IJ.saveAs;
import static mibi.gui.NodeTools.*;

import mibi.gui.NodeTools;
import mibi.stitch.GridStitchMacroOptions.Type;
import mibi.stitch.GridStitchMacroOptions.FusionMethod;
import mibi.stitch.GridStitchMacroOptions.ComputationParameter;
import org.controlsfx.control.spreadsheet.StringConverterWithFormat;
import qupath.imagej.gui.IJExtension;
import qupath.imagej.gui.ImageJMacroRunner;
import qupath.imagej.tools.IJTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;

import javax.swing.*;
import java.io.File;
import java.util.Arrays;

public class GridStitchGUI implements Runnable {

    private Stage stage;
    private GridStitchMacroOptions options;
    private static final String TITLE = "Grid Stitch";
    private QuPathGUI qupath;
    public GridStitchGUI(QuPathGUI qupath){
        this.qupath = qupath;
    }

    public void createStage(){
        stage = new Stage();
        stage.resizableProperty().set(false);
        stage.setTitle(TITLE);
        stage.initOwner(qupath.getStage());
        options = new GridStitchMacroOptions();
        GridPane gridPane = createGridPane();
        Scene scene = new Scene(gridPane);
        stage.setScene(scene);

        int i=0;
        int col1 = 0, col2 = 1;

        // Initialising things for the type of grid stitch
        Label typeLabel = createLabel("Select grid type ");
        ComboBox<Type> typeComboBox = createTypeComboBox();
        gridPane.add(typeLabel, col1, i);
        gridPane.add(typeComboBox, col2, i++);

        // Initialise things for the order of the type
        Label orderLabel = createLabel("Select the order ");
        ComboBox<String> orderComboBox = createOrderComboBox(typeComboBox);
        gridPane.add(orderLabel, col1, i);
        gridPane.add(orderComboBox, col2, i++);

        // Separator
        Separator sep1 = new Separator();
        sep1.setOrientation(Orientation.HORIZONTAL);
        gridPane.add(sep1, 0, i++, 3, 1);

        // Initialise width and height
        Label widthLabel = createLabel("Width of grid");
        Label heightLabel = createLabel("Height of grid");
        TextField widthTextField = createNaturalNumberTextField(scene, options.getWidth());
        TextField heightTextField = createNaturalNumberTextField(scene, options.getHeight());
        gridPane.add(widthLabel, col1, i);
        gridPane.add(widthTextField, col2, i++);
        gridPane.add(heightLabel, col1, i);
        gridPane.add(heightTextField, col2, i++);

        // For tile overlap
        Label overlapLabel = createLabel("Tile Overlap percentage");
        TextField overlapTextField = createPositiveRealTextField(scene, options.getTileOverlap());
        Slider overlapSlider = createSlider(options.getTileOverlap(), 0, 100, true);
        createSliderTextFieldPair(overlapSlider, overlapTextField);
        gridPane.add(overlapLabel, col1, i);
        gridPane.add(overlapTextField, col2, i++);
        gridPane.add(overlapSlider, col2, i++);

        // First file index
        Label firstFileIndexLabel = createLabel("Index of the first image");
        TextField firstFileIndexTextField = createNaturalNumberTextField(scene, options.getFirstFileIndex());
        gridPane.add(firstFileIndexLabel, col1, i);
        gridPane.add(firstFileIndexTextField, col2, i++);

        // Files names
        Label fileNamesLabel = createLabel("Enter the pattern of the image names");
        TextField fileNamesTextField = new TextField(options.getFilenamesRegex());
        gridPane.add(fileNamesLabel, col1, i);
        gridPane.add(fileNamesTextField, col2, i++);

        // Directory
        Label directoryLabel = createLabel("Choose directory with all images");
        TextField directoryTextField = new TextField();
        directoryTextField.setEditable(false);
        Button directoryButton = new Button("Browse");
        DirectoryChooser dirChooser = new DirectoryChooser();
        directoryButton.setOnAction(e -> {
            File pathOut = dirChooser.showDialog(stage);
            if (pathOut != null && pathOut.isDirectory()){
                directoryTextField.setText(pathOut.getAbsolutePath());
                options.setDirectory(pathOut);
            }
        });
        HBox directoryHBox = createHBox(directoryTextField, directoryButton);
        HBox.setHgrow(directoryTextField, Priority.ALWAYS);
        gridPane.add(directoryLabel, col1, i);
        gridPane.add(directoryHBox, col2, i++);

        // Text file
        Label textFileNameLabel = createLabel("Give a name for the output text file");
        TextField textFileNameTextField = new TextField(options.getTextFilename());
        gridPane.add(textFileNameLabel, col1, i);
        gridPane.add(textFileNameTextField, col2, i++);

        // Fusion method
        Label fusionMethodLabel = createLabel("Select fusion method");
        ComboBox<GridStitchMacroOptions.FusionMethod> fusionMethodComboBox = createFusionMethodComboBox();
        gridPane.add(fusionMethodLabel, col1, i);
        gridPane.add(fusionMethodComboBox, col2, i++);

        // Separator
        Separator sep2 = new Separator();
        sep2.setOrientation(Orientation.HORIZONTAL);
        gridPane.add(sep2, 0, i++, 3, 1);

        // Regression Threshold
        Label regressionThresholdLabel = createLabel("Regression threshold");
        TextField regressionThresholdTextField = createPositiveRealTextField(scene, options.getRegressionThreshold());
        gridPane.add(regressionThresholdLabel, col1, i);
        gridPane.add(regressionThresholdTextField, col2, i++);

        // Max Av displacement
        Label maxAvgDisplacementThresholdLabel = createLabel("Max/avg displacement threshold");
        TextField maxAvgDisplacementThresholdTextField = createPositiveRealTextField(scene, options.getMaxAvgDisplacementThreshold());
        gridPane.add(maxAvgDisplacementThresholdLabel, col1, i);
        gridPane.add(maxAvgDisplacementThresholdTextField, col2, i++);

        // Abs displacement threshold
        Label absDisplacementThresholdLabel = createLabel("Absolute displacement threshold");
        TextField absDisplacementThresholdTextField = createPositiveRealTextField(scene, options.getAbsoluteDisplacementThreshold());
        gridPane.add(absDisplacementThresholdLabel, col1, i);
        gridPane.add(absDisplacementThresholdTextField, col2, i++);

        // Separator
        Separator sep3 = new Separator();
        sep3.setOrientation(Orientation.HORIZONTAL);
        gridPane.add(sep3, 0, i++, 3, 1);

        // Add tiles to roi
        Label addToROILabel = createLabel("Add tiles to ROI");
        CheckBox addToROICheckBox = createCheckBox(options.isAddTilesAsROI());
        gridPane.add(addToROILabel, col1, i);
        gridPane.add(addToROICheckBox, col2, i++);

        // Compute overlap
        Label computeOverLapLabel = createLabel("Create Overlap");
        CheckBox computeOverlapCheckBox = new CheckBox();
        GridPane.setHalignment(computeOverlapCheckBox, HPos.CENTER);
        gridPane.add(computeOverLapLabel, col1, i);
        gridPane.add(computeOverlapCheckBox, col2, i++);

        // Invert
        Label invertXLabel = createLabel("Invert X");
        Label invertYLabel = createLabel("Inver Y");
        CheckBox invertXCheckBox = createCheckBox(options.isInvertXCoordinates());
        CheckBox invertYCheckBox = createCheckBox(options.isInvertYCoordinates());
        gridPane.add(invertXLabel, col1, i);
        gridPane.add(invertXCheckBox, col2, i++);
        gridPane.add(invertYLabel, col1, i);
        gridPane.add(invertYCheckBox, col2, i++);

        // ignore z stage
        Label ignoreZStageLabel = createLabel("Ignore Z stage");
        CheckBox ignoreZStageCheckBox = createCheckBox(options.isIgnoreZStagePosition());
        gridPane.add(ignoreZStageLabel, col1, i);
        gridPane.add(ignoreZStageCheckBox, col2, i++);

        // Subpixel accuracy
        Label subPixelAccuracyLabel = createLabel("Subpixel accuracy");
        CheckBox subPixelAccuracyCheckBox = createCheckBox(options.isSubpixelAccuracy());
        gridPane.add(subPixelAccuracyLabel, col1, i);
        gridPane.add(subPixelAccuracyCheckBox, col2, i++);

        // virtual
        Label virtualLabel = createLabel("Use virtual input images");
        CheckBox virtualCheckBox = createCheckBox(options.isUseVirtualInputImages());
        gridPane.add(virtualLabel, col1, i);
        gridPane.add(virtualCheckBox, col2, i++);

        // Separator
        Separator sep4 = new Separator();
        sep4.setOrientation(Orientation.HORIZONTAL);
        gridPane.add(sep4, 0, i++, 3, 1);

        // Computational parameters
        Label computationalParamLabel = createLabel("Computational parameters");
        ComboBox<GridStitchMacroOptions.ComputationParameter> computationParameterComboBox = createComputationParameterComboBox();
        gridPane.add(computationalParamLabel, col1, i);
        gridPane.add(computationParameterComboBox, col2, i++);

        // Output image name
        Label outputNameLabel = createLabel("Output image name");
        TextField outputNameTextField = new TextField(options.getFusedImageName());
        gridPane.add(outputNameLabel, col1, i);
        gridPane.add(outputNameTextField, col2, i++);

        // Separator
        Separator sep5 = new Separator();
        sep5.setOrientation(Orientation.HORIZONTAL);
        gridPane.add(sep5, 0, i++, 3, 1);

        // Run
        Button runButton = new Button("Run");
        runButton.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(runButton, col1, i++, 2, 1);

        ImageJ ij = IJExtension.getImageJInstance();
        ij.setVisible(false);
        IJ.debugMode = false;
        Interpreter interpreter = new Interpreter();
        Interpreter.batchMode = true;


        runButton.setOnAction(e -> {

            if (options.getDirectory()==null){
                Dialogs.showErrorMessage(TITLE, "Please select a directory");
                return;
            }

            // Shouldn't have to worry about null pointer
            options.setType(typeComboBox.getValue());
            options.setWidth(Integer.parseUnsignedInt(widthTextField.getText()));
            options.setHeight(Integer.parseUnsignedInt(heightTextField.getText()));
            options.setTileOverlap(Double.parseDouble(overlapTextField.getText()));
            options.setFirstFileIndex(Integer.parseUnsignedInt(firstFileIndexTextField.getText()));
            options.setFilenamesRegex(fileNamesTextField.getText());
            options.setTextFilename(textFileNameTextField.getText());
            options.setFusedImageName(outputNameTextField.getText());
            options.setFusionMethod(fusionMethodComboBox.getValue());

            options.setRegressionThreshold(Double.parseDouble(regressionThresholdTextField.getText()));
            options.setMaxAvgDisplacementThreshold(Double.parseDouble(maxAvgDisplacementThresholdTextField.getText()));
            options.setAbsoluteDisplacementThreshold(Double.parseDouble(absDisplacementThresholdTextField.getText()));

            options.setAddTilesAsROI(addToROICheckBox.isSelected());
            options.setComputeOverlap(computeOverlapCheckBox.isSelected());
            options.setInvertXCoordinates(invertXCheckBox.isSelected());
            options.setInvertYCoordinates(invertYCheckBox.isSelected());
            options.setIgnoreZStagePosition(ignoreZStageCheckBox.isSelected());
            options.setSubpixelAccuracy(subPixelAccuracyCheckBox.isSelected());

            // Maybe add in future
            // Needs a better way to close log window.
            String command = "setBatchMode(true);" + options.toString() + "if (isOpen(\"Log\")) {\n" +
                    "         selectWindow(\"Log\");\n" +
                    "         run(\"Close\" );\n" +
                    "    }";
            System.out.println(command);
            options.setUseVirtualInputImages(virtualCheckBox.isSelected());
            options.setComputationParameter(computationParameterComboBox.getValue());


            SwingUtilities.invokeLater(() -> {
                try {
                    interpreter.run(command);
                    Dialogs.showInfoNotification(TITLE, "Saved fused image" );
                } catch (Exception ex){
                    ex.printStackTrace();
                }
                }
            );


        });
    }

    @Override
    public void run() {
        if (stage == null){
            createStage();
        }
        stage.show();
    }

    public Stage getStage() {
        return stage;
    }

    // method to initialise the type combo box
    private ComboBox<Type> createTypeComboBox(){
        ComboBox<Type> typeComboBox = new ComboBox<>();
        typeComboBox.setItems(FXCollections.observableList(Arrays.asList(GridStitchMacroOptions.Type.values())));
        typeComboBox.setMaxWidth(Double.MAX_VALUE);
        typeComboBox.setConverter(new StringConverterWithFormat<>() {
            @Override
            public String toString(Type object) {
                return object.getName();
            }

            @Override
            public Type fromString(String string) {
                return typeComboBox
                        .getItems()
                        .stream()
                        .filter(p -> p.getName().equals(string))
                        .findFirst()
                        .orElse(null);
            }
        });
        typeComboBox.setValue(options.getType());
        return typeComboBox;
    }

    // Method to initialise the order combo box
    private ComboBox<String> createOrderComboBox(ComboBox<Type> typeComboBox){
        ComboBox<String> orderComboBox = new ComboBox<>();
        orderComboBox.setMaxWidth(Double.MAX_VALUE);
        orderComboBox.setItems(FXCollections.observableList(Arrays.asList(typeComboBox.getValue().getOrders())));
        orderComboBox.setValue(options.getType().getOrder());

        typeComboBox.valueProperty().addListener(e -> {
            orderComboBox.setItems(FXCollections.observableList(Arrays.asList(typeComboBox.getValue().getOrders())));
            orderComboBox.setValue(typeComboBox.getValue().getOrder());
        });

        return orderComboBox;
    }

    // Method to initialise the fusion method combo box
    private ComboBox<FusionMethod> createFusionMethodComboBox(){
        ComboBox<FusionMethod> fusionMethodComboBox = new ComboBox<>();
        fusionMethodComboBox.setMaxWidth(Double.MAX_VALUE);
        fusionMethodComboBox.setItems(FXCollections.observableList(Arrays.asList(GridStitchMacroOptions.FusionMethod.values())));
        fusionMethodComboBox.setConverter(new StringConverterWithFormat<>() {
            @Override
            public String toString(FusionMethod object) {
                return object.getName();
            }

            @Override
            public FusionMethod fromString(String string) {
                return fusionMethodComboBox
                        .getItems()
                        .stream()
                        .filter(p -> p.getName().equals(string))
                        .findFirst()
                        .orElse(null);
            }
        });
        fusionMethodComboBox.setValue(options.getFusionMethod());
        return fusionMethodComboBox;
    }

    // initialise computation parameter combo box
    private ComboBox<ComputationParameter> createComputationParameterComboBox(){
        ComboBox<ComputationParameter> computationParameterComboBox = new ComboBox<>();
        computationParameterComboBox.setMaxWidth(Double.MAX_VALUE);
        computationParameterComboBox.setItems(FXCollections.observableList(Arrays.asList(GridStitchMacroOptions.ComputationParameter.values())));

        computationParameterComboBox.setConverter(new StringConverterWithFormat<>() {
            @Override
            public String toString(ComputationParameter object) {
                return object.getName();
            }

            @Override
            public ComputationParameter fromString(String string) {
                return computationParameterComboBox
                        .getItems()
                        .stream()
                        .filter(p -> p.getName().equals(string))
                        .findFirst()
                        .orElse(null);
            }
        });
        computationParameterComboBox.setValue(options.getComputationParameter());
        return computationParameterComboBox;
    }

}
