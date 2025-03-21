package com.Lomikel.Apps;

// Groovy
import groovy.lang.Binding;
import groovy.console.ui.Console;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Groovy {@link Console} as {@linki Runnable}
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class GConsole extends Console implements Runnable {

  /** Create Groovy {@link Console} with {@link Binding}.
    * @param binding The associated {@link Binding}. */
  public GConsole(Binding binding) {
    super(binding);
    }

  /** Create Groovy {@link Console}. */
  public GConsole() {
    super();
    }
  
  @Override
  public void run() {
    super.run();
    }
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Console.class);

  }
