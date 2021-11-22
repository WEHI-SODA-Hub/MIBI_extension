package mibi.stitch;


import java.io.File;
import java.util.ArrayList;

public class GridStitchMacroOptions {

    private Type type = Type.GRID_COL_COL;

    // The grid size
    private int width = 3;
    private int height = 3;

    // The tile overlap
    private double tileOverlap = 50;

    // first file index
    private int firstFileIndex = 0;

    // The directory where images are stored;
    private File directory = null;

    // The pattern each filename follows
    private String filenamesRegex = "image{i}.tif";

    // Output text filename
    private String textFilename = "TileConfiguration.txt";

    // Output image name
    private String fusedImageName = "Fused.tif";

    // Fusion Method
    private FusionMethod fusionMethod = FusionMethod.LINEAR_BLENDING;

    // thresholds
    private double regressionThreshold = 0.3;
    private double maxAvgDisplacementThreshold = 2.50;
    private double absoluteDisplacementThreshold = 3.50;

    private boolean addTilesAsROI = false;
    private boolean computeOverlap = false;
    private boolean invertXCoordinates = false;
    private boolean invertYCoordinates = false;
    private boolean ignoreZStagePosition = false;
    private boolean subpixelAccuracy = false;
    private boolean downSampleTiles = false;
    private boolean displayFusion = false;
    private boolean useVirtualInputImages = false;

    private ComputationParameter computationParameter = ComputationParameter.SAVE_MEMORY;
    private ImageOutput imageOutput = ImageOutput.DISPLAY;

