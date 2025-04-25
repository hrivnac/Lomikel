@Grab('tech.tablesaw:tablesaw-core:0.43.1')
@Grab('org.apache.commons:commons-math3:3.6.1')
@Grab('org.apache.logging.log4j:log4j-api:2.24.3')
@Grab('org.apache.logging.log4j:log4j-core:2.24.3')
@Grab('org.slf4j:slf4j-api:1.7.30')
@Grab('ch.qos.logback:logback-classic:1.2.11')
@Grab('org.codehaus.groovy:groovy-json:3.0.21')
@Grab('org.deeplearning4j:deeplearning4j-core:1.0.0-beta6')
@Grab('org.nd4j:nd4j-native-platform:1.0.0-beta6')
@Grab('org.nd4j:nd4j-native:1.0.0-beta6')
@Grab('org.deeplearning4j:deeplearning4j-utility-iterators:1.0.0-beta6')

// Groovy
import groovy.json.JsonSlurper

// DL4J
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader
import org.datavec.api.records.reader.SequenceRecordReader
import org.datavec.api.split.NumberedFileInputSplit
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator
import org.deeplearning4j.earlystopping.EarlyStoppingConfiguration
import org.deeplearning4j.earlystopping.EarlyStoppingResult
import org.deeplearning4j.earlystopping.saver.InMemoryModelSaver
import org.deeplearning4j.earlystopping.scorecalc.DataSetLossCalculator
import org.deeplearning4j.earlystopping.termination.MaxEpochsTerminationCondition
import org.deeplearning4j.earlystopping.trainer.EarlyStoppingTrainer
import org.deeplearning4j.eval.Evaluation
import org.deeplearning4j.eval.ROC
import org.deeplearning4j.nn.api.OptimizationAlgorithm
import org.deeplearning4j.nn.conf.GradientNormalization
import org.deeplearning4j.nn.conf.inputs.InputType
import org.deeplearning4j.nn.conf.layers.DenseLayer
import org.deeplearning4j.nn.conf.layers.DropoutLayer
import org.deeplearning4j.nn.conf.layers.GravesLSTM
import org.deeplearning4j.nn.conf.layers.LSTM
import org.deeplearning4j.nn.conf.layers.recurrent.Bidirectional
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer
import org.deeplearning4j.nn.conf.MultiLayerConfiguration
import org.deeplearning4j.nn.conf.NeuralNetConfiguration
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.optimize.api.InvocationType
import org.deeplearning4j.optimize.listeners.EvaluativeListener
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
import org.nd4j.evaluation.classification.Evaluation
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.learning.config.Adam
import org.nd4j.linalg.learning.config.Nadam
import org.nd4j.linalg.learning.config.RmsProp
import org.nd4j.linalg.lossfunctions.LossFunctions
import org.nd4j.linalg.schedule.ScheduleType

// Apache Commons
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils

// Java
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.charset.Charset
import java.util.ArrayList
import java.util.Collections
import java.util.List
import java.util.Random

// Log
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.config.Configurator

// -----------------------------------------------------------------------------

// Initialise

Configurator.initialize(null, "../src/java/log4j2.xml")
def log = LogManager.getLogger(this.class);

def conf1 = evaluate(new File("../src/work/LightCurves/conf.groovy").text)
log.info("Conf1: " + conf1);

def csvDN           = conf1.csvDN
def curvesDN        = conf1.curvesDN;
def miniBatchSize   = conf1.miniBatchSize;
def numLabelClasses = conf1.numLabelClasses;
def blockSize       = conf1.blockSize;
def trainClasses    = conf1.trainClasses;
def trainFid        = conf1.trainFid;
def nEpochs         = conf1.nEpochs;

def conf2 = new JsonSlurper().parseText(Files.readString(Paths.get(csvDN + "/iterator_config.json")))
log.info("Conf2: " + conf2);

