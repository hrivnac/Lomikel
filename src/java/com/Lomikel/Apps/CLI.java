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
public abstract class CLI {

  /** Create. */
  public CLI(String scriptSrc,
             String scriptArgs) {
    _scriptSrc  = scriptSrc;
    _scriptArgs = scriptArgs;
    }

  /** Start and pass arguments on.
    * @param args The arguments. */
  public static void main(String[] args) {
    Init.init();
    parseArgs(args, "java -jar Lomikel.exe.jar");
    CLI cli = null;
    if (_api.equals("bsh")) {
      log.info("Starting Lomikel BeanShell CLI");
      cli = new BSCLI(Icons.lomikel,
                      "<html><h3>http://cern.ch/hrivnac/Activities/Packages/Lomikel</h3></html>",
                      "Welcome to Lomikel CLI " + Info.release() + "\nhttp://cern.ch/hrivnac/Activities/Packages/Lomikel\n",
                      null,
                      null);
      }
    else if (_api.equals("groovy") ) {
      log.info("Starting Lomikel Groovy CLI");
      cli = new GCLI(null,
                     null);
      }
    else {
      log.fatal("Unknown api language " + _api);
      System.exit(-1);
      }
    System.out.println(cli.execute());
    System.exit(0);
    }
    
  /** TBD */
  public abstract String execute();
    
  /** Parse the cli arguments.
    * @param args    The cli arguments.
    * @param helpMsg The general help message.
    * @return        The parsed {@link CommandLine}. */
  public static CommandLine parseArgs(String[] args,
                                      String   helpMsg) {
    return parseArgs(args, helpMsg, null);
    }
    
  /** Parse the cli arguments.
    * @param args    The cli arguments.
    * @param helpMsg The general help message.
    * @param options The already initialised {@link Options}. May be <tt>null</tt>.
    * @return        The parsed {@link CommandLine}. */
  public static CommandLine parseArgs(String[] args,
                                      String   helpMsg,
                                      Options  options) {
    CommandLineParser parser = new BasicParser();
    if (options == null) {
      options = new Options();
      }
    options.addOption("h", "help",  false, "show help");
    options.addOption("q", "quiet", false, "minimal direct feedback");
    options.addOption("g", "gui",   false, "run in a graphical window");
    options.addOption("b", "batch", false, "run in a batch");
    options.addOption(OptionBuilder.withLongOpt("source")
                                   .withDescription("source script file (init.<language> is also sourced)")
                                   .hasArg()
                                   .withArgName("file")
                                   .create("s"));
    options.addOption(OptionBuilder.withLongOpt("api")
                                   .withDescription("cli language: [bsh|groovy]")
                                   .hasArg()
                                   .withArgName("language")
                                   .create("a"));
    try {
      CommandLine cline = parser.parse(options, args );
      if (cline.hasOption("help")) {
        new HelpFormatter().printHelp(helpMsg, options);
        System.exit(0);
        }
      if (cline.hasOption("quiet")) {
        _quiet = true;
        }
      if (cline.hasOption("gui")) {
        _gui = true;
        }
      if (cline.hasOption("batch")) {
        _batch = true;
        }
      if (cline.hasOption("api")) {
        _api = cline.getOptionValue("api");
        }
      else {
        _api = "bsh";
        }
      if (cline.hasOption("source")) {
        _source = cline.getOptionValue("source");
        }
      return cline;
      }
    catch (ParseException e) {
      new HelpFormatter().printHelp(helpMsg, options);
      System.exit(-1);
      }
    return null;
    }
    
  /** Set site profile.
    * @param profile The Resource path to the site profile. */
  public static void setProfile(String profile) {
    _profile = profile;
    }
    
  /** Set script source.
    * @param source The filename of the script source. */
  public static void setSource(String source) {
    _source = source;
    }
    
 /** TBD */
 public static String api() {
   return _api;
   }
    
  protected static String _profile;
  
  protected static String _source = null;
    
  protected static String _scriptArgs;  
    
  protected static String _scriptSrc;
  
  protected static String _api = null;
  
  protected static boolean _quiet = false;
  
  protected static boolean _gui = false;
  
  protected static boolean _batch = false;

  /** Logging . */
  private static Logger log = Logger.getLogger(CLI.class);
   
 
  }
