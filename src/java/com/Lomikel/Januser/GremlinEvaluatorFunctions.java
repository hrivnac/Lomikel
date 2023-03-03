package com.Lomikel.Januser;

// Java
import java.util.Arrays;
import java.util.Arrays;

// Log4J
import org.apache.log4j.Logger;

/** <code>GremlinEvaluatorFuctions</code> provide static functions available to
  * {@link GremlinEvaluator}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class GremlinEvaluatorFunctions {
    
  /** Do-nothing Demo */
  public static boolean demo() {
    return true;
    }
  
  /** Give quadratic distance of two points in multiple dimensions.
    * @param values   The array of values of all coordinates for two points.
    *                 <code>{{x1_of_point1, x1_of_point2}, {x2_of_point1, x2_of_point2},...}</code>.
    * @param weighted Whether the distance should be weighted by the number of dimensions.
    * @return         The quadratic distance of two points in multiple dimensions. */
  public static double qdistance(double[][] values,
                                 boolean    weighted) {
    //double distance = Arrays.stream(values).mapToDouble(d -> Math.pow(d[0] - d[1], 2)).sum();
    double distance = 0;
    for (int i = 0; i < values.length; i++) {
      if (_weights == null) {
        distance += Math.pow(values[i][0] - values[i][1], 2);
        }
      else {
        distance += Math.pow((values[i][0] - values[i][1]) / _weights[i], 2);
        }
      }
    if (weighted) {
      distance = distance / values.length;
      }
    return Math.sqrt(distance);
    }
    
  /** Give abs-linear distance of two points in multiple dimensions.
    * @param values   The array of values of all coordinates for two points.
    *                 <code>{{x1_of_point1, x1_of_point2}, {x2_of_point1, x2_of_point2},...}</code>.
    * @param weighted Whether the distance should be weighted by the number of dimensions.
    * @return         The abs-linear distance of two points in multiple dimensions. */
  public static double ldistance(double[][] values,
                                 boolean    weighted) {
    //double distance = Arrays.stream(values).mapToDouble(d -> Math.abs(d[0] - d[1])).sum();
    double distance = 0;
    for (int i = 0; i < values.length; i++) {
      if (_weights == null) {
        distance += Math.abs(values[i][0] - values[i][1]);
        }
      else {
        distance += Math.abs((values[i][0] - values[i][1]) / _weights[i]);
        }
      }
    if (weighted) {
      distance = distance / values.length;
      }
    return distance;
    }
  
  /** Set the weights for distance methods.
    * @param weights The array with weights. */
  public static void setDistanceWeights(double[] weights) {
    _weights = weights;
    }
    
  private static double[] _weights;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinEvaluatorFunctions.class);
                                                
  }
