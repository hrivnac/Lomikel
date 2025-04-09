// read light curve data from all.lst file
// and train them using UCISequenceClassification

// Krakev
import com.Krakev.deeplearning4j.Utils.DataOrganizer;

// Deeplearning4j
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader;
import org.datavec.api.records.reader.SequenceRecordReader;
import org.datavec.api.split.NumberedFileInputSplit;
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator;
import org.deeplearning4j.eval.ROC;
import org.deeplearning4j.nn.conf.GradientNormalization;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.optimize.api.InvocationType;
import org.deeplearning4j.optimize.listeners.EvaluativeListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.nd4j.common.primitives.Pair;
import org.nd4j.evaluation.classification.Evaluation;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerMinMaxScaler;
import org.nd4j.linalg.dataset.api.preprocessor.NormalizerStandardize;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.learning.config.Nadam;
import org.nd4j.linalg.learning.config.RmsProp;
import org.nd4j.linalg.lossfunctions.LossFunctions;

// Log
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

// Apache
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

// Java
import java.io.File;
import java.nio.file.Files
import java.nio.file.Paths
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

// -----------------------------------------------------------------------------

def splitAndCreateData(String       csvFilePath,
                       int          chosenFid,
                       List<String> selectedClasses,
                       int          sampling,
                       int          minSamples = 1000, // New parameter for minimum samples
                       double       trainProportion = 0.75) {
  
  def lines = Files.readAllLines(Paths.get(csvFilePath));
  def headers = lines[0].split(',');
  def dataLines = lines[1..-1];

  def doAllClasses = false;
  if (selectedClasses == null) {
    selectedClasses = [];
    }
  if (selectedClasses.isEmpty()) {
    doAllClasses = true;
    }
  
  def filteredLines = dataLines.findAll { line -> 
    def maxClass = line.split(',')[-1];
    if (doAllClasses) {
      if (!selectedClasses.contains(maxClass)) {
        selectedClasses << maxClass;
        }
      return true;
      }
    selectedClasses.contains(maxClass);
    }
  
  int totalSequences = filteredLines.size();
  log.info("Total sequences: $totalSequences");
  
  def classCounts = filteredLines.groupBy {it.split(',')[-1] }.collectEntries { k, v -> [k, v.size()]};
  log.info("Number of samples per class:");
  classCounts.each {className, count -> log.info("\t$className: $count")};

  def filteredClassCounts = classCounts.findAll {it.value >= minSamples};
  selectedClasses = filteredClassCounts.keySet().toList();
  log.info("Classes with at least $minSamples samples: $selectedClasses");
  
  filteredLines = filteredLines.findAll {line ->  def maxClass = line.split(',')[-1];
                                                  selectedClasses.contains(maxClass);
                                                  }
  
  totalSequences = filteredLines.size();
  log.info("Total sequences after min sample filter: $totalSequences");
  
  def balancedLines = [];
  def maxCount = filteredClassCounts.values().max();
  def minCount = filteredClassCounts.values().min();
  if (sampling == 1) {
    filteredClassCounts.each { className, count -> 
      def classLines = filteredLines.findAll { it.split(',')[-1] == className };
      int repeats = Math.ceil(maxCount / count as double) as int;
      balancedLines.addAll(classLines * repeats);
      }
    balancedLines = balancedLines.take(maxCount * selectedClasses.size());
    }
  else if (sampling == -1) {
    filteredClassCounts.each { className, count -> 
      def classLines = filteredLines.findAll { it.split(',')[-1] == className };
      Collections.shuffle(classLines, new Random());  
      balancedLines.addAll(classLines.take(minCount));
      }
    }
  else {
    balancedLines = filteredLines;
    }
    
  Collections.shuffle(balancedLines, new Random());
                                            
  int balancedTotal = balancedLines.size();
  log.info("Total sequences after resampling: $balancedTotal");
  def balancedCounts = balancedLines.groupBy { it.split(',')[-1] }.collectEntries { k, v -> [k, v.size()] };
  log.info("Number of samples per class (after resampling):");
  balancedCounts.each { className, count -> log.info("\t$className: $count") };
  
  def labelMap = selectedClasses.withIndex().collectEntries { label, idx -> [label, idx] };
  int numLabelClasses = selectedClasses.size();
  log.info("Number of selected label classes: $numLabelClasses");
  
  int trainSize = (balancedTotal * trainProportion).toInteger();
  int testSize  = balancedTotal - trainSize;
  log.info("Training sequences: $trainSize, Testing sequences: $testSize");
  
  def trainLines = balancedLines[0..<trainSize];
  def testLines  = balancedLines[trainSize..-1];
  
  int inputSize = 1;
  int maxTimeSteps = 0;
  balancedLines.each { line -> 
    def parts = line.split(',');
    def fids = parts[1].split(';').collect { it as int };
    def filteredCount = fids.count { it == chosenFid };
    maxTimeSteps = Math.max(maxTimeSteps, filteredCount);
    }
  log.info("Max time steps for fid $chosenFid: $maxTimeSteps");
  
  def createTensorAndLabels = { List liness,
                                int  batchSize ->
    log.info("Creating tensor with shape: [$batchSize, $inputSize, $maxTimeSteps]");
    double[] data = new double[batchSize * inputSize * maxTimeSteps];
    INDArray tensor = Nd4j.create(data, [batchSize, inputSize, maxTimeSteps] as int[]);
    double[] labelData = new double[batchSize * numLabelClasses * maxTimeSteps];
    INDArray labels = Nd4j.create(labelData, [batchSize, numLabelClasses, maxTimeSteps] as int[]);
    liness.eachWithIndex { line, batchIdx -> 
      def parts    = line.split(',');
      def fids     = parts[1].split(';').collect { it as int    };
      def mags     = parts[2].split(';').collect { it as double };
      def jds      = parts[3].split(';').collect { it as double };                                           
      def maxClass = parts[-1];
      def sequence = [fids, mags, jds].transpose()
                      .findAll { it[0] == chosenFid }
                      .sort { it[2] }
                      .collect { it[1] };                                           
      sequence.eachWithIndex { mag, timeIdx -> tensor.putScalar([batchIdx, 0, timeIdx] as int[], mag) }
      int labelIdx = labelMap[maxClass];
      maxTimeSteps.times { timeIdx -> labels.putScalar(batchIdx, labelIdx, timeIdx, 1.0) }
      }
    return [features: tensor,
            labels:   labels];
    }
  
  def trainTensor = createTensorAndLabels(trainLines, trainSize);
  def testTensor  = createTensorAndLabels(testLines,  testSize );
  return [train:           new DataSet(trainTensor.features, trainTensor.labels),
          test:            new DataSet(testTensor.features,  testTensor.labels),
          numLabelClasses: numLabelClasses];
  }

