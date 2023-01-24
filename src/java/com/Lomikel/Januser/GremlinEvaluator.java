package com.Lomikel.Januser;

import com.Lomikel.Utils.LomikelException;
import com.Lomikel.DB.Evaluator;

// Log4J
import org.apache.log4j.Logger;

/** <code>Evaluator</code> evaluates formulas for Janusgraph.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class GremlinEvaluator extends Evaluator {
  
  /** Create.
    * @param schema The {@link GremlinSchema} to use to interpret types.
    * @throws CommonpException If can't be initiated. */
  public GremlinEvaluator(GremlinSchema schema) throws LomikelException {
    super(schema);
    log.info("\tas GremlinEvaluator");
    setEvaluatorFunctions("com.Lomikel.Januser.GremlinEvaluatorFunctions", "com/Lomikel/Januser/GremlinEvaluatorFunctions.bsh");
    }
                                          
  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinEvaluator.class);
                                                
  }
