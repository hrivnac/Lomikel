package com.Lomikel.HBaser;

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

/** <code>Evaluator</code> evaluates formulas for HBase.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseEvaluator extends Evaluator {
  
  /** Create.
    * @param schema The {@link HBaseSchema} to use to interpret types.
    * @throws CommonpException If can't be initiated. */
  public HBaseEvaluator(HBaseSchema schema) throws LomikelException {
    super(schema);
    }
    
  @Override
  protected String varName(String fullName) {
    return fullName.substring(2);
    }
                                         
  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseEvaluator.class);
                                                
  }
