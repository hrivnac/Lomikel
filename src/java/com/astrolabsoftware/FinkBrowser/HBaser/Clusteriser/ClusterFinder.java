package com.astrolabsoftware.FinkBrowser.HBaser.Clusteriser;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.math3.linear.*;

import java.util.List;
import java.util.Arrays;
import java.io.File;
import java.io.IOException;

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
    ClusterFinder finder = new ClusterFinder("/tmp/scaler_params.json",
                                             "/tmp/pca_params.json",
                                             "/tmp/cluster_centers.json");
    double[] newData = {1.2, 3.4, 5.6, 7.8, 2.1, 4.3, 6.5, 8.7, 3.2, 5.4,
                        1.2, 3.4, 5.6, 7.8, 2.1, 4.3, 6.5, 8.7, 3.2, 5.4,
                        1.2, 3.4, 5.6, 7.8, 2.1, 4.3, 6.5, 8.7, 3.2, 5.4,
                        1.2, 3.4, 5.6, 7.8, 2.1, 4.3, 6.5, 8.7, 3.2, 5.4,
                        1.2, 3.4, 5.6, 7.8, 2.1, 4.3, 6.5, 8.7, 3.2, 5.4,};  // Example input
    int cluster = finder.transformAndPredict(newData);
    log.info("Assigned cluster: " + cluster);
    }

  public ClusterFinder(String scalerFile,
                       String pcaFile,
                       String clustersFile) throws IOException {
    loadScalerParams(scalerFile);
    loadPCAParams(pcaFile);
    loadClusterCenters(clustersFile);
    }
  
  private void loadScalerParams(String filePath) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    ScalerParams params = objectMapper.readValue(new File(filePath), ScalerParams.class);
    _mean = params.mean;
    _std = params.std;
    log.debug("Scaler: " + _mean.length);
    }
  
  private void loadPCAParams(String filePath) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    PCAParams params = objectMapper.readValue(new File(filePath), PCAParams.class);
    _pcaComponents = new Array2DRowRealMatrix(params.components);
    _explainedVariance = params.explained_variance;
    log.debug("PCA Components: " + _pcaComponents.getColumnDimension() + " * " + _pcaComponents.getRowDimension());
    }
    
  private void loadClusterCenters(String filePath) throws IOException {
    ObjectMapper objectMapper = new ObjectMapper();
    _clusterCenters = new Array2DRowRealMatrix(objectMapper.readValue(new File(filePath), double[][].class));
    log.debug("Cluster Centers: " + _clusterCenters.getColumnDimension() + " * " + _clusterCenters.getRowDimension());
    }    
    
  
  private double[] standardize(double[] input) {
    double[] standardized = new double[input.length];
    for (int i = 0; i < input.length; i++) {
      if (_std[i] == 0) {
        standardized[i] = 0;
        }
      else {
        standardized[i] = (input[i] - _mean[i]) / _std[i];
        }
      }
    log.debug("Standardized: " + standardized.length);
    return standardized;
    }
  
  private double[] applyPCA(double[] standardizedInput) {
    RealVector inputVector = new ArrayRealVector(standardizedInput);
    RealVector transformed = _pcaComponents.transpose().operate(inputVector);
    log.debug("PCA Transformed: " + transformed.getDimension());
    return transformed.toArray();
    }
  
  private int findClosestCluster(double[] transformedData) {
    RealVector transformedVector = new ArrayRealVector(transformedData);
    double minDistance  = Double.MAX_VALUE;
    double minDistance2 = Double.MAX_VALUE;
    int closestCluster = -1;
    RealVector clusterCenter;
    double distance;
    log.info("DISTANCE");
    for (int i = 0; i < _clusterCenters.getRowDimension(); i++) {
      clusterCenter = _clusterCenters.getRowVector(i);
      distance = transformedVector.getDistance(clusterCenter);
      log.info("\t" + distance);
      if (distance < minDistance2) {
        if (distance < minDistance) {
          minDistance   = distance;
          minDistance2  = minDistance;
          closestCluster = i;
          }
        else {
          minDistance2 = distance;
          }
        }
      }
    log.info("" + minDistance + " " + minDistance2);
    if (minDistance > 0.5 * minDistance2) {
      return -1;
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