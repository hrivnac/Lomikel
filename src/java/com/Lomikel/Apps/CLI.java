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

// Bean Shell
import bsh.Interpreter;
import bsh.util.JConsole;
import bsh.EvalError;

// Java
import java.io.InputStreamReader;

// AWT
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Color;
import java.awt.Dimension;

// Swing
import javax.swing.JFrame;
import javax.swing.JSplitPane;
import javax.swing.UIManager;
import javax.swing.ToolTipManager;
import javax.swing.AbstractButton;
import javax.swing.JSplitPane;
import javax.swing.JPopupMenu;
import javax.swing.JToolBar;
import javax.swing.BoxLayout;
import javax.swing.plaf.ColorUIResource;
import javax.swing.ImageIcon;

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
    * @param icon      The {@ImageIcon} for menu.
    * @param toolTiple The menu tooltip.
    * @param msg       The message so show. */
  public CLI(ImageIcon icon,
             String    toolTip,
             String    msg) {
    if (_batch) {
      _interpreter = new Interpreter();
      }
    else if (_gui) {
      JPopupMenu.setDefaultLightWeightPopupEnabled(false);
      ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
      UIManager.put("ToolTip.font",       new Font("Dialog", Font.PLAIN, 12));
      UIManager.put("ToolTip.foreground", new ColorUIResource(Color.red));
      UIManager.put("ToolTip.background", new ColorUIResource(0.95f, 0.95f, 0.3f));
      Dimension separatorDimension = new Dimension(10, 0);
   
      JFrame f = new JFrame();
      
      JToolBar north = new JToolBar();
      north.setFloatable(true);
      north.setLayout(new BoxLayout(north, BoxLayout.X_AXIS));
      north.add(new AboutLabel(icon, toolTip));
      north.addSeparator(separatorDimension);
      north.add(new SimpleButton("Exit",
                                 Icons.exit,
                                 AbstractButton.CENTER,
                                 Fonts.NONE,
                                 Dimensions.BIG,
                                 "Exit",
                                 new AListener()));
      f.getContentPane().add(north, BorderLayout.NORTH);

      JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true);
      _console = new Console();
      center.setLeftComponent(_console);
      f.getContentPane().add(center, BorderLayout.CENTER);
      
      f.setSize(1200, 600);  
      f.setVisible(true);      

      _interpreter = new Interpreter(_console);
      }
    else {
      _interpreter = new Interpreter(new InputStreamReader(System.in), System.out, System.err, true);
      }
    if (!_quiet) {
      if (_gui) {
        _console.setText(msg);
        }
      else {
        _interpreter.print(msg);
        }
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
    new CLI(Icons.lomikel,
            "<html><h3>http://cern.ch/hrivnac/Activities/Packages/Lomikel</h3></html>",
            "Welcome to Lomikel CLI " + Info.release() + "\nhttp://cern.ch/hrivnac/Activities/Packages/Lomikel\n");
    }

  /** Load standard init files and setup standard environment. */
  public void setupInterpreter() {
    // Set global reference and imports
    try {
      _interpreter.set("cli", this);
      log.info("cli set");
      }
    catch (EvalError e) {
      log.error("Cannot set cli", e);
      log.debug("Cannot set cli", e);
      }
    try {
      interpreter().eval("import com.Lomikel.HBaser.HBaseClient");
      log.info("HBaseClient imported");
      }
    catch (EvalError e) {
      log.error("Cannot import com.Lomikel.HBaser.HBaseClient");
      log.debug("Cannot import com.Lomikel.HBaser.HBaseClient", e);
      }
    try {
      interpreter().eval("import com.Lomikel.Januser.GremlinClient");
      log.info("GremlinClient imported");
      }
    catch (EvalError e) {
      log.error("Cannot import com.Lomikel.Januser.GremlinClient");
      log.debug("Cannot import com.Lomikel.Januser.GremlinClient", e);
      }      
    try {
      interpreter().eval("import com.Lomikel.Januser.SimpleGremlinClient");
      log.info("SimpleGremlinClient imported");
      }
    catch (EvalError e) {
      log.error("Cannot import com.Lomikel.Januser.SimpleGremlinClient");
      log.debug("Cannot import com.Lomikel.Januser.SimpleGremlinClient", e);
      }      
    try {
      interpreter().eval("import com.Lomikel.Januser.Hertex");
      log.info("Hertex imported");
      }
    catch (EvalError e) {
      log.error("Cannot import com.Lomikel.Januser.Hertex");
      log.debug("Cannot import com.Lomikel.Januser.Hertex", e);
      }      
    try {
      interpreter().eval("import com.Lomikel.Januser.Sertex");
      log.info("Sertex imported");
      }
    catch (EvalError e) {
      log.error("Cannot import com.Lomikel.Januser.Sertex");
      log.debug("Cannot import com.Lomikel.Januser.Sertex", e);
      }      
    try {
      interpreter().eval("import com.Lomikel.Phoenixer.PhoenixClient");
      log.info("PhoenixClient imported");
      }
    catch (EvalError e) {
      log.error("Cannot import com.Lomikel.Phoenixer.PhoenixClient");
      log.debug("Cannot import com.Lomikel.Phoenixer.PhoenixClient", e);
      }
    try {
      interpreter().eval("import com.Lomikel.Phoenixer.PhoenixProxyClient");
      log.info("PhoenixProxyClient imported");
      }
    catch (EvalError e) {
      log.error("Cannot import com.Lomikel.Phoenixer.PhoenixProxyClient");
      log.debug("Cannot import com.Lomikel.Phoenixer.PhoenixProxyClient", e);
      }
    String init = "";
    // Source init.bsh
    log.debug("Sourcing init.bsh");
    try {
      init = new StringFile("init.bsh").toString();
      _interpreter.eval(init);
      }
    catch (LomikelException e) {
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
      catch (LomikelException e) {
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
    catch (LomikelException e) {
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
      catch (LomikelException e) {
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
      new HelpFormatter().printHelp(helpMsg, options);
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
  
  private static Console _console;

  /** Logging . */
  private static Logger log = Logger.getLogger(CLI.class);
   
 
  }
