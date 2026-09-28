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

/** Owns an embedded, rollback-capable JanusGraph connection.
  *
  * <p>This is the mutation client for recipes that need a real transaction.
  * It opens the graph from explicit HBase parameters or a JanusGraph
  * properties file and owns both the traversal source and graph lifecycle.</p>
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class JanusClient implements TransactionalGremlinClient {

  /** Extract the implicit schema.
    * @param args The operation ({@code extract}), HBase hostname, HBase port,
    *             and HBase table.
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
    * @param port     The HBase port.
    * @param table    The HBase table. */
  public JanusClient(String hostname,
                     int    port,
                     String table) {
    this(hostname, port, table, false);
    }
   
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param port     The HBase port.
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
    _properties = null;
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
    try (FileInputStream propStream = new FileInputStream(properties)) {
      p.load(propStream);
      }
    catch (IOException e) {
      throw new IllegalArgumentException("Properties " + properties + " cannot be loaded", e);
      }
    _hostname = p.getProperty("storage.hostname");
    _table    = p.getProperty("storage.hbase.table");
    _batch    = p.containsKey("storage.batch-loading") && p.getProperty("storage.batch-loading").equals("true");
    if (_batch) {
      log.info("\tas batch");
      }
    _graph = JanusGraphFactory.open(properties);
    _g = _graph.traversal();
    _properties = properties;
    log.info("Connected");
    }
    
  @Override
  public void commit() {
    _graph.tx().commit();
    log.debug("Commited");
    }

  @Override
  public void rollback() {
    _graph.tx().rollback();
    log.debug("Rolled back");
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
    if (_properties == null) {
      open();
      }
    else {
      open(_properties);
      }
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
    boolean report = modulus < 0 || (modulus > 0 && i%modulus == 0);
    boolean commit = modulusCommit > 0 && i%modulusCommit == 0;
    if (report) {
      long dt = (System.currentTimeMillis() - _t) / 1000;
      if (dt == 0) {
        dt = 1;
        }
      log.info("" + i + " " + msg + " in " + dt + "s, freq = " + (i / dt) + "Hz");
      }
    if (commit) {
      commit();
      }
    return report || commit;
    }    
    
  private String _table;
  
  private String _hostname;
  
  private int _port;
  
  private boolean _batch;

  /** File-based configuration, or {@code null} for explicit HBase parameters. */
  private String _properties;
    
  private JanusGraph _graph;
  
  private GraphTraversalSource _g;

  long _t = 0;

  /** Logging . */
  private static Logger log = LogManager.getLogger(JanusClient.class);

  }
