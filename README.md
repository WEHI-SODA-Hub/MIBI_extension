# MIBI extension

> **DEPRECATED. We do not recommend using this extension.**
> It was written for QuPath 0.3.x (2021–2022) and is not maintained. It has not been updated for later QuPath
> versions (0.4 and newer), whose extension API, JavaFX and Java versions differ. Several parts are unfinished
> (see [Known problems](#known-problems)). The code is kept here as a record of what was run.
> For new analyses, use a current QuPath release with a maintained segmentation workflow, for
> example the QuPath Cellpose or StarDist extensions, or run DeepCell/Mesmer directly.

A QuPath 0.3.2 extension for analysing MIBI (multiplexed ion beam imaging) data. It adds an
**Extensions > MIBI Extension** menu with three tools.

| Menu item | What it does |
|-----------|--------------|
| Stitching | Runs the ImageJ "Grid/Collection stitching" plugin on a folder of tiles and saves the fused image. |
| Segmentation | Segments nuclei and whole cells with the DeepCell Mesmer model (a TensorFlow SavedModel) and adds shape, intensity and percentile measurements. Runs on the selected annotations, or on every image in the project. |
| Quantile | Adds percentile intensity measurements to cells that already exist, for every image in the project. |

The Mesmer model is in `src/main/resources/model/MultiplexSegmentation`.
The code in `mibi/phenotype` (`AstirCellTypeClassifier`, `SCDataset`, `TypeRecognitionNet`, `PhenotypeHierarchy`)
is an unfinished start on an ASTIR-style cell-type classifier. It is not in the menu and does not classify anything.

## How it works

### Stitching

`GridStitchGUI` collects the settings, and `GridStitchMacroOptions` turns them into an ImageJ macro command for the
"Grid/Collection stitching" plugin. The command is run in a hidden ImageJ instance (the one QuPath's ImageJ extension
provides). The plugin writes the tile configuration file into the image folder, and the fused image is saved
as a TIFF in the same folder.

### Segmentation

`SegmentationGUI` collects the settings and builds a `DeepCell2D` detector. For each tile, `DeepCell2D`:

1. extracts the nuclear channel, and multiplies the selected membrane channels together into one membrane channel (`DeepCellImageOps`);
2. applies CLAHE to each channel, then scales it to 0–1 by its own minimum and maximum (`DeepCellPreprocessOp`);
3. runs the model, which returns whole-cell and nuclear maxima and interior maps;
4. converts the maps to label images with `DeepWatershed`: an h-maxima step finds the cell centres, and a
   marker-controlled watershed (MorphoLibJ) grows them inside the thresholded interior map. The h-maxima
   threshold is 0.1, reduced by the segmentation level (below), and the interior threshold is 0.3;
5. matches each nucleus to the whole-cell label whose centroid is within 2 px. If there is none, the cell
   boundary is estimated from the nucleus with twice the cell expansion;
6. resolves overlaps, keeping the larger cell, and drops cells that touch a tile edge;
7. adds the requested measurements.

The result is QuPath cell objects with a nucleus ROI and a cell ROI. Existing child objects of the segmented
annotation are replaced.

### Quantile

`QuantileGUI` computes percentile intensities of every channel for each cell object in every project image,
using `AdditionalObjectMeasurements`, and saves each image.

## Building

Requirements: a JDK, a QuPath 0.3.2 installation, and the QuPath TensorFlow extension 0.3.0
(`qupath-extension-tensorflow-0.3.0.jar`, from the
[qupath-extension-tensorflow releases](https://github.com/qupath/qupath-extension-tensorflow/releases/tag/v0.3.0)).
QuPath 0.3.2 is available from the [QuPath releases](https://github.com/qupath/qupath/releases/tag/v0.3.2).

The build needs two Gradle properties that point at those files. Set them in `~/.gradle/gradle.properties`:

```
qupathAppDir=/path/to/QuPath-0.3.2/app
qupathTensorflowJar=/path/to/qupath-extension-tensorflow-0.3.0.jar
```

or pass them on the command line:

```
./gradlew shadowJar -PqupathAppDir=/path/to/QuPath-0.3.2/app -PqupathTensorflowJar=/path/to/qupath-extension-tensorflow-0.3.0.jar
```

`qupathAppDir` is the folder that contains the QuPath jars (`QuPath-0.3.2.app/Contents/app` on macOS, `QuPath/lib/app`
in the Linux archive). The build stops with a message if either property is missing. The jar is written to `build/libs`.

Notes:

- The wrapper uses Gradle 7.0, which runs on JDK 8 to 16 only, and the build needs the `1.5.7-SNAPSHOT`
  javacpp plugin and libraries from the Sonatype snapshot repository. The Gradle build has not been run since the
  paths were made configurable.
- The main sources were also compiled directly with `javac --release 17` against the QuPath 0.3.2 jars, the
  TensorFlow extension 0.3.0 jar, `MorphoLibJ_` 1.4.0, `tensorflow-core-api` 0.4.0, `ndarray` 0.3.3 and `protobuf-java` 3.19.2.
  All classes compiled, and QuPath's extension loader found `mibi.MIBIExtension`.
  The extension has not been opened in the QuPath window since these changes.

## Installing

1. Install QuPath 0.3.2. Other versions are not supported.
2. Put these jars in QuPath's extensions folder (set under **Edit > Preferences > Extension directory**), or drag them
   onto the QuPath window:
   - the MIBI extension jar (`MIBI_Extension-<version>.jar`);
   - the QuPath TensorFlow extension jar, `qupath-extension-tensorflow-0.3.0.jar`;
   - `MorphoLibJ_-1.4.0.jar`;
   - `tensorflow-core-api-0.4.0.jar` and its native jar for your system (for example `tensorflow-core-api-0.4.0-linux-x86_64.jar`), with `ndarray-0.3.3.jar` and `protobuf-java-3.19.2.jar`.

   The `shadowJar` build bundles MorphoLibJ, the TensorFlow extension and `tensorflow-core-platform` (all operating systems) into one jar.
3. Restart QuPath. **Extensions > MIBI Extension** should list Stitching, Segmentation and Quantile.
4. Keep the model folder (`src/main/resources/model/MultiplexSegmentation`, containing `saved_model.pb` and `variables/`)
   somewhere on disk. The Segmentation window asks for this folder.

Starting QuPath on Java 17 or later needs `--add-opens java.base/java.lang=ALL-UNNAMED`, which TensorFlow's native
loader uses. QuPath 0.3.2's own launcher sets `--illegal-access=permit`.

## Settings

### Stitching

An image must be open in QuPath. Defaults are shown in brackets.

| Setting | Meaning |
|---------|---------|
| Grid type [column-by-column] | How tile positions are laid out: row-by-row, column-by-column, snake by rows, snake by columns, or defined by the filename. |
| Order | Start corner and direction. The order chosen in the window is not applied (see [Known problems](#known-problems)). |
| Width / height of grid [3 / 3] | Number of tiles in x and y. |
| Tile overlap percentage [50] | Expected overlap between neighbouring tiles. |
| Index of the first image [0] | Value of `{i}` for the first tile. |
| Pattern of the image names [`image{i}.tif`] | Tile filenames, with `{i}` replaced by the tile index. |
| Directory | Folder containing the tiles. The output is written here. |
| Output text file [`TileConfiguration.txt`] | Name of the tile configuration file. |
| Fusion method [Linear Blending] | Linear Blending, Average, Median, Max. Intensity, Min. Intensity, intensity of a random input tile, or do not fuse (write the tile configuration only). |
| Regression threshold [0.3] | Minimum correlation for a pair of tiles to be accepted. |
| Max/avg displacement threshold [2.5] | Maximum displacement, relative to the average, before a link is rejected. |
| Absolute displacement threshold [3.5] | Maximum absolute displacement before a link is rejected. |
| Add tiles to ROI | Add the tile outlines as ROIs. |
| Create overlap | Compute the overlap from the tile positions. |
| Invert X / Invert Y | Flip the tile coordinates. |
| Ignore Z stage | Ignore the z stage position. |
| Subpixel accuracy | Use subpixel registration. |
| Use virtual input images | Read tiles from disk instead of loading them all. |
| Computational parameters [Save memory] | Save memory (slower) or save computation time (more RAM). |
| Output image name [`Fused.tif`] | Name of the fused TIFF saved in the directory. |

### Segmentation

An image must be open in QuPath. Defaults are shown in brackets.

| Setting | Meaning |
|---------|---------|
| Model folder | The Mesmer SavedModel folder. The model must have exactly 4 outputs. |
| Nuclei channel | The channel used as the nuclear input. |
| Membrane channels | One or more channels, multiplied together into one membrane input. Add and remove them with the + and − buttons. At least one is required. |
| Include marker measurements [on] | Adds mean, median, min, max and standard deviation of every channel in the nucleus, cytoplasm, membrane and whole cell. |
| Include shape measurements [on] | Adds QuPath's shape measurements for each cell and nucleus. |
| Minimum nuclei area [0] | Nuclei with an area of this many pixels² or less are dropped. |
| Cell expansion [3] | Distance in pixels used when estimating a cell boundary. Used for cells with no matching whole-cell label; twice this value is used. |
| Cell constrain scale [1] | Passed to QuPath's `CellTools.estimateCellBoundary`. |
| Padding around tiles [64] | Pixels added around each 256 × 256 tile to reduce edge artefacts. |
| Number of threads [1] | 1 leaves the thread count at Java's default parallelism; larger values use that many threads. |
| Segmentation level [5] | Slider from 0 to 10. Higher values reduce the h-maxima threshold, which finds more cell centres and splits cells more. 0 gives a factor of 0, 5 gives 0.001, and 10 gives 0.9. |

Percentile measurements at 95, 90, 80 and 70 are always added to the intensity measurements, with names
such as `<channel>: Cell: Percentile: 95.0`. They are not available as a setting in this window.

**Run Segmentation** segments the selected annotations. If nothing is selected, it segments the whole image.
**Run Segmentation for Current Project** repeats this for every image in the project and saves each one; reopen the
current image afterwards to see the new cells. **Refresh window** reloads the channel lists after the image changes.
The channel choices are remembered per image while QuPath is open.

### Quantile

A project must be open in QuPath. Enter a percentile between 0 and 100 (for example 95) and press **Insert**.
**Run For Project** adds `<channel>: <Nucleus|Cytoplasm|Membrane|Cell>: Percentile: <value>` measurements to every
cell in every project image, and saves each image.

## Known problems

- Stitching: the order chosen in the **Order** box is ignored; the first order for the chosen grid type is always used.
  The image is always fused, displayed and saved as a TIFF.
- Quantile: **Remove** does nothing, because it compares a string with a list of numbers.
- Segmentation: tiles and cells are processed with parallel streams that add to non-thread-safe lists, so results
  with more than one thread may differ between runs.
- Segmentation always runs at full image resolution, and the percentile list (95, 90, 80, 70) is fixed in the code.
- There are no automated tests of the segmentation. The files in `src/test` are small manual launchers.
- Only QuPath 0.3.2 and the Linux load check described above have been tried since the code was moved to this
  repository.

## License

GPL-3.0, see `LICENSE`.
