package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;

// Janus Graph
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;

// Java
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>JanusClient</code> provides connection to Janus Graph.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class JanusClient implements ModifyingGremlinClient {

  /** Extract implicite schema.
    * @param args[0] The operation: <tt>extract,populate</tt>.
    * @param args[1] The HBase hostname.
    * @param args[2] The HBase port.
    * @param args[3] The HBase table.
    * @throws Exception If fails. */ 
  public static void main(String[] args) throws Exception {
    Init.init("JanusClient");
    if (args[0].trim().equals("extract")) {
      JanusClient jc = new JanusClient(args[1],
                                       Integer.valueOf(args[2]),
                                       args[3]);
      GremlinRecipies gr = new GremlinRecipies(jc); 
      gr.createMetaSchema();
      }
    else {
      System.err.println("Unknown function " + args[0] + ", try extract");
      System.exit(-1);
      }
    System.exit(0);
    } 
       
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param table    The HBase table. */
  public JanusClient(String hostname,
                     int    port,
                     String table) {
    this(hostname, port, table, false);
    }
   
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param table    The HBase table.
    * @param batch    Whether open graph for batch loading. */
  public JanusClient(String  hostname,
                     int     port,
                     String  table,
                     boolean batch) {
    _hostname = hostname;
    _port     = port;
    _table    = table;
    _batch    = batch;
    Init.init("JanusClient");
    open();
    }
    
  /** Create with connection properties file.
    * @param properties The file with the complete properties. */
  public JanusClient(String properties) {
    Init.init("JanusClient");
    open(properties);
    }
    
  /** Open graph with already set parameters. */
  public void open() {
    log.info("Opening " + _table + "@" + _hostname);
    if (_batch) {
      log.info("\tas batch");
      }
    _graph = JanusGraphFactory.build()
                              .set("storage.backend",       "hbase")
                              .set("storage.hostname",      _hostname)
                              .set("storage.port",          _port)
                              .set("storage.hbase.table",   _table)
                              .set("storage.batch-loading", _batch)
                              .open();
    _g = _graph.traversal();
    log.info("Connected");
    }
    
  /** Open graph with file-based properties.
    * @param properties The file with the complete properties. */
  public void open(String properties) {
    log.info("Opening " + properties);
    Properties p = new Properties();
    try {
      FileInputStream propStream = new FileInputStream(properties);
      p.load(propStream);
      propStream.close();
      }
    catch (IOException e) {
      log.error("Properties " + properties + " cannot be loaded", e);
      }
    _hostname = p.getProperty("storage.hostname");
    _table    = p.getProperty("storage.hbase.table");
    _batch    = p.containsKey("storage.batch-loading") && p.getProperty("storage.batch-loading").equals("true");
    if (_batch) {
      log.info("\tas batch");
      }
    _graph = JanusGraphFactory.open(properties);
    _g = _graph.traversal();
    log.info("Connected");
    }
    
  @Override
  public void commit() {
    _graph.tx().commit();
    log.debug("Commited");
    }
    
  @Override
  public void close() {
    _graph.close();
    log.info("Closed");
    }
    
  /** Reopen graph (commit, close, open). */
  public void reopen() {
    commit();
    close();
    open();
    }
    
  @Override
  public GraphTraversalSource g() {
    return _g;
    }

  /** Start timer. */
  protected void timerStart() {
    _t = System.currentTimeMillis();
    }
    
  /** Timer snapshot. Report, commit.
    * @param msg           The message to use for loggiong.
    * @param i             The call number.
    * @param modulus       The <em>mod</em> to specify report frequency.
    * @param modulusCommit The <em>mod</em> to specify commit frequency.
    * @return              If any action has been commited. */
  protected boolean timer(String msg,
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
	    commit();
      }
    return true;
    }    
    
  private String _table;
  
  private String _hostname;
  
  private int _port;
  
  private boolean _batch;
    
  private boolean _found;  
    
  private JanusGraph _graph;
  
  private GraphTraversalSource _g;

  long _t = 0;

  /** Logging . */
  private static Logger log = LogManager.getLogger(JanusClient.class);

  }
