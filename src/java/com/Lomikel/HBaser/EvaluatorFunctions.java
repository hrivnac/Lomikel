package com.Lomikel.HBaser;

// Log4J
import org.apache.log4j.Logger;

/** <code>EvaluatorFuctions</code> provide static functions available to
  * {@link Evaluator}.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class EvaluatorFunctions {
    
  /** TBD */
  public static boolean isWithinGeoLimits(double ra,
                                          double dec,
                                          double raMin,
                                          double raMax,
                                          double decMin,
                                          double decMax) {
    return ra  > raMin  &&
           ra  < raMax  &&
           dec > decMin &&
           dec < decMax;
    }
      
  /** Logging . */
  private static Logger log = Logger.getLogger(EvaluatorFunctions.class);
                                                
  }
