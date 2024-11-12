package com.Lomikel.Livyser;

// Java
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Sennder represents a connection on a Spark Server behind Livy Server.
  * You can send an Job to it.
  * Because Senders are not known to <em>Livy</em>,
  * they are registered here, together with related {@link Batch}s.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Sender extends Element
                    implements Comparable<Sender> {
  
  /** Create new Sender.
    * @param name     The Sender name.
    * @param server The {@link Server} keeping this Sender. */
  public Sender(String   name,
                Server   server) {
    super(name);
    _id = ++_maxId;
    _server = server;
    }
    
  @Override
  public int compareTo(Sender sender) {
    return toString().compareTo(sender.toString());
    }
    
  /** Give the keeping {@link Server}.
    * @return The keeping {@link Server}. */
  public Server server() {
    return _server;
    }
    
  /** Register {@link Batch} related to this Sender.
    * @param idBatch The {@link Batch} id. */
  public void registerBatch(int idBatch) {
    _batchOnSender.put(idBatch, this);
    register();
    }
    
  /** Register this Sender. */
  public void register() {
    _senders.add(this);
    }
    
  /** Give Sender for a {@link Batch}.
    * @param idBatch The {@link Batch} id.
    * @return        The hosting Sender. */
  public static Sender sender(int idBatch) {
    return _batchOnSender.get(idBatch);
    }
    
  /** Give all registered Senders.
    * @return The {@link Set} of all registered Senders. */
  public static Set<Sender> senders() {
    return _senders;
    }
  
  @Override
  public String toString() {
    return name() + " : " + _id + " on " + _server.name();
    }
  
  private Server _server;
    
  private static Map<Integer, Sender> _batchOnSender = new HashMap<>();
  
  private static Set<Sender> _senders = new TreeSet<>();
  
  private int _id;
  
  private static int _maxId;
 
  /** Logging . */
  private static Logger log = LogManager.getLogger(Sender.class);

  }
