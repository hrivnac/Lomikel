package com.Lomikel.DB;

import com.Lomikel.Utils.LomikelException;
import com.Lomikel.Utils.StringResource;

// Groovy
import groovy.lang.GroovyShell;
import groovy.lang.Binding;

// Java
import java.util.Set;
import java.util.TreeSet;
import java.util.Map;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Evaluator</code> evaluates formulas using <em>Groovy<em> language.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class GroovyEvaluator {
  
  /** Create.
    * @param schema The {@link Schema} to use to interpret types.
    * @throws CommonpException If can't be initiated. */
  public GroovyEvaluator(Schema schema) throws LomikelException {
    log.info("Creating Groovy Evaluator");
    _schema = schema;
    _sharedData = new Binding();
    _shell = new GroovyShell(_sharedData);
    }
    
  /** Set Java and BeanShell Evaluator Functions.
    * @param javaEF   The Java Evaluatior Functions class name. May be <tt>null</tt>.
    * @param groovyEF The Groovy Evaluatior Functions script name. May be <tt>null</tt>. 
    * @throws CommonpException If can't be set. */
  public void setEvaluatorFunctions(String javaEF,
                                    String groovyEF) throws LomikelException {
    if (javaEF != null) {
      log.info("Importing " + javaEF);
      _shell.evaluate("import static " + javaEF + ".*;");
      }
    if (groovyEF != null) {
      log.info("Importing " + groovyEF);
      _shell.evaluate(new StringResource(groovyEF).toString());
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
    if (values != null) {
      for (String variable : _variables) {
        if (values.containsKey(variable)) {
          setVariable(variable, values.get(variable));
          }
        }
      }
    String cmd = type + " result = " + formula + ";\nreturn result\n";
    Object o =_shell.evaluate(cmd);
    String result = o.toString();
    return result;
    }
          
  /** Declare variable. Set to <tt>0</tt>.
    * @param name The name of variables.*/
  public void setVariable(String name) {
    setVariable(name, "0");
    }

  /** Set variable.
    * @param name  The name of variables.
    * @param value The value of variables. */
  public void setVariable(String name,
                          String value) {
    String cmd = "";
    String fname = varName(name);
    String type = "double";
    if (_schema.type(name) != null) {
      type = _schema.type(name);
      }
    switch (type) {
      case "float":
      case "java.lang.Float":
        _shell.setVariable(fname, Float.parseFloat(value));
        break;
      case "double":
      case "java.lang.Double":
        _shell.setVariable(fname, Double.parseDouble(value));
        break;
      case "integer":
      case "java.lang.Integer":
        _shell.setVariable(fname, Integer.parseInt(value));
        break;
      case "long":
      case "java.lang.Long":
        _shell.setVariable(fname, Long.parseLong(value));
        break;
      case "short":
      case "java.lang.Short":
        _shell.setVariable(fname, Short.parseShort(value));
        break;
      default: // includes "string"          
        _shell.setVariable(fname, value);
      }
    }
    
  /** Set variable.
    * @param name  The name of variables.
    * @param value The value of variables.  */
  public void setVariable(String name,
                          double value) {
    String fname = varName(name);
    _shell.setVariable(fname, value);
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
    
  private GroovyShell _shell;
  
  private Binding _sharedData; 

  
  /** Logging . */
  private static Logger log = LogManager.getLogger(GroovyEvaluator.class);
                                                
  }
