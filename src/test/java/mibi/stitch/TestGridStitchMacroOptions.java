package mibi.stitch;


import ij.macro.Interpreter;

import java.io.File;


public class TestGridStitchMacroOptions {

    public static void main(String[] args) {
        GridStitchMacroOptions gs = new GridStitchMacroOptions();

        gs.setDirectory(new File("/Users/yokote.k/Desktop/test_stitch/images"));
        gs.setFilenamesRegex("test_image{i}.tif");
        gs.setWidth(1);
        gs.setHeight(2);

        System.out.println(gs.toString());


    }
}
