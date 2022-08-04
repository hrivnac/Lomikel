package com.Lomikel.Apps;

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
import org.apache.commons.cli.CommandLine;

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

/** BeanShell Command Line.
  * with usual interval operations.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class BSCLI extends CLI {

  /** Start {@link Interpreter} and run forever.
    * @param icon       The {@ImageIcon} for menu.
    * @param toolTiple  The menu tooltip.
    * @param msg        The message so show. 
    * @param scriptSrc  The additional script to be executed.
    * @param scriptArgs The arguments for the additional script. */
  public BSCLI(ImageIcon icon,
               String    toolTip,
               String    msg,
               String    scriptSrc,
               String    scriptArgs) {
    super(scriptSrc, scriptArgs);
    _icon    = icon;
    _toolTip = toolTip;
    _msg     = msg;
    }
    
  @Override
  public String execute() {
    if (batch() || web()) {
      _interpreter = new Interpreter();
      _interpreter.setExitOnEOF(true);
      }
    else if (gui()) {
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
      north.add(new AboutLabel(_icon, _toolTip));
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
    if (!quiet()) {
      if (gui()) {
        _console.setText(_msg);
        }
      else {    
        _interpreter.print(_msg);
        }
      }
    String result = setupInterpreter();
    if (!batch() && !web()) {
      new Thread(_interpreter).start();
      }
    return result;
    }
    
  @Override
  public void close() {
    // TBD
    }

  /** Load standard init files and setup standard environment. 
    * @return The output of the setup. */
  public String setupInterpreter() {
    String result = "";
    // Set global reference and imports
    try {
      interpreter().set("cli", this);
      log.info("cli set");
      }
    catch (EvalError e) {
      log.error("Cannot set cli", e);
      log.debug("Cannot set cli", e);
      }
    StringFile     sf;
    StringResource sr;
    // Source init.bsh
    try {
      sf = new StringFile("init.bsh");
      if (sf.content() != null) {
        log.info("Sourcing init.bsh");
        result += _interpreter.eval(sf.content());
        }
      }
    catch (LomikelException e) {
      log.warn("init.bsh file cannot be read.");
      log.debug("init.bsh file cannot be read.", e);
      }
    catch (EvalError e) {
      log.error("Can't evaluate standard BeanShell expression", e);
      }
    // Load site profile
    if (profile() != null) {
      try {
        sr = new StringResource(profile() + ".bsh");
        if (sr.content() != null) { 
          log.info("Loading profile: " + profile());  
          result += _interpreter.eval(sr.content());
          }
        }
      catch (LomikelException e) {
        log.warn("Profile " + profile() + " cannot be loaded.");
        log.debug("Profile " + profile() + " cannot be loaded.", e);
        }
      catch (EvalError e) {
        log.error("Can't evaluate standard BeanShell expression", e);
        }
      }
    // Loading state
    try {
      sf = new StringFile(".state.bsh");
      if (sf.content() != null) {
        log.info("Sourcing .state.bsh");
        result += _interpreter.eval(sf.content());
        }
      }
    catch (LomikelException e) {
      log.warn(".state.bsh file cannot be read.");
      log.debug(".state.bsh file cannot be read.", e);
      }
    catch (EvalError e) {
      log.error("Can't evaluate standard BeanShell expression", e);
      }
    // Source command line source
    if (source() != null) {
      try {
        sf = new StringFile(source());
        if (sf.content() != null) {
          log.info("Sourcing " + source());
          result += _interpreter.eval(sf.content());
          }
        }
      catch (LomikelException e) {
        log.warn(source() + " file cannot be read.");
        log.debug(source() + " file cannot be read.", e);
        }
      catch (EvalError e) {
        log.error("Can't evaluate standard BeanShell expression", e);
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
        log.error("Cannot read " + scriptSrc());
        log.debug("Cannot read " + scriptSrc(), e);
        }
      catch (EvalError e) {
        log.error("Cannot evaluate " + scriptArgs() + " " + scriptSrc());
        log.debug("Cannot evaluate " + scriptArgs() + " " + scriptSrc(), e);
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
     
  private ImageIcon _icon;
  
  private String    _toolTip;
  
  private String    _msg;
    
  private Interpreter _interpreter;
  
  private static Console _console;

  /** Logging . */
  private static Logger log = Logger.getLogger(BSCLI.class);
   
 
  }
