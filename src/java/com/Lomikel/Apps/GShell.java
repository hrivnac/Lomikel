package com.Lomikel.Apps;

// Groovy
import groovy.lang.Binding;
import groovy.console.ui.Console;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.apache.groovy.groovysh.Groovysh;
import org.codehaus.groovy.tools.shell.IO;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Groovy {@link Groovysh} as {@link Runnable}
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public final class GShell implements Runnable {
  
  /** Create Groovy {@link Groovysh} with {@link Binding}.
    * @param binding The associated {@link Binding}. */
  public GShell(Binding binding) {
    _shell = new Groovysh(binding, new IO());
    }
  
  @Override
  public void run() {
    _shell.run(null);
    }
    
  /** Give connected Groovy {@link Groovysh}.
    * @return The connected Groovy {@link Groovysh}. */
  public Groovysh shell() {
    return _shell;
    }
    
  private Groovysh _shell;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(GShell.class);

  }
