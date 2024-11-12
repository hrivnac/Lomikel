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

  /** Start timer. */
  public void start() {
    _t = System.currentTimeMillis();
    _i = 0;
    }

  /** Timer snapshot. Report.
    * @param msg           The message to use for logging.
    * @param modulus       The <em>mod</em> to specify report frequency.
    * @param modulusCommit The <em>mod</em> to specify commit frequency.
    * @return              If commit should be executed. */
  public boolean report(String msg,
                        int    modulus,
                        int    modulusCommit) {
    _i++;
    if (modulus > -1 && i%modulus != 0) {
      return false;
      }
    long dt = (System.currentTimeMillis() - _t) / 1000;
    if (dt == 0) {
      dt = 1;
      }
    log.info("" + _i + " " + msg + " in " + dt + "s, freq = " + (_i / dt) + "Hz");
    if (modulusCommit > -1 && _i%modulusCommit == 0) {
	    return true;
      }
    return false;
    }    

  long _t = 0;
  
  int _i;

  /** Logging . */
  private static Logger log = LogManager.getLogger(Timer.class);

  }