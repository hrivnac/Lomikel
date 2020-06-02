package com.Lomikel.Apps;

import com.Lomikel.Utils.Info;
import com.Lomikel.Utils.StringFile;
import com.Lomikel.Utils.StringResource;
import com.Lomikel.Utils.CommonException;

// Bean Shell
import bsh.Interpreter;
import bsh.util.JConsole;
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

  /** Start {@link Interpreter} and run forever. */
  public CLI() {
    _interpreter = new Interpreter(new InputStreamReader(System.in), System.out, System.err, true);
    setupInterpreter();
    new Thread(_interpreter).start();
    }
    
  /** Load standard init files and setup standard environment. */
  public void setupInterpreter() {
    // Set global reference and imports
    try {
      _interpreter.set("fb", this);
      }
    catch (EvalError e) {
      log.error("Can't set CommandLine references", e);
      }
    String init = "";
    // Source init.bsh
    log.debug("Sourcing init.bsh");
    try {
      init = new StringFile("init.bsh").toString();
      _interpreter.eval(init);
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
      log.info("Loading profile: " + _profile);  
      try {
        init = new StringResource(_profile + ".bsh").toString();
        _interpreter.eval(init);
        }
      catch (CommonException e) {
        log.warn("Profile " + _profile + " cannot be loaded.");
        log.debug("Profile " + _profile + " cannot be loaded.", e);
        }
      catch (EvalError e) {
        log.error("Can't evaluate standard BeanShell expression", e);
        }
      }
    // Loading state
    log.debug("Sourcing .state.bsh");
    try {
      init = new StringFile(".state.bsh").toString();
      _interpreter.eval(init);
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
        _interpreter.eval(init);
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
    
  /** Give {@link Interpreter}.
    * @return The {@link Interprer}. */
  public Interpreter interpreter() {
    return _interpreter;
    }  
    
  private String _profile;
  
  private String _source;
    
  private Interpreter _interpreter;
 
  /** Logging . */
  private static Logger log = Logger.getLogger(CLI.class);
   
 
  }
