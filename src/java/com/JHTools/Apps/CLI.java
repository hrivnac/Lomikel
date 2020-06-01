package com.JHTools.Apps;

import com.JHTools.Utils.Info;
import com.JHTools.Utils.StringFile;
import com.JHTools.Utils.StringResource;
import com.JHTools.Utils.CommonException;

// Bean Shell
import bsh.Interpreter;
import bsh.EvalError;

// Java
import java.io.InputStreamReader;

// Log4J
import org.apache.log4j.Logger;

/** Simple Command Line.
  * with usual interval operations.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class CLI {

  /** Start {@link Interpreter} and run forever.
    * @param args The command arguments. Ignored. */
  public CLI(String[] args) {
    Interpreter interpreter = new Interpreter(new InputStreamReader(System.in), System.out, System.err, true);
    interpreter.print("Welcome to Fink Browser CLI " + Info.release() + "\n");
    interpreter.print("https://astrolabsoftware.github.io\n");
    setupInterpreter(interpreter);
    new Thread(interpreter).start();
    }
    
  /** Load standard init files and setup standard environment.
    * @param interpreter The embedded {@link Interpreter}. */
  public void setupInterpreter(Interpreter interpreter) {
    _interpreter = interpreter;
    // Set global reference and imports
    try {
      interpreter.eval("import com.astrolabsoftware.AstroLabNet.DB.*");
      interpreter.eval("import com.astrolabsoftware.AstroLabNet.Livyser.Language");
      interpreter.set("fb", this);
      }
    catch (EvalError e) {
      log.error("Can't set CommandLine references", e);
      }
    String init = "";
    // Source init.bsh
    log.info("Sourcing init.bsh");
    try {
      init = new StringFile("init.bsh").toString();
      interpreter.eval(init);
      }
    catch (CommonException e) {
      log.warn("init.bsh file cannot be read.");
      log.debug("init.bsh file cannot be read.", e);
      }
    catch (EvalError e) {
      log.error("Can't evaluate standard BeanShell expression", e);
      }
    // Load site profile
    if (_profile != null) {
      log.info("Loading site profile: " + _profile);  
      try {
        init = new StringResource(_profile + ".bsh").toString();
        interpreter.eval(init);
        }
      catch (CommonException e) {
        log.warn("Site profile " + _profile + " cannot be loaded.");
        log.debug("Site profile " + _profile + " cannot be loaded.", e);
        }
      catch (EvalError e) {
        log.error("Can't evaluate standard BeanShell expression", e);
        }
      }
    // Loading state
    log.info("Sourcing .state.bsh");
    try {
      init = new StringFile(".state.bsh").toString();
      interpreter.eval(init);
      }
    catch (CommonException e) {
      log.warn(".state.bsh file cannot be read.");
      log.debug(".state.bsh file cannot be read.", e);
      }
    catch (EvalError e) {
      log.error("Can't evaluate standard BeanShell expression", e);
      }
    // Source command line source
    if (_source != null) {
      log.info("Sourcing " + _source);
      try {
        init = new StringFile(_source).toString();
        interpreter.eval(init);
        }
      catch (CommonException e) {
        log.warn(_source + " file cannot be read.");
        log.debug(_source + " file cannot be read.", e);
        }
      catch (EvalError e) {
        log.error("Can't evaluate standard BeanShell expression", e);
        }
      }
    }
    
  /** Set site profile.
    * @param profile The Resource path to the site profile. */
  public void setProfile(String profile) {
    _profile = profile;
    }
    
  /** Set script source.
    * @param source The filename of the script source. */
  public void setSource(String source) {
    _source = source;
    }
    
  private String _profile;
  
  private String _source;
    
  private Interpreter _interpreter;
 
  /** Logging . */
  private static Logger log = Logger.getLogger(CLI.class);
   
 
  }
