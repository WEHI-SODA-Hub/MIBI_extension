# MIBI analysis pipeline

The QuPath extension for analysing MIBI data.

# Current Stage  
- Stitching using the imageJ plugin has been complete. 
- Segmentation: 
      - The conversion of the savedModel folder to an opencv compatible frozen_graph.pb did not work. StarDist was trained using tensorflow 1.12. Possible reasons may be that tf2onnx may only work with tensorflow 1.12? not sure. 
      - We cannot implement the model simply using TensorflowOps.java like what was done in StarDist2D.java in qupath v0.2.3. This is because TensorflowOps only allows for one input and one output. Since the DeepCell model has 4 outputs, we need to write our own way of processing the output. https://github.com/qupath/qupath-extension-tensorflow/blob/6e1d772dc943b6029903a488e2a68aba889b4cc3/src/main/java/qupath/ext/tensorflow/TensorFlowOp.java line 82 for reference. 
      - Currently following what the mesmer.py does in the DeepCell library https://github.com/vanvalenlab/deepcell-tf/blob/e675a68ee824eb78ec24b3bf1f2be599e9d8da69/deepcell/applications/mesmer.py#L76 
      - Outputs of the model are : first Tensor is the Maxima black and white "image" for the whole-cell segmentation and the second tensor is the distance transforms for the whole-cell segmentations. Both are used for watershed segmentation which then creates the labelled images. Likewise, the third Tensor is the maxima for the nuclear, and last is the distance transform for the nuclear. 
      - DeepCell uses the skimage.morphology watershed function. We could possibly use the JavaCV (java opencv) watershed function. Should do the same. There are also other functions from skimage which have been used but there are openCV counterparts. 
      - The inputs for the model is a 4 dimensional Tensor. (-1, 256, 256, 2). Has to have 2 channels and has to be a square with sides 256. We need to tile the whole image before we can send it through the model
      - Another consideration: there are some pre-processing steps applied to the input before sending it through the model. 

# Our 2 Major Steps
- Being able to pre-process, tile, load the image and send it through the model
- Being able to collect the outputs, apply watershed segmentation to the outputs, then convert the outputs to detection objects in qupath

