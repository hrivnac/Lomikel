package com.Lomikel.Apps;

// CLI
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

// Java
import java.io.PrintWriter;
import java.io.StringWriter;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Simple Command Line.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public abstract class CLI {

  /** Create. 
    * @param scriptSrc  The additional script to be executed.
    * @param scriptArgs The arguments for the additional script. */
  public CLI(String scriptSrc,
             String scriptArgs) {
    _scriptSrc  = scriptSrc;
    _scriptArgs = scriptArgs;
    }

  /** Execute command line in required way.
    * @return The execution result. */
  public abstract String execute();
  
  /** Close. */
  public abstract void close();
    
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
    options.addOption("h", "help",     false, "show help");
    options.addOption("q", "quiet",    false, "minimal direct feedback");
    options.addOption("g", "gui",      false, "run in a graphical window");
    options.addOption("b", "batch",    false, "run in a batch");
    options.addOption("w", "web",      false, "run as a web service");
    options.addOption("n", "notebook", false, "run in an notebook");
    options.addOption(OptionBuilder.withLongOpt("source")
                                   .withDescription("source script file (init.<language> is also sourced)")
                                   .hasArg()
                                   .withArgName("file")
                                   .create("s"));
    options.addOption(OptionBuilder.withLongOpt("api")
                                   .withDescription("cli language: [bsh|groovy|python] (othewise taken from source extension, defauld is groovy")
                                   .hasArg()
                                   .withArgName("language")
                                   .create("a"));
    try {
      CommandLine cline = parser.parse(options, args );
      if (cline.hasOption("quiet")) {
        _quiet = true;
        }
      if (cline.hasOption("gui")) {
        _gui = true;
        }
      if (cline.hasOption("batch")) {
        _batch = true;
        }
      if (cline.hasOption("web")) {
        _web   = true;
        _batch = true;
        }
      if (cline.hasOption("notebook")) {
        _notebook = true;
        }
      if (cline.hasOption("source")) {
        _source = cline.getOptionValue("source");
        }
      if (cline.hasOption("api")) {
        _api = cline.getOptionValue("api");
        }
      else if (_source != null) {
        String[] parts = _source.split("\\.");
        String ext = parts[parts.length - 1];
        switch (ext) {
          case "bsh":
            _api = "bsh";
            break;
          case "groovy":
            _api = "groovy";
            break;
          case "py":
            _api = "python";
            break;
          default:
            _api = "groovy"; // the default
            break;
          }
        }
      if (cline.hasOption("help")) {
        StringWriter out    = new StringWriter();
        PrintWriter  writer = new PrintWriter(out);
        new HelpFormatter().printHelp(writer, 80, helpMsg, "", options, 0, 0, "", true);
        writer.flush();
        _help = out.toString();
        }
      return cline;
      }
    catch (ParseException e) { 
      new HelpFormatter().printHelp(helpMsg, options);
      return null;
      }
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
    
  /** Give the script arguments.
    * @return The  script arguments. */
  public static String scriptArgs() {
    return _scriptArgs;
   }
     
  /** Give the script source.
    * @return The script source. */
  public static String scriptSrc() {
    return _scriptSrc;
   }
     
  /** Give the profile.
    * @return The profile. */
  public static String profile() {
    return _profile;
   }
     
  /** Give the source.
    * @return The source. */
  public static String source() {
    return _source;
   }
       
  /** Give the script language.
    * @return The script language. */
  public static String api() {
    return _api;
   }
   
  /** Tell whether running in a quiet mode.
    * @return Whether running in a quiet mode. */
  public static boolean quiet() {
    return _quiet;
   }
   
   /** Give the help.
    * @return The help. */
  public static String help() {
    return _help;
   }
  
  /** Tell whether running in gui.
    * @return Whether rinning in a gui. */
  public static boolean gui() {
    return _gui;
   }
   
  /** Tell whether running as a batch.
    * @return Whether running as a batch. */
  public static boolean batch() {
    return _batch;
   }
   
  /** Tell whether running as a Web Service.
    * @return Whether running as a Web Service. */
  public static boolean web() {
    return _web;
   }
   
  /** Tell whether running in a notebook.
    * @return Whether running in a notebook. */
  public static boolean notebook() {
    return _notebook;
   }
    
  private static String  _scriptArgs = null;  
                         
  private static String  _scriptSrc  = null;
                         
  private static String  _profile    = null;
                         
  private static String  _source     = null;
                         
  private static String  _api        = "groovy";
                                    
  private static String  _help       = "";
                                    
  private static boolean _quiet      = false;
                                     
  private static boolean _gui        = false;
                                     
  private static boolean _batch      = false;
                                     
  private static boolean _web        = false;
                                     
  private static boolean _notebook   = false;

  /** Logging . */
  private static Logger log = LogManager.getLogger(CLI.class);
   
 
  }
