package com.Lomikel.Apps;

import com.Lomikel.Utils.StringFile;
import com.Lomikel.Utils.StringResource;
import com.Lomikel.Utils.LomikelException;

// CLI
import org.apache.commons.cli.CommandLine;

// Jython
import org.python.util.PythonInterpreter; 
import org.python.core.*; 

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Python Command Line.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class PYCLI extends CLI {

  /** Create. 
    * @param scriptSrc  The additional script to be executed.
    * @param scriptArgs The arguments for the additional script. */
  public PYCLI(String scriptSrc,
               String scriptArgs) {
    super(scriptSrc, scriptArgs);
    }
    
  @Override
  public String execute() {
    _interpreter = new PythonInterpreter();
    return setupShell();
    }

  @Override
  public void close() {
    }
    
  /** Load standard init files and setup standard environment.
    * @return The output of the setup. */
  public String setupShell() {
    String result = "";
    // Set global reference and imports
    _interpreter.set("cli", this);
    log.info("cli set");
    StringFile     sf;
    StringResource sr;     
    // Source init.py
    try {
      sf = new StringFile("init.py");
      if (sf.content() != null) {
        log.info("Sourcing init.py");
        result += _interpreter.eval(sf.content());
        }
      }
    catch (LomikelException e) {
      log.warn("init.groovy file cannot be read or processed: "+ e.getMessage());
      log.debug("init.groovy file cannot be read or processed.", e);
      }
    // Load site profile
    if (profile() != null) {
      try {
        sr = new StringResource(profile() + ".py");
        if (sr.content() != null) { 
          log.info("Loading profile: " + profile());  
          result += _interpreter.eval(sr.content());
          }
        }
      catch (LomikelException e) {
        log.warn("Profile " + profile() + " cannot be loaded or processed: " + e.getMessage());
        log.debug("Profile " + profile() + " cannot be loaded or processed.", e);
        }
      }
    // Loading state
    try {
      sf = new StringFile(".state.py");
      if (sf.content() != null) {
        log.debug("Sourcing .state.groovy");
        result += _interpreter.eval(sf.content());
        }
      }
    catch (LomikelException e) {
      log.warn(".state.groovy file cannot be read or processed: " + e.getMessage());
      log.debug(".state.groovy file cannot be read or processed.", e);
      }
    // Source command line source
    if (source() != null) {
      try {
        sf = new StringFile(source());
        if (sf.content() != null) {
          log.info("Sourcing " + source());
          //result += _interpreter.eval(sf.content());
          _interpreter.exec(sf.content());
          }
        }
      catch (LomikelException e) {
        log.warn(source() + " file cannot be read or processed: " + e.getMessage());
        log.debug(source() + " file cannot be read or processed", e);
        }
      }
    // Source embedded script
    if (scriptSrc() != null) {
      try {
        sr = new StringResource(scriptSrc());
        if (sr.content() != null) {
          log.info("Sourcing " + scriptSrc());
          result += interpreter().eval(scriptArgs() + sr.content());
          }
        }
      catch (LomikelException e) {
        log.error("Cannot read or process " + scriptSrc() + ": " + e.getMessage());
        log.debug("Cannot read or proces " + scriptSrc(), e);
        }
      }
    return result;
    }

  /** Parse the cli arguments.
    * @param args    The cli arguments.
    * @param helpMsg The general help message.
    * @return        The parsed {@link CommandLine}. */
  public static CommandLine parseArgs(String[] args,
                                      String   helpMsg) {
    return parseArgs(args, helpMsg, null);
    }
   
  /** Give {@link PythonInterpreter}.
    * @return The {@link PythonInterpreter}. */
  public PythonInterpreter interpreter() {
    return _interpreter;
    }  
  
  protected static PythonInterpreter _interpreter;
 
  /** Logging . */
  private static Logger log = LogManager.getLogger(PYCLI.class);
   
 
  }