def trainFeatureDir = conf2.trainFeatureDir
def trainLabelDir   = conf2.trainLabelDir
def testFeatureDir  = conf2.testFeatureDir
def testLabelDir    = conf2.testLabelDir
def batchSize       = conf2.batchSize
def numClasses      = conf2.numClasses
def maxSeqLength    = conf2.maxSeqLength
def fidValues       = conf2.fidValues
def trainRate       = conf2.trainRate
def trainSize       = conf2.trainSize
def testSize        = conf2.testSize 

// Initialise data

def featuresDirTrain = new File(trainFeatureDir);
def labelsDirTrain   = new File(trainLabelDir);
def featuresDirTest  = new File(testFeatureDir);
def labelsDirTest    = new File(testLabelDir);

numLabelClasses=numClasses

trainFeatures = new CSVSequenceRecordReader();
trainFeatures.initialize(new NumberedFileInputSplit(featuresDirTrain.getAbsolutePath() + "/seq_%d.csv", 0, trainSize - 1));
trainLabels = new CSVSequenceRecordReader();
trainLabels.initialize(new NumberedFileInputSplit(labelsDirTrain.getAbsolutePath() + "/label_%d.csv", 0, trainSize - 1));
trainData = new SequenceRecordReaderDataSetIterator(trainFeatures,
                                                    trainLabels,
                                                    miniBatchSize,
                                                    numLabelClasses,
                                                    false,
                                                    SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_START); // ALIGN_END, LIGN_START, EQUAL_LENGTH
                                                
testFeatures = new CSVSequenceRecordReader();
testFeatures.initialize(new NumberedFileInputSplit(featuresDirTest.getAbsolutePath() + "/seq_%d.csv", 0, testSize - 1));
testLabels = new CSVSequenceRecordReader();
testLabels.initialize(new NumberedFileInputSplit(labelsDirTest.getAbsolutePath() + "/label_%d.csv", 0, testSize - 1));

testData = new SequenceRecordReaderDataSetIterator(testFeatures,
                                                   testLabels,
                                                   miniBatchSize,
                                                   numLabelClasses,
                                                   false,
                                                   SequenceRecordReaderDataSetIterator.AlignmentMode.ALIGN_END);

normalizer = new NormalizerStandardize();
//normalizer = new NormalizerMinMaxScaler(0, 1);
normalizer.fit(trainData);
trainData.setPreProcessor(normalizer);
testData.setPreProcessor(normalizer);

// Prepare NN configuration

conf = new NeuralNetConfiguration.Builder()
                                 .seed(123)
                                 .weightInit(WeightInit.XAVIER)
                                 .updater(new Adam(0.002)) // Nadam(), Nadam(0.002), Adam(0.001) or RMSProp:
                                 .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
                                 .gradientNormalizationThreshold(0.5) // 0.5, 5.0
                                 .l2(0.001)
                                 .list()
                                 .layer(new LSTM.Builder()
                                                .activation(Activation.TANH) // TANH, RELU, LEAKYRELU
                                                .nIn(2)
                                                .nOut(10)
                                                .dropOut(0.1)
                                                .build())
                                 .layer(new LSTM.Builder()
                                                .nOut(10)
                                                .activation(Activation.TANH)
                                                .dropOut(0.3)
                                                .build())
                                 .layer(new DenseLayer.Builder()
                                                      .nOut(10)
                                                      .activation(Activation.RELU)
                                                      .build())
                                 .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT) // MCXENT, KL_DIVERGENCE, MSE
                                                          .activation(Activation.SOFTMAX)
                                                          .nOut(numLabelClasses)
                                                          .build())
                                 .build();

net = new MultiLayerNetwork(conf);
net.init();

// Training

log.info("Starting training...");
net.setListeners(new ScoreIterationListener(20), new EvaluativeListener(testData, 1, InvocationType.EPOCH_END));
net.fit(trainData, nEpochs);

// Test

log.info("Evaluating...");
eval = net.evaluate(testData);
log.info(eval.stats(true, true));

// Save

new File(curvesDN + "/network.json").write(conf.toJson());
net.save(new File(curvesDN + "/network.model"));

log.info("Complete");

// -----------------------------------------------------------------------------

