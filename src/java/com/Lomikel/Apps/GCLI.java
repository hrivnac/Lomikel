package com.Lomikel.Apps;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Info;
import com.Lomikel.Utils.StringFile;
import com.Lomikel.Utils.StringResource;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.GUI.AboutLabel;
import com.Lomikel.GUI.SimpleButton;
import com.Lomikel.GUI.Icons;
import com.Lomikel.GUI.Dimensions;
import com.Lomikel.GUI.Fonts;
import com.Lomikel.GUI.AListener;

// CLI
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

// Groovy
import groovy.lang.GroovyShell;
import groovy.lang.Binding;

// Java
import java.io.InputStreamReader;

// Log4J
import org.apache.log4j.Logger;

/** Groovy Command Line.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class GCLI extends CLI{

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
    if (batch()) {
      _shell = new GroovyShell(_sharedData);
      }
    else if (gui()) {
      _shell = new GroovyShell(_sharedData);
      }
    else {
      _shell = new GroovyShell(_sharedData);
      }
    return setupShell();
    }

  @Override
  public void close() {// TBD
    }
    
  /** Load standard init files and setup standard environment.
    * @return The output of the setup. */
  public String setupShell() {
    String result = "";
    // Set global reference and imports
    _sharedData.setVariable("cli", this);
    log.info("cli set");
    String init = "";
    // Source init.groovy
    log.info("Sourcing init.groovy");
    try {
      init = new StringFile("init.groovy").toString();
      result += _shell.evaluate(init);
      }
    catch (LomikelException e) {
      log.warn("init.groovy file cannot be read.");
      log.debug("init.groovy file cannot be read.", e);
      }
    // Load site profile
    if (profile() != null) {
      log.info("Loading profile: " + profile());  
      try {
        init = new StringResource(profile() + ".groovy").toString();
        result += _shell.evaluate(init);
        }
      catch (LomikelException e) {
        log.warn("Profile " + profile() + " cannot be loaded.");
        log.debug("Profile " + profile() + " cannot be loaded.", e);
        }
      }
    // Loading state
    log.debug("Sourcing .state.groovy");
    try {
      init = new StringFile(".state.groovy").toString();
      result += _shell.evaluate(init);
      }
    catch (LomikelException e) {
      log.warn(".state.groovy file cannot be read.");
      log.debug(".state.groovy file cannot be read.", e);
      }
    // Source command line source
    if (source() != null) {
      log.info("Sourcing " + source());
      try {
        init = new StringFile(source()).toString();
        result += _shell.evaluate(init);
        }
      catch (LomikelException e) {
        log.warn(source() + " file cannot be read.");
        log.debug(source() + " file cannot be read.", e);
        }
      }
    // Source embedded script
    if (scriptSrc() != null) {
      log.info("Sourcing " + scriptSrc());
      String script = "";
      try {
        script = new StringResource(scriptSrc()).toString();
        result += shell().evaluate(scriptArgs() + script);
        }
      catch (LomikelException e) {
        log.error("Cannot read " + scriptSrc());
        log.debug("Cannot read " + scriptSrc(), e);
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
   
  /** Give {@link GroovyShell}.
    * @return The {@link GroovyShell}. */
  public GroovyShell shell() {
    return _shell;
    }  
     
  protected static Binding     _sharedData;
  
  protected static GroovyShell _shell;
 
  /** Logging . */
  private static Logger log = Logger.getLogger(GCLI.class);
   
 
  }
