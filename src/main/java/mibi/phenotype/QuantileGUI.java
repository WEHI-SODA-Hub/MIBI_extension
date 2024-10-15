package mibi.phenotype;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.analysis.features.AdditionalObjectMeasurements;
import qupath.lib.analysis.features.ObjectMeasurements;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.images.servers.TransformedServerBuilder;
import qupath.lib.objects.PathCellObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static mibi.gui.NodeTools.*;

import static mibi.gui.NodeTools.createGridPane;

public class QuantileGUI implements Runnable{
    private QuPathGUI qupath;
    private String TITLE = "Quantile";
    private Stage stage;
    private List<Double> quantiles;
    private List<ObjectMeasurements.Compartments> compartments = Arrays.asList(ObjectMeasurements.Compartments.values());

    public QuantileGUI(QuPathGUI qupathGUI){
        this.qupath = qupathGUI;
    }

    public void run(){
        var project = qupath.getProject();
        if (project == null){
            Dialogs.showErrorMessage(TITLE, "Project is empty. Please open an image.");
            return;
        }
        if (stage == null){
            createStage();
            stage.initOwner(qupath.getStage());;
        }
        stage.show();
    }

    public void createStage(){
        stage = new Stage();
        stage.resizableProperty().set(false);
        stage.setTitle(TITLE);

        quantiles = new ArrayList<>();

        GridPane gridPane = createGridPane();

        var quantileLabel = createLabel("Quantile");
        var quantileTextField = createPositiveRealTextField();
        var quantileInsertButton = new Button("Insert");
        var quantileRemoveButton = new Button("Remove");

        gridPane.add(quantileLabel, 0, 0);
        gridPane.add(quantileTextField, 1, 0);
        gridPane.add(quantileInsertButton, 2, 0);
        gridPane.add(quantileRemoveButton, 3, 0);



        var quantileDisplayTextField = new TextField();

        gridPane.add(quantileDisplayTextField, 0, 1, 4, 1);

        quantileInsertButton.setOnAction((event) -> {
            var selected = Double.parseDouble(quantileTextField.getText());
            if (!quantiles.contains(selected)){
                quantiles.add(selected);
                String text = quantiles.toString();
                quantileDisplayTextField.setText(text.substring(1, text.length() - 1));
            }
        });

        quantileRemoveButton.setOnAction((event) ->{
            var selected = quantileTextField.getText();
            if (quantiles.contains(selected)){
                quantiles.remove(selected);
                String text = quantiles.toString();
                quantileDisplayTextField.setText(text.substring(1, text.length()-1));
            };
        });

        var quantileRun = new Button("Run For Project");
        gridPane.add(quantileRun, 0, 2, 4, 1);
        quantileRun.setOnAction((event) -> {
            if (qupath.getProject() == null){
                Dialogs.showErrorMessage(TITLE, "Could not find a project!");
                return;
            }

            if (Dialogs.showConfirmDialog(TITLE, "Are you sure you want to run for the entire project? This may take a very long time")){
                for (var entry : qupath.getProject().getImageList()) {
                    try {
                        var imageData = entry.readImageData();
                        var cells = imageData.getHierarchy().getCellObjects();
                        var server = imageData.getServer();

                        var stains = imageData.getColorDeconvolutionStains();
                        var builder = new TransformedServerBuilder(server);
                        if (stains != null) {
                            List<Integer> stainNumbers = new ArrayList<>();
                            for (int s = 1; s <= 3; s++) {
                                if (!stains.getStain(s).isResidual())
                                    stainNumbers.add(s);
                            }
                            builder.deconvolveStains(stains, stainNumbers.stream().mapToInt(i -> i).toArray());
                        }
                        var resolution = imageData.getServer().getPixelCalibration();
                        var server2 = builder.build();
                        var cal = server.getPixelCalibration();
                        double downsample = resolution.getAveragedPixelSize().doubleValue() / cal.getAveragedPixelSize().doubleValue();

                        cells.parallelStream().forEach(cell -> {
                                    try {
                                        AdditionalObjectMeasurements.addPercentileIntensityMeasurements(server2, (PathCellObject) cell, downsample, quantiles, compartments);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            );

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


        var scene = new Scene(gridPane);

        stage.setScene(scene);
    }
}
