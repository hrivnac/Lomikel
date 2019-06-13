package com.JHTools.Utils;

// Log4J
import org.apache.log4j.Logger;
import org.apache.log4j.LogManager;
import org.apache.log4j.Level;
import org.apache.log4j.PropertyConfigurator;

// CLI
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;

// Java
import java.util.Enumeration;
import java.util.Arrays;

/** <code>Init></code> initialises <em>AstroLabNet</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: not static
public class Init {

  /** Minimal initialisation of <em>AstroLabNet</em>. */
  public static void init() {
    PropertyConfigurator.configure(Init.class.getClassLoader().getResource(PROPERTIES_MINIMAL));
    fixLog4J();
    Notifier.notify("");
    }
        
  /** Modify the default Log4J setup for external packages. */
  private static void fixLog4J() {
    for (String s : WARN) {
      Logger.getLogger(s).setLevel(Level.WARN);
      }
    for (String s : ERROR) {
      Logger.getLogger(s).setLevel(Level.ERROR);
      }
    //Enumeration<Logger> e = LogManager.getCurrentLoggers();
    //while (e.hasMoreElements()) {
    //  e.nextElement().setLevel(Level.WARN); 
    //  }
    }
    
  private static String[] WARN = {"org.apache.zookeeper.ZooKeeper",
                                  "org.apache.zookeeper.ClientCnxn"};
          
  private static String[] ERROR = {"org.apache.hadoop.hbase.HBaseConfiguration"};
  
  private static String PROPERTIES              = "com/JHTools/Utils/log4j.properties";
  private static String PROPERTIES_MINIMAL      = "com/JHTools/Utils/log4j-minimal.properties";
  private static String PROPERTIES_MINIMALISTIC = "com/JHTools/Utils/log4j-minimalistic.properties";
    
  /** Logging . */
  private static Logger log = Logger.getLogger(Init.class);

  }
