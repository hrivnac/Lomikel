package com.Lomikel.DB;

import com.Lomikel.Utils.LomikelException;
import com.Lomikel.Utils.StringResource;

// Bean Shell
import bsh.Interpreter;
import bsh.EvalError;

// Java
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.stream.Stream;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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
  public Evaluator(Schema schema) throws LomikelException {
    log.info("Creating Evaluator");
    _schema = schema;
    _interpreter = new Interpreter();
    }
    
  /** Set Java and BeanShell Evaluator Functions.
    * @param javaEF The Java Evaluatior Functions class name. May be <tt>null</tt>.
    * @param bshEF  The BeanShell Evaluatior Functions script name. May be <tt>null</tt>. 
    * @throws CommonpException If can't be set. */
  public void setEvaluatorFunctions(String javaEF,
                                    String bshEF) throws LomikelException {
    try {
      if (javaEF != null) {
        log.info("Importing " + javaEF);
        _interpreter.eval("import static " + javaEF + ".*;");
        }
      if (bshEF != null) {
        log.info("Importing " + bshEF);
        _interpreter.eval(new StringResource(bshEF).toString());
        }
      }
    catch (EvalError e) {
      throw new LomikelException("Can't initiate Evaluator", e);
      }
    }

  /** Evaluate boolean formula with supplied variables and values.
    * @param values  The names and values of variables.
    * @param formula The formula to be evaluated. It should use supplied
    *                variables, which will be filled with supplied values.
    * @throws LomikelException If formula cannot be evaluated with supplied variables. */
  public boolean evalBoolean(Map<String, String> values,
                             String              formula) throws LomikelException {
    String r = eval(values, formula, "boolean");
    return Boolean.parseBoolean(r);
    }    
   public static double qdistance0(double v1, double v2, double v3, double v4) {
    double distance = Math.pow(v1-v2, 2) + Math.pow(v3-v4, 2);
    return Math.sqrt(distance);
    }


  /** Evaluate double formula with supplied variables and values.
    * @param values  The names and values of variables.
    * @param formula The formula to be evaluated. It should use supplied
    *                variables, which will be filled with supplied values.
    * @throws LomikelException If formula cannot be evaluated with supplied variables. */
  public double evalDouble(Map<String, String> values,
                           String              formula) throws LomikelException {
    String r = eval(values, formula, "double");
    return Double.parseDouble(r);
    }

  /** Evaluate formula with supplied variables and values.
    * @param values  The names and values of variables.
    * @param formula The formula to be evaluated. It should use supplied
    *                variables, which will be filled with supplied values.
    * @param type    The formula result type.
    * @throws LomikelException If formula cannot be evaluated with supplied variables. */
  public String eval(Map<String, String> values,
                     String              formula,
                     String              type) throws LomikelException {
    try {
      if (values != null) {
        for (String variable : _variables) {
          if (values.containsKey(variable)) {
            setVariable(variable, values.get(variable));
            }
          }
        }
      _interpreter.eval(type + " result = " + formula + ";");
      Object o = _interpreter.get("result");
      String result = o.toString();
      return result;
      }
    catch (EvalError e) {
      throw new LomikelException("Can't evaluate formula: " + formula, e);
      }
    }
          
  /** Declare variable. Set to <tt>0</tt>.
    * @param name The name of variables.*/
  public void setVariable(String name) {
    setVariable(name, "0");
    }

  /** Set variable.
    * @param name  The name of variables.
    * @param value The value of variables.
    *               Undefined types are considered <code>double</code>,
    *               unknown types are taken as <code>string</code>. */
  public void setVariable(String name,
                          String value) {
    String fname = varName(name);
    String type = "double";
    if (_schema.type(name) != null) {
      type = _schema.type(name);
      }
    try {
      switch (type) {
        case "float":
        case "java.lang.Float":
          _interpreter.set(fname, Float.parseFloat(value));
          break;
        case "double":
        case "java.lang.Double":
          _interpreter.set(fname, Double.parseDouble(value));
          break;
        case "integer":
        case "java.lang.Integer":
          _interpreter.set(fname, Integer.parseInt(value));
          break;
        case "long":
        case "java.lang.Long":
          _interpreter.set(fname, Long.parseLong(value));
          break;
        case "short":
        case "java.lang.Short":
          _interpreter.set(fname, Short.parseShort(value));
          break;
        default: // includes "string"          
        _interpreter.set(fname, value);
        }
      }
    catch (EvalError e) {
      log.error("Cannot assign " + name + " = '" + value);
      }
    }
    
  /** TBD */
  public void setVariable(String name,
                          double value) {
    String fname = varName(name);
    try {
      _interpreter.set(fname, value);
      }
    catch (EvalError e) {
      log.error("Cannot assign " + name + " = '" + value);
      }
    }
    
  /** Set array variable.
    * @param name   The name of variables.
    * @param values The values of variables.
    *               Undefined types are considered <code>double</code>,
    *               unknown types are taken as <code>string</code>. */
  public void setVariable(String   name,
                          String[] values) {
    String fname = varName(name);
    int length = values.length;
    String type = "double";
    if (_schema.type(name) != null) {
      type = _schema.type(name);
      }
    try {
      switch (type) {
        case "float":
        case "java.lang.Float":
          _interpreter.set(fname, Stream.of(values).mapToDouble(Float::parseFloat).toArray());
          break;
        case "double":
        case "java.lang.Double":
          _interpreter.set(fname, Stream.of(values).mapToDouble(Double::parseDouble).toArray());
          break;
        case "integer":
        case "java.lang.Integer":
          _interpreter.set(fname, Stream.of(values).mapToInt(Integer::parseInt).toArray());
          break;
        case "long":
        case "java.lang.Long":
          _interpreter.set(fname, Stream.of(values).mapToLong(Long::parseLong).toArray());
          break;
        case "short":
        case "java.lang.Short":
          _interpreter.set(fname, Stream.of(values).mapToInt(Short::parseShort).toArray());
          break;
        default: // includes "string"          
          _interpreter.set(fname, values);
        }
      }
    catch (EvalError e) {
      log.error("Cannot assign " + name + " = '" + values);
      }
    }
    
  /** TBD */
  public void setVariable(String   name,
                          double[] values) {
    String fname = varName(name);    
    try {
      _interpreter.set(fname, values);
      }
    catch (EvalError e) {
      log.error("Cannot assign " + name + " = '" + values);
      }
    }
    
  /** Give the array of used variables.
    * @return The array of used variables. */
  public String[] variables() {
    return _variables.toArray(new String[0]);
    }
    
  /** Shows whether a variable exists.
    * @param var The name of a variable.
    * @return    Whether that variable exists. */
  public boolean hasVariable(String var) {
    return _variables.contains(var);
    }
    
  /** Add variables to the list of used variables.
    * Only add variables available in {@link Schema}.
    * @param formula The formula to be used for list of used variables. */
  public void setVariables(String formula) {
    Set<String> cn = _schema.columnNames();
    for (String column : cn) {
      if (formula.contains(varName(column))) {
        _variables.add(column);
        }
      }
    }
    
  /** Add variables to the list of used variables.
    * Add variables even if they are not available in {@link Schema}.
    * @param formula The formula to be used for list of used variables. */
  public void forceVariables(String variables) {
    for (String v : variables.split(" ")) {
      _variables.add(v.trim());
      }
    }
    
  /** Give variable name from the database name.
    * @param fullName The fill name of the database column.
    * @return         The schema variable name. */
  protected String varName(String fullName) {
    return fullName;
    }
    
  private Schema _schema;
  
  private Set<String> _variables = new TreeSet<>();
    
  private Interpreter _interpreter;     
                                         
  /** Logging . */
  private static Logger log = LogManager.getLogger(Evaluator.class);
                                                
  }
