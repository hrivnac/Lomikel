package com.Lomikel.Livyser;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Batch represents a job on a Spark Server behind Livy Server.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Batch extends Element {
  
  /** Create new Batch.
    * @param name    The Batch name.
    * @param sender  The hosting {@link Sender}
    * @param id      The Batch id. */
  public Batch(String   name,
               Sender   sender,
               int      id) {
    super(name);
    _sender  = sender;
    _id      = id;
    }
    
  /** Give the keeping {@link Sender}.
    * @return The keeping {@link Sender}. */
  public Sender sender() {
    return _sender;
    }
    
  /** Give the Batch id.
    * @return The Batch id. */
  public int id() {
    return _id;
    }
    
  @Override
  public String toString() {
    return name();
    }
  
  private Sender _sender;
  
  private int _id;
 
  /** Logging . */
  private static Logger log = LogManager.getLogger(Batch.class);

  }