    @Override
    public String toString(){

        StringBuilder sb = new StringBuilder();
        sb.append("run(");
        sb.append("\"Grid/Collection stitching\", \"");
        sb.append(String.format("type=[%s] ", type.getName()));
        sb.append(String.format("order=[%s                ] ", type.getOrder()));
        sb.append(String.format("grid_size_x=%d ",width));
        sb.append(String.format("grid_size_y=%d ", height));
        sb.append(String.format("tile_overlap=%f ", tileOverlap));
        sb.append(String.format("first_file_index_i=%d ", firstFileIndex));
        sb.append(String.format("directory=[%s] ", directory.getAbsolutePath()));
        sb.append(String.format("file_names=[%s] ", filenamesRegex));
        sb.append(String.format("output_textfile_name=[%s] ", textFilename));
        sb.append(String.format("fusion_method=[%s] ", fusionMethod.getName()));
        sb.append(String.format("regression_threshold=%f ", regressionThreshold));
        sb.append(String.format("max/avg_displacement_threshold=%f ", maxAvgDisplacementThreshold));
        sb.append(String.format("absolute_displacement_threshold=%f ", absoluteDisplacementThreshold));

        if (addTilesAsROI){sb.append("add_tiles_as_rois ");}
        if (computeOverlap){sb.append("compute_overlap ");}
        if (invertXCoordinates){sb.append("invert_x "); }
        if (invertYCoordinates){sb.append("invert_y "); }
        if (ignoreZStagePosition) {sb.append("ignore_z_stage ");}
        if (subpixelAccuracy) {sb.append("subpixel_accuracy "); }
        // TODO: probably don't need Downsampling
//        if (downSampleTiles) {sb.append("downsample_tiles "); }
        // TODO: probably don't need display fusion
//        if (displayFusion) {sb.append("display_fusion ");}
        if (useVirtualInputImages) {sb.append("use_virtual_input_images "); }

        sb.append(String.format("computation_parameters=[%s] ", computationParameter.getName()));
        sb.append(String.format("image_output=[%s]\");", imageOutput.getName()));

        if (imageOutput == ImageOutput.DISPLAY){
            File fusedFile = new File(directory, fusedImageName);
            sb.append(String.format("saveAs(\"Tiff\", \"%s\");", fusedFile.getAbsolutePath()));
        }

        return sb.toString();
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public String getFusedImageName() {
        return fusedImageName;
    }

    public void setFusedImageName(String fusedImageName) {
        this.fusedImageName = fusedImageName;
    }

    public double getTileOverlap() {
        return tileOverlap;
    }

    public void setTileOverlap(double tileOverlap) {
        this.tileOverlap = tileOverlap;
    }

    public int getFirstFileIndex() {
        return firstFileIndex;
    }

    public void setFirstFileIndex(int firstFileIndex) {
        this.firstFileIndex = firstFileIndex;
    }

    public File getDirectory() {
        return directory;
    }

    public void setDirectory(File directory) {
        this.directory = directory;
    }

    public String getFilenamesRegex() {
        return filenamesRegex;
    }

    public void setFilenamesRegex(String filenamesRegex) {
        this.filenamesRegex = filenamesRegex;
    }

    public String getTextFilename() {
        return textFilename;
    }

    public void setTextFilename(String textFilename) {
        this.textFilename = textFilename;
    }

    public FusionMethod getFusionMethod() {
        return fusionMethod;
    }

    public void setFusionMethod(FusionMethod fusionMethod) {
        this.fusionMethod = fusionMethod;
    }

    public double getRegressionThreshold() {
        return regressionThreshold;
    }

    public void setRegressionThreshold(double regressionThreshold) {
        this.regressionThreshold = regressionThreshold;
    }

    public double getMaxAvgDisplacementThreshold() {
        return maxAvgDisplacementThreshold;
    }

    public void setMaxAvgDisplacementThreshold(double maxAvgDisplacementThreshold) {
        this.maxAvgDisplacementThreshold = maxAvgDisplacementThreshold;
    }

    public double getAbsoluteDisplacementThreshold() {
        return absoluteDisplacementThreshold;
    }

    public void setAbsoluteDisplacementThreshold(double absoluteDisplacementThreshold) {
        this.absoluteDisplacementThreshold = absoluteDisplacementThreshold;
    }

    public boolean isAddTilesAsROI() {
        return addTilesAsROI;
    }

    public void setAddTilesAsROI(boolean addTilesAsROI) {
        this.addTilesAsROI = addTilesAsROI;
    }

    public boolean isComputeOverlap() {
        return computeOverlap;
    }

    public void setComputeOverlap(boolean computeOverlap) {
        this.computeOverlap = computeOverlap;
    }

    public boolean isInvertXCoordinates() {
        return invertXCoordinates;
    }

    public void setInvertXCoordinates(boolean invertXCoordinates) {
        this.invertXCoordinates = invertXCoordinates;
    }

    public boolean isInvertYCoordinates() {
        return invertYCoordinates;
    }

    public void setInvertYCoordinates(boolean invertYCoordinates) {
        this.invertYCoordinates = invertYCoordinates;
    }

    public boolean isIgnoreZStagePosition() {
        return ignoreZStagePosition;
    }

    public void setIgnoreZStagePosition(boolean ignoreZStagePosition) {
        this.ignoreZStagePosition = ignoreZStagePosition;
    }

    public boolean isSubpixelAccuracy() {
        return subpixelAccuracy;
    }

    public void setSubpixelAccuracy(boolean subpixelAccuracy) {
        this.subpixelAccuracy = subpixelAccuracy;
    }

    public boolean isDownSampleTiles() {
        return downSampleTiles;
    }

    public void setDownSampleTiles(boolean downSampleTiles) {
        this.downSampleTiles = downSampleTiles;
    }

    public boolean isDisplayFusion() {
        return displayFusion;
    }

    public void setDisplayFusion(boolean displayFusion) {
        this.displayFusion = displayFusion;
    }

    public boolean isUseVirtualInputImages() {
        return useVirtualInputImages;
    }

    public void setUseVirtualInputImages(boolean useVirtualInputImages) {
        this.useVirtualInputImages = useVirtualInputImages;
    }

    public ComputationParameter getComputationParameter() {
        return computationParameter;
    }

    public void setComputationParameter(ComputationParameter computationParameter) {
        this.computationParameter = computationParameter;
    }

    public ImageOutput getImageOutput() {
        return imageOutput;
    }

    public void setImageOutput(ImageOutput imageOutput) {
        this.imageOutput = imageOutput;
    }

    /**
     * The enumeration for how the grid position will be determined
     */
    public enum Type {
        GRID_ROW_ROW("Grid: row-by-row", new String[]{"Right & Down", "Left & Down", "Right & Up", "Left & Up"}),
        GRID_COL_COL("Grid: column-by-column",new String[]{"Down & Right", "Down & Left", "Up & Right", "Up & Left"}),
        GRID_SNAKE_ROW("Grid: snake by rows", new String[]{"Right & Down", "Left & Down", "Right & Up", "Left & Up"}),
        GRID_SNAKE_COL("Grid: snake by columns", new String[]{"Down & Right", "Down & Left", "Up & Right", "Up & Left"}),
        POSITION_FILENAME("Filename defined position", new String[]{"Defined by filename"});
//        UNKNOWN("Unknown position", new String[]{"All files in directory"}),
//        POSITION_FROM_FILE("Position from file", new String[]{"Defined by TileConfiguration", "Defined by image metadata"}),
//        SEQUENTIAL_IMAGES("Sequential Images", new String[]{"All files in directory"});

        private final String[] orders;
        private String order;
        private final String name;
        String[] getOrders(){return orders;}
        void setOrder(String order){this.order = order;}
        String getOrder(){return order;}
        String getName(){return name;}
        private Type(String name, String[] orders){
            this.orders = orders;
            this.name = name;
            this.order = orders[0];
        }
    }

    /**
     * Fusion methods
     */
    public enum FusionMethod{
        LINEAR_BLENDING("Linear Blending"),
        AVERAGE("Average"),
        MEDIAN("Median"),
        MAX_INTENSITY("Max. Intensity"),
        MIN_INTENSITY("Min. Intensity"),
        RANDOM_INPUT_FILE("Intensity of random input tile"),
        NO_FUSE("Do not fuse images (Only write TileConfiguration)");

        private String name;
        private FusionMethod(String name){
            this.name = name;
        }
        String getName(){return name;}
    }

    /**
     * Computation Parameter in Grid Stitch
     */
    public enum ComputationParameter {
        SAVE_MEMORY("Save memory (but be slower)"),
        SAVE_TIME("Save computation time (but use more RAM)");

        private String name;
        private ComputationParameter(String name){
            this.name = name;
        }
        String getName(){return name;}
    }

    /**
     * Image output
     */
    public enum ImageOutput {
        DISPLAY("Fuse and display"),
        WRITE("Write to disk");

        private String name;
        private ImageOutput(String name){
            this.name = name;
        }
        String getName(){
            return name;
        }
    }
}
