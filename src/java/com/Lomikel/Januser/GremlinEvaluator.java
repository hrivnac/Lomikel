package com.Lomikel.Januser;

import com.Lomikel.Utils.LomikelException;
import com.Lomikel.Utils.StringResource;
import com.Lomikel.DB.Evaluator;

// Bean Shell
import bsh.Interpreter;
import bsh.EvalError;

// Java
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;

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
    setEvaluatorFunctions("import com.Lomikel.Januser.GremlinEvaluatorFunctions", "com/Lomikel/Januser/GremlinEvaluatorFunctions.bsh");
    }
                                          
  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinEvaluator.class);
                                                
  }
