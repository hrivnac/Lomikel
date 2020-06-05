package com.Lomikel.Apps;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Info;
import com.Lomikel.Utils.StringFile;
import com.Lomikel.Utils.StringResource;
import com.Lomikel.Utils.CommonException;

// CLI
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

// Bean Shell
import bsh.Interpreter;
import bsh.util.JConsole;
import bsh.EvalError;

// Java
import java.io.InputStreamReader;
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import java.awt.Color;
import java.awt.Font;
import java.awt.Dimension;
import javax.swing.JScrollPane;
import javax.swing.BorderFactory;


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
    * @param msg The message so show. */
  public CLI(String msg) {
    if (_batch) {
      _interpreter = new Interpreter();
      }
    else if (_gui) {
      JFrame f = new JFrame();
      JSplitPane pane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
      f.getContentPane().add(pane);
      f.setSize(600, 800);  
      f.setVisible(true);
      _console = new Console();
      pane.setLeftComponent(_console);
      _interpreter = new Interpreter(_console);
      }
    else {
      _interpreter = new Interpreter(new InputStreamReader(System.in), System.out, System.err, true);
      }
    if (!_quiet) {
      _interpreter.print(msg);
      }
    setupInterpreter();
    if (!_batch) {
      new Thread(_interpreter).start();
      }
    }

  /** Start and pass arguments on.
    * @param args The arguments. */
  public static void main(String[] args) {
    Init.init();
    parseArgs(args, "java -jar Lomikel.exe.jar");
    new CLI("Welcome to Lomikel CLI " + Info.release() + "\nhttp://cern.ch/hrivnac/Activities/Packages/Lomikel\n");
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
    
  /** Parse the cli arguments.
    * @param args    The cli arguments.
    * @param helpMsg The general help message. */
  public static void parseArgs(String[] args,
                                String   helpMsg) {
    CommandLineParser parser = new BasicParser();
    Options options = new Options();
    options.addOption("h", "help",  false, "show help");
    options.addOption("q", "quiet", false, "minimal direct feedback");
    options.addOption("g", "gui",   false, "run in a graphical window");
    options.addOption("b", "batch", false, "run in a batch");
    options.addOption(OptionBuilder.withLongOpt("source")
                                   .withDescription("source bsh file (init.bsh is also read)")
                                   .hasArg()
                                   .withArgName("file")
                                   .create("s"));
    try {
      CommandLine line = parser.parse(options, args );
      if (line.hasOption("help")) {
        new HelpFormatter().printHelp(helpMsg, options);
        System.exit(0);
        }
      if (line.hasOption("quiet")) {
        _quiet = true;
        }
      if (line.hasOption("gui")) {
        _gui = true;
        }
      if (line.hasOption("batch")) {
        _batch = true;
        }
      if (line.hasOption("source")) {
        _source = line.getOptionValue("source");
        }
      }
    catch (ParseException e) {
      new HelpFormatter().printHelp("java -jar AstroLabNet.exe.jar", options);
      System.exit(-1);
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
    
  /** Give {@link JConsole}.
    * @return The {@link JConsole}. */
  public static JConsole console() {
    return _console;
    }  
    
  private static String _profile;
  
  private static boolean _quiet = false;
  
  private static boolean _gui = false;
  
  private static boolean _batch = false;
  
  private static String _source = null;
    
  private Interpreter _interpreter;
  
  private static JConsole _console;

  /** Logging . */
  private static Logger log = Logger.getLogger(CLI.class);
   
 
  }