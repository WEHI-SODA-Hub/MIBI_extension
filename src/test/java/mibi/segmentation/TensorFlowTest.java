package mibi.segmentation;



import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Objects;
import java.util.stream.Collectors;


//import org.bytedeco.tensorflow.*;
//import org.bytedeco.tensorflow.global.tensorflow;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.proto.framework.MetaGraphDef;
import org.tensorflow.proto.framework.SignatureDef;
import org.tensorflow.proto.framework.TensorInfo;
import qupath.ext.tensorflow.TensorFlowTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.projects.Projects;

import javax.imageio.ImageIO;


public class TensorFlowTest {

    public static void main(String[] args) throws IOException {
        String modelPath = "/Users/yokote.k/Desktop/MIBI/MultiplexSegmentation";
        String projectPath = "/Users/yokote.k/Desktop/MIBI/Test_proj";
        String imagePath = "/Users/yokote.k/Desktop/MIBI/images/Stack_dna-mem-channels.tif";


        File multiplexModel = new File(new File(new File(new File("src", "main"),
                                            "resources"),"model"),"MultiplexSegmentation");
        System.out.println(multiplexModel.getAbsoluteFile());

        var model = SavedModelBundle.load(multiplexModel.getAbsolutePath());
        final SignatureDef signatureDef = model.metaGraphDef().getSignatureDefMap().get("serving_default");
        final TensorInfo inputTensorInfo = (TensorInfo) signatureDef.getInputsMap()
                .values()
                .stream()
                .filter(Objects::nonNull)
                .findFirst().orElseThrow();

        final ArrayList<TensorInfo> outputTensorInfo = signatureDef.getOutputsMap()
                .values()
                .stream()
                .filter(Objects::nonNull).collect(Collectors
                        .toCollection(ArrayList::new));;
        System.out.println(inputTensorInfo);
        System.out.println(outputTensorInfo);


//        try {
//            var img = ImageIO.read(new File(imagePath));
//        } catch (IOException e) {
//
//        }
//        var sessionOptions = new SessionOptions();
//        var runOptions = RunOptions.default_instance();
//        var tags = new StringUnorderedSet();
//        tags.insert(tensorflow.kSavedModelTagServe());
//        var bundle = new SavedModelBundle();
//        var status = tensorflow.LoadSavedModel(
//                sessionOptions,
//                runOptions,
//                modelPath,
//                tags,
//                bundle
//        );

//        var model = TensorFlowTools.createDnnModel(modelPath);
//        System.out.println(model.getPredictionFunction().getInputs());
//        System.out.println(model.getPredictionFunction().getOutputs());
//        model.getPredictionFunction().predict();
//
//
//        MetaGraphDef graphDef = bundle.meta_graph_def();
//        StringSignatureDefMap sigdefMap = graphDef.signature_def();
//
//        SignatureDef map = sigdefMap.get(sigdefMap.begin().first());
//        var inputs = map.inputs();
//        System.out.println(inputs.size());
//
//        var outputs = map.outputs();
//        System.out.println(outputs.size());

    }
}
