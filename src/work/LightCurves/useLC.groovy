// tbd

// Deeplearning4j
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader;
import org.datavec.api.split.NumberedFileInputSplit;
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.InvocationType;
import org.deeplearning4j.optimize.listeners.EvaluativeListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.deeplearning4j.eval.ROC;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.Nadam;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.common.primitives.Pair;
import org.nd4j.linalg.api.ndarray.BaseNDArray;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.NDArrayIndex;

// Log
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

// Apache
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

// Java
import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// -----------------------------------------------------------------------------

log = LogManager.getLogger(this.class);

conf = MultiLayerConfiguration.fromJson(new File("../run/network.json").text);
net = new MultiLayerNetwork(conf);
net.init();
net.load(new File("../run/network.model"), false);

batchSize = 1;    // Number of samples
inputSize = 1;      // Number of features per time step (must match your network's input layer)
timeSteps = 100;     // Number of time steps (adjust based on your model)

arr = new double[100]
(0..<100).each {i -> arr[i] = Math.random()}

curve = Nd4j.create(arr, new int[]{batchSize, inputSize, timeSteps});

output = net.output(curve); // For raw output (e.g., regression)
lastTimeStep = output.get(NDArrayIndex.all(), NDArrayIndex.all(), NDArrayIndex.point(timeSteps - 1)); // Shape: [batchSize, nOut]
predictions = lastTimeStep.argMax(1).toIntVector(); // Get class indices
log.info("Predicted classes: " + Arrays.toString(predictions));




