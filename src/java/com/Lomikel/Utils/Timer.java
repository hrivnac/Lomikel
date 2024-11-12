package com.Lomikel.Utils;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Timer</code> helps to profile repeated calls.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Timer {

  /** Create.
    * @param msg           The message to use for logging.
    * @param modulus       The <em>mod</em> to specify report frequency.
    * @param modulusCommit The <em>mod</em> to specify commit frequency.
  public Timer(String msg,
               int    modulus,
               int    modulusCommit) {
    _msg           = msg;
    _modulus       = modulus;
    _modulusCommit = modulusCommit;
    }
  
  /** Start timer. */
  public void start() {
    _t = System.currentTimeMillis();
    _i = 0;
    }

  /** Report snapshot.
    * @return If commit should be executed. */
  public boolean report() {
    _i++;
    if (_modulus > -1 && _i % _modulus != 0) {
      return false;
      }
    long dt = (System.currentTimeMillis() - _t) / 1000;
    if (dt == 0) {
      dt = 1;
      }
    log.info("" + _i + " " + _msg + " in " + dt + "s, freq = " + (_i / dt) + "Hz");
    if (_modulusCommit > -1 && _i % _modulusCommit == 0) {
	    return true;
      }
    return false;
    }    

  private long _t = 0;
  
  private int _i;
  
  private String  _msg;
  
  private int _modulus;
  
  private int _modulusCommit;

  /** Logging . */
  private static Logger log = LogManager.getLogger(Timer.class);

  }