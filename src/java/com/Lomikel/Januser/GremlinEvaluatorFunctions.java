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
    * @param maxSize  The max size of the value array. Full array taken if maxSize = 0 or negative.
    * @param weighted Whether the distance should be weighted by the number of dimensions.
    * @return         The quadratic distance of two points in multiple dimensions. */
  public static double qdistance(double[][] values,
                                 int        maxSize,
                                 boolean    weighted) {
    double distance;
    if (maxSize > 0) {
      distance = Arrays.stream(values).limit(maxSize).mapToDouble(d -> Math.pow(d[0] - d[1], 2)).sum();
      }
    else {
      distance = Arrays.stream(values).mapToDouble(d -> Math.pow(d[0] - d[1], 2)).sum();
      }
    if (weighted) {
      distance = distance / values.length;
      }
    return Math.sqrt(distance);
    }
    
  /** Give abs-linear distance of two points in multiple dimensions.
    * @param values   The array of values of all coordinates for two points.
    *                 <code>{{x1_of_point1, x1_of_point2}, {x2_of_point1, x2_of_point2},...}</code>.
    * @param maxSize  The max size of the value array. Full array taken if maxSize = 0 or negative.
    * @param weighted Whether the distance should be weighted by the number of dimensions.
    * @return         The abs-linear distance of two points in multiple dimensions. */
  public static double ldistance(double[][] values,
                                 int        maxSize,
                                 boolean    weighted) {
    double distance;
    if (maxSize > 0) {
      distance = Arrays.stream(values).limit(maxSize).mapToDouble(d -> Math.abs(d[0] - d[1])).sum();
      }
    else {
      distance = Arrays.stream(values).mapToDouble(d -> Math.abs(d[0] - d[1])).sum();
      }
    if (weighted) {
      distance = distance / values.length;
      }
    return distance;
    }
                                        
  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinEvaluatorFunctions.class);
                                                
  }
