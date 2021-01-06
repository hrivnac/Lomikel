package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Info;
import com.Lomikel.HBaser.HBaseClient;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Direction;

// Janus Graph
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;

// Java
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.io.IOException;

// Log4J
import org.apache.log4j.Logger;

/** <code>JanusClient</code> provides connection to Janus Graph.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class JanusClient {

  /** Extract implicite schema.
    * @param args[0] The operation: <tt>extract,populate</tt>.
    * @param args[1] The zookeeper host.
    * @param args[2] The HBase table name.
    * @throws Exception If fails. */ 
  public static void main(String[] args) throws Exception {
    Init.init();
    JanusClient jc = new JanusClient(args[1], args[2]);
    if (args[0].trim().equals("extract")) {
      jc.createMetaSchema();
      }
    else if (args[0].trim().equals("populate")) {
      jc.populateGraph(args[3], new Integer(args[4]), args[5], args[6], args[7], args[8], args[9]);
      }
    else {
      System.err.println("Unknown function " + args[0] + ", try extract or populate");
      System.exit(-1);
      }
    System.exit(0);
    }   
  /** Create with default parameters. */
  public JanusClient() {
    this(Info.zookeeper(), Info.hbase_table());
    }
   
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param table    The HBase table. */
  public JanusClient(String hostname,
                     String table) {
    Init.init();
    log.info("Opening " + table + "@" + hostname);
    _graph = JanusGraphFactory.build()
                              .set("storage.backend",       "hbase")
                              .set("storage.hostname",      hostname)
                              .set("storage.hbase.table",   table)
                              .open();
    _g = _graph.traversal();
    log.info("Connected");
    }
    
  /** Commit transaction. */
  public void commit() {
    _graph.tx().commit();
    log.info("Commited");
    }
    
  /** Close graph. */
  public void close() {
    log.info("Closed");
    }
    
  /** Give {@link GraphTraversalSource}.
    * @return {@link GraphTraversalSource}. */
  public GraphTraversalSource g() {
    return _g;
    }
    
  /** Extract implicite schema. */
  public void createMetaSchema() {
    log.info("Cleaning MetaGraph");
    g().V().hasLabel("MetaGraph").drop().iterate();
    g().E().hasLabel("MetaGraph").drop().iterate();
    commit();
    Map<String, Set<String>> vMap  = new HashMap<>();
    Map<String, Set<String>> eMap  = new HashMap<>();
    Map<String, String>      evMap = new HashMap<>();
    Set<String> vSet;
    Set<String> eSet;
    Property<Vertex> vP;
    Property<Edge>   eP;
    log.info("Scanning Vertexes");
    for (Vertex v : g().V().toList()) {
      vSet = new HashSet<>();
      vMap.put(v.label(), vSet);
      for (Iterator<VertexProperty<Vertex>> i = v.properties(); i.hasNext();) { 
        vP = i.next();
        vSet.add(vP.key());
        }
      }
    log.info("Scanning Edges");
    for (Edge e : g().E().toList()) {
      eSet = new HashSet<>();
      eMap.put(e.label(), eSet);
      evMap.put(e.label(), e.outVertex().label() + " " + e.inVertex().label());
      for (Iterator<Property<Edge>> i = e.properties(); i.hasNext();) { 
        eP = i.next();
        eSet.add(eP.key());
        }
      }
    Vertex v;
    for (Map.Entry<String, Set<String>> entry : vMap.entrySet()) {
      log.info("Adding Vertex " + entry.getKey());
      try {
        v = g().addV("MetaGraph").next();
        v.property("MetaLabel", entry.getKey());
        for (String p : entry.getValue()) {
          v.property(p, "");
          }
        }
      catch (Exception e) {
        log.error("... failed");
        }
      }
    String[] vvS;
    Edge e;
    for (Map.Entry<String, String> entry : evMap.entrySet()) {
      log.info("Adding Edge " + entry.getKey() + " : " + entry.getValue());
      vvS = entry.getValue().split(" ");
      v = g().V().has("MetaGraph", "MetaLabel", vvS[0]).next();
      e = v.addEdge("MetaGraph", g().V().has("MetaGraph", "MetaLabel", vvS[1]).next());
      e.property("MetaLabel", entry.getKey());
      for (String p : eMap.get(entry.getKey())) {
        e.property(p, "");
        }
      }
    commit();
    close();
    }
    
  /** Populate JanusGraph from HBase table.
    * @param hbaseHost       The HBase hostname.
    * @param hbasePort       The HBase port.
    * @param hbasetable      The HBase table to replicate in Graph.
    * @param tableSchema     The HBase table schema name.
    * @param label           The label of newly created Vertexes.
    * @param rowkey          The row key name.
    * @param keyPrefixSearch The key prefix to limit replication to.
    * @throws IOException If anything goes wrong. */
  public void populateGraph(String hbaseHost,
                            int    hbasePort,
                            String hbaseTable,
                            String tableSchema,
                            String label,
                            String rowkey,
                            String keyPrefixSearch) throws IOException { 
    log.info("Cleaning Graph");
    g().V().hasLabel(label).drop().iterate();
    log.info("Connection to HBase table");
    HBaseClient hc = new HBaseClient(hbaseHost, hbasePort);
    hc.connect(hbaseTable, tableSchema); 
    Map<String, Map<String, String>> results = hc.scan(null, "key:key:" + keyPrefixSearch + ":prefix", "*", 0, false, false);
    log.info("Populating Graph");
    Vertex v;
    int i = 0;
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      if (!entry.getKey().startsWith("schema")) {
        i++;
        v = g().addV(label).next();
        v.property(rowkey, entry.getKey());
        for (Map.Entry<String, String> cell : entry.getValue().entrySet()) {
          v.property(cell.getKey().split(":")[1], cell.getValue()); // TBD: handle binary data
          }
        }
      }
    log.info("" + i + " " + label + "s created");
    commit();
    close();
    }
        
  /** Add a {@link Vertex}, unless it exists
    * @param label         The {@link Vertex} label.
    * @param propertyName  The name of {@link Vertex} property.
    * @param propertyValue The value of {@link Vertex} property.
    * @return              The {@link List} of created {@link Vertex}es. */
  // TBD: allow replacing
  // TBD: isn't it just one ?
  public List<Vertex> addOrCreate(String label,
                                  String propertyName,
                                  Object propertyValue) {
     List<Vertex> vertexes = g().V().has(label, propertyName, propertyValue)
                                   .fold()
                                   .coalesce(unfold(), 
                                             g().addV(label).property(propertyName, propertyValue)).toList();
     return vertexes;
     }
    
  /** Add an {@link Edge} between two {@link Vertex}s,
    * unless it exists.
    * @param v1       The first {@link Vertex}.
    * @param v2       The second {@link Vertex}.
    * @param relation The {@link Edge} name. */
  public void addEdge(Vertex v1,
                      Vertex v2,
                      String relation) {
    if (!checkEdge(v1, v2, relation)) {
      v1.addEdge(relation, v2);
      }
    }
    
  /** Check whether an {@link Edge} exists.
    * @param v1       The source {@link Vertex}.
    * @param v2       The destination {@link Vertex}.
    * @param relation The {@link Edge} name.
    * @return         Whether this {@link Edge} exists. */
  public boolean checkEdge(Vertex v1,
                           Vertex v2,
                           String relation) {
    _found = false;
    if (v1.vertices(Direction.OUT, relation).hasNext()) {
      v1.vertices(Direction.OUT, relation).forEachRemaining(v -> {
                                             if (v.equals(v2)) {
                                               _found = true;
                                               }
                                             });
      }
    return _found;
    }

  /** Start timer. */
  protected void timerStart() {
    _t = System.currentTimeMillis();
    }
    
  /** Timer snapshot. Report and commit.
    * @param msg           The message to use for loggiong.
    * @param i             The call number.
    * @param modulus       The <em>mod</em> to specify report frequency.
    * @param modulusCommit The <em>mod</em> to specify commit frequency. */
  protected void timer(String msg,
                       int    i,
                       int    modulus,
                       int    modulusCommit) {
    if (i == 0) {
      return;
      }
    if (i%modulus != 0) {
      return;
      }
    long dt = (System.currentTimeMillis() - _t) / 1000;
    if (dt == 0) {
      dt = 1;
      }
    log.info("" + i + " " + msg + " in " + dt + "s, freq = " + (i / dt) + "Hz");
    if (i%modulusCommit == 0) {
	    commit();
	    log.info("Committed");
      }
    }    
    
  private boolean _found;  
    
  private JanusGraph _graph;
  
  private GraphTraversalSource _g;

  long _t = 0;

  /** Logging . */
  private static Logger log = Logger.getLogger(JanusClient.class);

  }
