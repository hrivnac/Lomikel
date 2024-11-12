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
    }

  /** Timer snapshot. Report.
    * @param msg           The message to use for loggiong.
    * @param i             The call number.
    * @param modulus       The <em>mod</em> to specify report frequency.
    * @param modulusCommit The <em>mod</em> to specify commit frequency.
    * @return              If commit should be executed. */
  public boolean report(String msg,
                        int    i,
                        int    modulus,
                        int    modulusCommit) {
    if (i == 0) {
      return false;
      }
    if (modulus > -1 && i%modulus != 0) {
      return false;
      }
    long dt = (System.currentTimeMillis() - _t) / 1000;
    if (dt == 0) {
      dt = 1;
      }
    log.info("" + i + " " + msg + " in " + dt + "s, freq = " + (i / dt) + "Hz");
    if (modulusCommit > -1 && i%modulusCommit == 0) {
	    return true;
      }
    return false;
    }    

  long _t = 0;

  /** Logging . */
  private static Logger log = LogManager.getLogger(Timer.class);

  }