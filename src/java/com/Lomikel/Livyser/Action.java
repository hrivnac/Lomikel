package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Action</code> represents an action to be executed on Spark
  * Server, formulated as a simple text.
  * It can be written in Python, Scala, SQL or R.
  * You can send it to a Session opened on a Spark Server.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Action extends Element {  
  
  /** Create new Action.
    * @param name     The Action name.
    * @param cmd      The command.
    * @param language The {@link Language} od the command.
    * @param conf     The Sparc configuration (as JSON String). */
  public Action(String        name,
                String        cmd,
                Language      language,
                String        conf) {
    super(name);
    _cmd      = cmd;
    _language = language;
    _conf     = conf;
    }

  /** Give the associated command text.
    * @return The associated command text. */
  public String cmd() {
    return _cmd;
    }
    
  /** Give the Action {@link Language}.
    * @return The Action {@link Language}. */
  public Language language() {
    return _language;
    }
    
  /** Give the Spark configuration.
    * @return The Spark configuration. */
  public String conf() {
    return _conf;
    }
    
  /** Set as a new Action, so it will be stored on Exit. */
  public void setNew() {
    _new = true;
    }
    
  /** Whether is new, to be stored on Exit.
    * return Whether is new. */
  public boolean isNew() {
    return _new;
    }
    
  @Override
  public String toString() {
    return name() + " (" + _language + ")";
    }

  private String _cmd;
  
  private Language _language;
  
  private String _conf;
  
  private boolean _new = false;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(Action.class);

  }
