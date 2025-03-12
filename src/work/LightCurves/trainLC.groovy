// read light curve data from all.lst file
// and train them using UCISequenceClassification

// Krakev
import com.Krakev.deeplearning4j.Utils.DataOrganizer;

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
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.learning.config.Nadam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.common.primitives.Pair;

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

miniBatchSize = 10;
numLabelClasses = 5;
blockSize = 50;
trainSize = (int)(numLabelClasses * blockSize * 0.75);
testSize =  (int)(numLabelClasses * blockSize - trainSize);
nEpochs = 40;

// Get data
// gr.lst file should contain numLabelClasses sets of training data,
//        each set with blockSize of cases

c = new DataOrganizer("../data/lc/");
data = new File("../data/gr.lst").text;
c.prepareData(data, blockSize, trainSize);

// Initialise data

trainFeatures = new CSVSequenceRecordReader();
trainFeatures.initialize(new NumberedFileInputSplit(c.featuresDirTrain.getAbsolutePath() + "/%d.csv", 0, trainSize - 1));
trainLabels = new CSVSequenceRecordReader();
trainLabels.initialize(new NumberedFileInputSplit(c.labelsDirTrain.getAbsolutePath() + "/%d.csv", 0, trainSize - 1));

trainData = new SequenceRecordReaderDataSetIterator(trainFeatures,
                                                    trainLabels,
                                                    miniBatchSize,
                                                    numLabelClasses,
                                                    false,
                                                    SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_END);

normalizer = new NormalizerStandardize();
normalizer.fit(trainData);
trainData.reset();
trainData.setPreProcessor(normalizer);

testFeatures = new CSVSequenceRecordReader();
testFeatures.initialize(new NumberedFileInputSplit(c.featuresDirTest.getAbsolutePath() + "/%d.csv", 0, testSize - 1));
testLabels = new CSVSequenceRecordReader();
testLabels.initialize(new NumberedFileInputSplit(c.labelsDirTest.getAbsolutePath() + "/%d.csv", 0, testSize - 1));

testData = new SequenceRecordReaderDataSetIterator(testFeatures,
                                                   testLabels,
                                                   miniBatchSize,
                                                   numLabelClasses,
                                                   false,
                                                   SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_END);

testData.setPreProcessor(normalizer);

// Prepare NN configuration

conf = new NeuralNetConfiguration.Builder()
                                 .seed(123)
                                 .weightInit(WeightInit.XAVIER)
                                 .updater(new Nadam())
                                 .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                                 .gradientNormalizationThreshold(0.5)
                                 .list()
                                 .layer(new LSTM.Builder()
                                                .activation(Activation.TANH)
                                                .nIn(1)
                                                .nOut(10)
                                                .build())
                                 .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                                                          .activation(Activation.SOFTMAX)
                                                          .nIn(10)
                                                          .nOut(numLabelClasses)
                                                          .build())
                                 .build();

net = new MultiLayerNetwork(conf);
net.init();

// Train

log.info("Starting training...");
net.setListeners(new ScoreIterationListener(20), new EvaluativeListener(testData, 1, InvocationType.EPOCH_END));
net.fit(trainData, nEpochs);

// Test

log.info("Evaluating...");
eval = net.evaluate(testData);
log.info(eval.stats());

log.info("Complete");

// -----------------------------------------------------------------------------

