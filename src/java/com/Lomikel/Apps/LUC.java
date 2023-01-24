package com.Lomikel.Apps;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Info;
import com.Lomikel.Apps.CLI;
import com.Lomikel.Apps.BSCLI;
import com.Lomikel.Apps.GCLI;
import com.Lomikel.GUI.Icons;

// Log4J
import org.apache.log4j.Logger;

/** Simple Command Line.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class LUC {

  /** Create. */
  public LUC() {
    }

  /** Start and pass arguments on.
    * @param args The arguments. */
  public static void main(String[] args) {
    System.out.println(doit(args));
    if (cli().batch()) {
      System.exit(0);
      }
    }
    
  /** Start and pass arguments on.
    * @param args The arguments. */
  public static String doit(String[] args) {
    Init.init();
    CLI.parseArgs(args, "java -jar Lomikel.exe.jar");
    if (CLI.api().equals("bsh") ) {
      log.info("Starting Lomikel Universal Client in BeanShell");
      _cli = new BSCLI(Icons.lomikel,
                       "<html><h3>http://cern.ch/hrivnac/Activities/Packages/Lomikel</h3></html>",
                       "Welcome to Lomikel Universal Client " + Info.release() + "\nhttp://cern.ch/hrivnac/Activities/Packages/Lomikel\n" + CLI.help(),
                       null,
                       null);
      }
    else if (CLI.api().equals("groovy") ) {
      log.info("Starting Lomikel Universal Client in Groovy");
      _cli = new GCLI(null,
                      null);
      }
    else {
      log.fatal("Unknown api language " + CLI.api());
      return "FATAL: Unknown api language " + CLI.api();
      }
    return _cli.execute();
    }
    
  /** Give the embedded {@link CLI}.
    * @return The embedded {@link CLI}. */
  public static CLI cli() {
    return _cli;
    }
    
  private static CLI _cli;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(LUC.class);
   
 
  }
