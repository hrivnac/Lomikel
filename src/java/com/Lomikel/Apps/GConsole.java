package com.Lomikel.Apps;

// Groovy
import groovy.lang.Binding;
import groovy.console.ui.Console;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Groovy {@link Console} as {@link Runnable}
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class GConsole implements Runnable {
  
  /** Create Groovy {@link Console} with {@link Binding}.
    * @param binding The associated {@link Binding}. */
  public GConsole(Binding binding) {
    _console = new Console(binding);
    }
  
  @Override
  public void run() {
    _console.run();
    }
    
  /** Give connected Groovy {@link Console}.
    * @return The connected Groovy {@link Console}. */
  public Console console() {
    return _console;
    }
    
  private Console _console;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Console.class);

  }
