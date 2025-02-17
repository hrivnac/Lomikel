package com.astrolabsoftware.FinkBrowser.HBaser.Clusteriser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.linear.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>ClusterFinder</code> identifies HBase rows with
  * clusters defined by previous clustering algorithm, read from
  * <tt>JSON</tt> model files.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class ClusterFinder {
  
  public static void main(String[] args) throws IOException {
    ClusterFinder transformer = new ClusterFinder("/tmp/scaler_params.json",
                                                  "/tmp/pca_params.json",
                                                  "/tmp/cluster_centers.json");
    double[] newData = {1.2, 3.4, 5.6, 7.8, 2.1, 4.3, 6.5, 8.7, 3.2, 5.4};  // Example input
    int cluster = transformer.transformAndPredict(newData);
    log.info("Assigned cluster: " + cluster);
    }

  public ClusterFinder(String scalerFile, String pcaFile, String clustersFile) throws IOException {
    loadScalerParams(scalerFile);
    loadPCAParams(pcaFile);
    loadClusterCenters(clustersFile);
    }
  
  private void loadScalerParams(String filePath) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ScalerParams params = objectMapper.readValue(new File(filePath), ScalerParams.class);
    _mean = params.mean;
    _std = params.std;
    }
  
  private void loadPCAParams(String filePath) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    PCAParams params = objectMapper.readValue(new File(filePath), PCAParams.class);
    _pcaComponents = new Array2DRowRealMatrix(params.components);
    _explainedVariance = params.explained_variance;
    }
  
  private void loadClusterCenters(String filePath) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ClusterCenters centers = objectMapper.readValue(new File(filePath), ClusterCenters.class);
    _clusterCenters = new Array2DRowRealMatrix(centers.centers);
    }
  
  private double[] standardize(double[] input) {
    double[] standardized = new double[input.length];
    for (int i = 0; i < input.length; i++) {
      standardized[i] = (input[i] - _mean[i]) / _std[i];
      }
    return standardized;
    }
  
  private double[] applyPCA(double[] standardizedInput) {
    RealVector inputVector = new ArrayRealVector(standardizedInput);
    RealVector transformed = _pcaComponents.operate(inputVector);
    return transformed.toArray();
    }
  
  private int findClosestCluster(double[] transformedData) {
    RealVector transformedVector = new ArrayRealVector(transformedData);
    double minDistance = Double.MAX_VALUE;
    int closestCluster = -1;
    for (int i = 0; i < _clusterCenters.getRowDimension(); i++) {
      RealVector clusterCenter = _clusterCenters.getRowVector(i);
      double distance = transformedVector.getDistance(clusterCenter);
      if (distance < minDistance) {
        minDistance = distance;
        closestCluster = i;
        }
      }
    return closestCluster;
    }
  
  public int transformAndPredict(double[] inputData) {
    double[] standardized = standardize(inputData);
    double[] pcaTransformed = applyPCA(standardized);
    return findClosestCluster(pcaTransformed);
    }
  
  private double[] _mean;
  
  private double[] _std;
  
  private RealMatrix _pcaComponents;
  
  private double[] _explainedVariance;
  
  private RealMatrix _clusterCenters;
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(ClusterFinder.class);
  
  }