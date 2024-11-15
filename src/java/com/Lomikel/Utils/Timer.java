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
    * @param modulusCommit The commit frequency (how many reports). */
  public Timer(String msg,
               int    modulus,
               int    commitF) {
    _msg     = msg;
    _modulus = modulus;
    _commitF = commitF;
    }
  
  /** Start timer. */
  public void start() {
    _t = System.currentTimeMillis();
    _i = 0;
    _j = 0;
    }

  /** Report snapshot.
    * @return If commit should be executed. */
  public boolean report() {
    _i++;
    if (_modulus > -1 && _i % _modulus != 0) {
      return false;
      }
    _j++;
    long dt = (System.currentTimeMillis() - _t) / 1000;
    if (dt == 0) {
      dt = 1;
      }
    log.info("" + _i + " " + _msg + " in " + dt + "s, freq = " + (_i / dt) + "Hz");
    if (_commitF > -1 && _j % _commitF == 0) {
	    return true;
      }
    return false;
    }    

  private long _t = 0;
  
  private int _i;
  
  private int _j;
  
  private String  _msg;
  
  private int _modulus;
  
  private int _commitF;

  /** Logging . */
  private static Logger log = LogManager.getLogger(Timer.class);

  }