package com.Lomikel.Januser;

import com.Lomikel.Utils.LomikelException;
import com.Lomikel.Utils.StringResource;

// Bean Shell
import bsh.Interpreter;
import bsh.EvalError;

// Java
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Stream;

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
    
  /** Do-nothink Demo */
  public static boolean demo() {
    return true;
    }
  
  /** Give quadratic distance.
    * TBD */
  public static double qdistance(double[][] values) {
    double distance = Arrays.stream(values).mapToDouble(d -> Math.pow(d[0] - d[1], 2)).sum();
    return Math.sqrt(distance);
    }
    
   public static double qdistance0(double v1, double v2, double v3, double v4) {
    double distance = Math.pow(v1-v2, 2) + Math.pow(v3-v4, 2);
    return Math.sqrt(distance);
    }
                                        
  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinEvaluatorFunctions.class);
                                                
  }