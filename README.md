# MIBI extension

A QuPath 0.3.2 extension for analysing MIBI (multiplexed ion beam imaging) data. It adds an
**Extensions > MIBI Extension** menu with three tools.

| Menu item | What it does |
|-----------|--------------|
| Stitching | Runs the ImageJ "Grid/Collection stitching" plugin on a folder of tiles and saves the fused image. |
| Segmentation | Segments nuclei and whole cells with the DeepCell Mesmer model (TensorFlow SavedModel) and adds shape, intensity and percentile measurements. Runs on the selected annotations, or on every image in the project. |
| Quantile | Adds percentile intensity measurements to cells that already exist, for every image in the project. |

The Mesmer model is bundled in `src/main/resources/model/MultiplexSegmentation`.
The phenotyping code in `mibi/phenotype` (`AstirCellTypeClassifier`, `SCDataset`, `TypeRecognitionNet`)
is unfinished and is not available from the menu.

## Segmentation

For each tile, `DeepCell2D`:

1. takes the nuclear channel, and multiplies the selected membrane channels together into one membrane channel;
2. applies CLAHE and min-max scaling to each channel;
3. runs the model, which returns whole-cell and nuclear maxima and interior maps;
4. converts the maps to label images with `DeepWatershed` (h-maxima, then marker-controlled watershed, using MorphoLibJ);
5. matches each nucleus to the whole-cell label whose centroid lies within 2 px, or expands the nucleus by the cell expansion if there is none;
6. resolves overlapping cells and drops cells touching a tile edge.

## Building

Requirements: a JDK (the build uses JavaFX 11), a QuPath 0.3.2 installation, and the QuPath TensorFlow extension
jar for 0.3.0 (`qupath-extension-tensorflow-0.3.0.jar`).

The build needs two properties that point at those files. Set them in `~/.gradle/gradle.properties`:

```
qupathAppDir=/path/to/QuPath-0.3.2/app
qupathTensorflowJar=/path/to/qupath-extension-tensorflow-0.3.0.jar
```

or pass them on the command line:

```
./gradlew shadowJar -PqupathAppDir=/path/to/QuPath-0.3.2/app -PqupathTensorflowJar=/path/to/qupath-extension-tensorflow-0.3.0.jar
```

`qupathAppDir` is the folder that contains the QuPath jars (`QuPath-0.3.2.app/Contents/app` on macOS).
The build fails with a message if either property is missing.

The extension jar is written to `build/libs`. Copy it into QuPath's extensions folder together with
the TensorFlow extension.

## Using the segmentation tool

1. Open an image or project in QuPath and choose **Extensions > MIBI Extension > Segmentation**.
2. Choose the model folder (the `MultiplexSegmentation` folder containing `saved_model.pb`).
3. Choose the nuclear channel and one or more membrane channels.
4. Set the minimum nucleus area, cell expansion, tile padding and number of threads.
5. Set the segmentation level (0 to 10): a higher value splits cells more.
6. Select the annotations to segment, then choose **Run Segmentation**. With no annotation selected, the whole image is segmented.
   **Run Segmentation for Current Project** repeats this for every image in the project and saves each image.
   Reopen the current image afterwards to see the new cells.

## License

GPL-3.0, see `LICENSE`.
