package com.Lomikel.HBaser;

import com.Lomikel.Utils.LomikelException;
import com.Lomikel.DB.GroovyEvaluator;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Evaluator</code> evaluates formulas for HBase.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseEvaluator extends GroovyEvaluator {
  
  /** Create.
    * @param schema The {@link HBaseSchema} to use to interpret types.
    * @throws CommonpException If can't be initiated. */
  public HBaseEvaluator(HBaseSchema schema) throws LomikelException {
    super(schema);
    log.info("\tas HBaseEvaluator");
    setEvaluatorFunctions("com.Lomikel.HBaser.HBaseEvaluatorFunctions", "com/Lomikel/HBaser/HBaseEvaluatorFunctions.groovy");
    setEvaluatorFunctions(null, "com/Lomikel/WebService/HBaseColumnsProcessor.groovy");  
    }
    
  @Override
  protected String varName(String fullName) {
    return fullName.substring(2);
    }
                                         
  /** Logging . */
  private static Logger log = LogManager.getLogger(HBaseEvaluator.class);
                                                
  }
