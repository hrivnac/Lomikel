package com.Lomikel.Apps;

// Groovy
import groovy.lang.Binding;
import groovy.ui.Console;

// Log4J
import org.apache.log4j.Logger;

/** Groovy {@link Console} as {@linki Runnable}
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class GConsole extends Console implements Runnable {

  /** TBD */
  public GConsole(Binding binding) {
    super(binding);
    }

  /** TBD */
  public GConsole() {
    super();
    }
  
  @Override
  public void run() {
    super.run();
    }
  
  /** Logging . */
  private static Logger log = Logger.getLogger(Console.class);

  }