// -----------------------------------------------------------------------------

log = LogManager.getLogger(this.class);

def csvFilePath = "LightCurves.csv";
def chosenFid = 1;
def nEpochs = 1;
def selectedClasses = []; // Empty means filter by min samples
def sampling = -1;
def minSamples = 2000; // Minimum samples threshold

// Initialise data

def data = splitAndCreateData(csvFilePath, chosenFid, selectedClasses, sampling, minSamples);
trainData = data.train;
testData  = data.test;
def numLabelClasses = data.numLabelClasses;

// Prepare NN configuration

def conf = new NeuralNetConfiguration.Builder()
    .seed(123)
    .weightInit(WeightInit.XAVIER)
    .updater(new Nadam())
    .gradientNormalization(GradientNormalization.ClipElementWiseAbsoluteValue)
    .gradientNormalizationThreshold(5.0)
    .list()
    .layer(new LSTM.Builder()
                   .activation(Activation.RELU)
                   .nIn(1)
                   .nOut(20)
                   .build())
    .layer(new LSTM.Builder()
                   .activation(Activation.TANH)
                   .nIn(20)
                   .nOut(10)
                   .build())
    .layer(new RnnOutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                             .activation(Activation.SOFTMAX)
                             .nIn(10)
                             .nOut(numLabelClasses)
                             .build())
    .build()

net = new MultiLayerNetwork(conf);
net.init();

// Train

log.info("Starting training...");
net.setListeners(new ScoreIterationListener(20), new EvaluativeListener(testData, 1, InvocationType.EPOCH_END));
nEpochs.times { epoch -> 
  log.info("Epoch ${epoch + 1} of $nEpochs");
  net.fit(trainData);
}

// Test

log.info("Evaluating...");
def evaluation = new Evaluation(numLabelClasses); 
INDArray testOutput = net.output(testData.features);
evaluation.eval(testData.labels, testOutput);
log.info(evaluation.stats(false, true))

// Save

new File("../run/network.json").write(conf.toJson());
net.save(new File("../run/network.model"));

log.info("Complete");