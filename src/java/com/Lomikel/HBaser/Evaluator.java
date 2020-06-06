package com.Lomikel.HBaser;

import com.Lomikel.Utils.CommonException;
import com.Lomikel.Utils.StringResource;

// Bean Shell
import bsh.Interpreter;
import bsh.EvalError;

// Java
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.HashMap;

// Log4J
import org.apache.log4j.Logger;

/** <code>Evaluator</code> evaluates formulas.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Evaluator {

  /** Create.
    * @param schema The {@link Schema} to use to interpret types.
    * @throws CommonpException If can't be initiated. */
  public Evaluator(Schema schema) throws CommonException {
    _schema = schema;
    _interpreter = new Interpreter();
    try {
      _interpreter.eval("import com.Lomikel.HBaser.EvaluatorFunctions.*;");
      _interpreter.eval(new StringResource("com/Lomikel/HBaser/EvaluatorFunctions.bsh").toString());
      }
    catch (EvalError e) {
      throw new CommonException("Can't initiate Evaluator", e);
      }
    }

  /** Evaluate boolean formula with supplied variables and values.
    * @param values  The names and values of variables.
    * @param formula The formula to be evaluated. It should use supplied
    *                variables, which will be filled with supplied values.
    * @throws CommonException If formula cannot be evaluated with supplied variables. */
  public boolean evalBoolean(Map<String, String> values,
                             String              formula) throws CommonException {
    String r = eval(values, formula, "boolean");
    return Boolean.parseBoolean(r);
    }

  /** Evaluate formula with supplied variables and values.
    * @param values  The names and values of variables.
    * @param formula The formula to be evaluated. It should use supplied
    *                variables, which will be filled with supplied values.
    * @param type    The formula result type.
    * @throws CommonException If formula cannot be evaluated with supplied variables. */
  public String eval(Map<String, String> values,
                     String              formula,
                     String              type) throws CommonException {
    try {
      for (String variable : _variables) {
        setVariable(variable, values.get(variable));
        }
      _interpreter.eval(type + " result = " + formula + ";");
      Object o = _interpreter.get("result");
      String result = o.toString();
      return result;
      }
    catch (EvalError e) {
      throw new CommonException("Can't evaluate formula: " + formula, e);
      }
    }
          
  /** Declare variable. Set to <tt>0</tt>.
    * @param name The name of variables.*/
  private void setVariable(String name) {
    setVariable(name, "0");
    }

  /** Set variable.
    * @param name  The name of variables.
    * @param value The value of variables. */
  private void setVariable(String name,
                           String value) {
    String fname = name.substring(2);
    try {
      switch (_schema.type(name)) {
        case "float":
          _interpreter.set(fname, Float.parseFloat(value));
          break;
        case "double":
          _interpreter.set(fname, Double.parseDouble(value));
          break;
        case "integer":
          _interpreter.set(fname, Integer.parseInt(value));
          break;
        case "long":
          _interpreter.set(fname, Long.parseLong(value));
          break;
        default: // includes "string"          
        _interpreter.set(fname, value);
        }
      }
    catch (EvalError e) {
      log.error("Cannot assign " + name + " = '" + value);
      }
    }
    
  /** Add variables to the list of used variables.
    * @param formula The formula to be used for list of used variables. */
  public void setVariables(String formula) {
    for (String column : _schema.columnNames()) {
      if (formula.contains(column.substring(2))) {
        _variables.add(column);
        }
      }
    }
           
  private Schema _schema;
  
  private Set<String> _variables = new TreeSet<>();
    
  private Interpreter _interpreter;      
                                         
  /** Logging . */
  private static Logger log = Logger.getLogger(Evaluator.class);
                                                
  }
