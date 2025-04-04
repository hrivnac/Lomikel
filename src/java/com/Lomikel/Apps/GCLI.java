package com.Lomikel.Apps;

import com.Lomikel.Utils.StringFile;
import com.Lomikel.Utils.StringResource;
import com.Lomikel.Utils.LomikelException;

// CLI
import org.apache.commons.cli.CommandLine;

// Groovy
import groovy.lang.GroovyShell;
import groovy.lang.Binding;
import org.apache.groovy.groovysh.Groovysh;
import org.codehaus.groovy.tools.shell.IO;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Groovy Command Line.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class GCLI extends CLI {

  /** Create. 
    * @param scriptSrc  The additional script to be executed.
    * @param scriptArgs The arguments for the additional script. */
  public GCLI(String scriptSrc,
              String scriptArgs) {
    super(scriptSrc, scriptArgs);
    }
    
  @Override
  public String execute() {
    _sharedData = new Binding();
    if (batch() || web()) {
      _shell = new GroovyShell(_sharedData);
      }
    else if (gui()) {
      GConsole console = new GConsole(_sharedData);
      _shell = console.console().getShell();
      new Thread(console).start();
      }
    else {
      IO io = new IO();
      GShell shell = new GShell(_sharedData);
      _sh = shell.shell();
      new Thread(shell).start();
      }
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
    _sharedData.setVariable("cli", this);
    log.info("cli set");
    StringFile     sf;
    StringResource sr;
    // Source init.groovy
    try {
      sf = new StringFile("init.groovy");
      if (sf.content() != null) {
        log.info("Sourcing init.groovy");
        result += evaluate(sf.content());
        }
      }
    catch (LomikelException e) {
      log.warn("init.groovy file cannot be read or processed: " + e.getMessage());
      log.debug("init.groovy file cannot be read or processed.", e);
      }
    // Load site profile
    if (profile() != null) {
      try {
        sr = new StringResource(profile() + ".groovy");
        if (sr.content() != null) { 
          log.info("Loading profile: " + profile());  
          result += evaluate(sr.content());
          }
        }
      catch (LomikelException e) {
        log.warn("Profile " + profile() + " cannot be loaded or processed: " + e.getMessage());
        log.debug("Profile " + profile() + " cannot be loaded or processed.", e);
        }
      }
    // Loading state
    try {
      sf = new StringFile(".state.groovy");
      if (sf.content() != null) {
        log.debug("Sourcing .state.groovy");
        result += evaluate(sf.content());
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
          result += evaluate(sf.content());
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
          result += shell().evaluate(scriptArgs() + sr.content());
          }
        }
      catch (LomikelException e) {
        log.error("Cannot read or process " + scriptSrc() + ": " + e.getMessage());
        log.debug("Cannot read or process" + scriptSrc(), e);
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
   
  /** Evaluate command in appropriate groovy shell
    * ({@link GroovyShell} or {@link Groovysh}).
    * @param cmd The commands to be evaluated.
    * @return    The result of the last command. */
  private String evaluate(String cmd) {
    if (shell() != null) {
      String result = "";
      Object resultO = shell().evaluate(cmd);
      if (resultO != null) {
        return resultO.toString();
        }
      }
    else if (sh() != null) {
      String result = "";
      Object resultO;
      for (String line : cmd.split("\\n")) {
        resultO = sh().execute(line);
        if (resultO != null) {
          result = resultO.toString();
          }
        }
      }
    else {
      log.error("No available shell, cannot evaluate " + cmd);
      }
    return "";
    }
    
  /** Give {@link GroovyShell}.
    * @return The {@link GroovyShell}. */
  public GroovyShell shell() {
    return _shell;
    }  
   
  /** Give {@link Groovysh}.
    * @return The {@link Groovysh}. */
  public Groovysh sh() {
    return _sh;
    }  
     
  protected static Binding  _sharedData;
  
  protected static GroovyShell _shell;
  
  protected static Groovysh _sh;
 
  /** Logging . */
  private static Logger log = LogManager.getLogger(GCLI.class);
   
 
  }
