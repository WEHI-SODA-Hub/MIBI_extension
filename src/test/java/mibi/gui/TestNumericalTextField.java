package mibi.gui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import qupath.lib.gui.tools.PaneTools;

public class TestNumericalTextField extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        GridPane gp = PaneTools.createColumnGrid(2);


        Scene scene = new Scene(gp);
        TextField tf = NodeTools.createPositiveRealTextField();
        Slider slider = NodeTools.createSlider(50,0, 100, true);
        NodeTools.createSliderTextFieldPair(slider, tf);

        gp.add(tf, 0, 0);
        gp.add(slider, 1, 0);

        stage.setScene(scene);
        stage.show();
    }
}
