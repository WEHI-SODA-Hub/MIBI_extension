package mibi.gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class NodeTools {

    private static final int DEFAULT_FONT_SIZE = 14;
    public static final int DEFAULT_TEXT_FIELD_WIDTH = 60;
    /**
     * Static method for creating a label with a fixed font size
     * @param msg
     * @return
     */
    public static Label createLabel(String msg){
        return createLabel(msg, DEFAULT_FONT_SIZE);
    }

    /**
     * Static method for creating a label
     * @param msg
     * @param fontSize
     * @return
     */
    public static Label createLabel(String msg, int fontSize) {
        Label label = new Label(msg);
        label.setFont(javafx.scene.text.Font.font(fontSize));
        label.setAlignment(Pos.CENTER);
        return label;
    }


    /**
     * Creates a textfield in which only a numerical value can be entered

     * @return
     */
    public static TextField createPositiveRealTextField(){
        TextField numericalTextField = new TextField();
        String regex = "\\d*([\\.]\\d*)?";
        numericalTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches(regex)) {
                numericalTextField.setText(oldValue);
            }
        });
        return numericalTextField;
    }

    public static String convertToNumericalString(String str){
        String strFinal = str;
        if (strFinal.length() == 1 && strFinal.charAt(0) == '-'){
            strFinal = "0";
        }
        if (strFinal.length() > 0 && strFinal.charAt(0)== '.'){
            strFinal = "0" + strFinal;
        }
        if (strFinal.length() > 1 && strFinal.startsWith("-.")){
            strFinal = "-0"+strFinal.substring(1);
        }
        if (strFinal.endsWith(".")|| strFinal.charAt(strFinal.length() - 1) == '-'){
            strFinal = strFinal.substring(0, strFinal.length()-1);
        }
        if (strFinal.equals("-0")){
            strFinal = "0";
        }
        return strFinal;
    }


    /**
     * Creates a textfield in which only a integer value can be entered
     * @param
     * @return
     */
    public static TextField createNaturalNumberTextField(){
        TextField numericalTextField = new TextField();
        String regex = "\\d*";
        numericalTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches(regex)) {
                numericalTextField.setText(oldValue);
            }
        });
        return numericalTextField;
    }


    public static boolean inHierarchy(Node node, Node potentialHierarchyElement) {
        if (potentialHierarchyElement == null) {
            return true;
        }
        while (node != null) {
            if (node == potentialHierarchyElement) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }


    /**
     * Creates a Slider
     */
    public static Slider createSlider(double defaultVal, double min, double max, boolean isIntegerJumps){
        Slider slider = new Slider();
        slider.setValue(defaultVal);
        slider.setMin(min);
        slider.setMax(max);
        if (isIntegerJumps) {
            slider.valueProperty().addListener((obs, oldVal, newVal) ->
                    slider.setValue(newVal.intValue()));
        }
        return slider;
    }

    public static void createSliderTextFieldPair(Slider slider, TextField textField){
        slider.valueProperty().addListener(e -> {
            if (slider.isValueChanging() || slider.isFocused()) {
                textField.setText(String.valueOf(slider.getValue()));
            }
        });

        textField.textProperty().addListener(e -> {
            slider.setValue(Double.parseDouble(textField.getText()));
        });
    }

    public static HBox createHBox(Node... nodes){
        HBox hBox = new HBox();
        hBox.setMaxWidth(Double.MAX_VALUE);
        hBox.getChildren().addAll(nodes);
        return hBox;
    }

    public static GridPane createGridPane(){
        GridPane gridPane =  new GridPane();
        gridPane.setVgap(5);
        gridPane.setHgap(5);
        gridPane.setPadding(new Insets(10, 10, 10, 10));
        return gridPane;
    }

}
