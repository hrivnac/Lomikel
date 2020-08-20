package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Info;

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

// Log4J
import org.apache.log4j.Logger;

/** <code>JanusClient</code> provides connection to Janus Graph.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class JanusClient {

  /** Extract implicite schema. */ 
  public static void main(String[] args) {
    Init.init();
    JanusClient jc;
    if (args.length == 2) {
      jc = new JanusClient(args[0], args[1]);
      }
    else {
      jc = new JanusClient();
      }
    jc.metaSchema();
    System.exit(0);
    }
    
  /** Create, take connection parameters from configuration. */
  public JanusClient() {
    }
    
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param table    The HBase table. */
  public JanusClient(String hostname,
                     String table) {
    _hostname = hostname;
    _table    = table;
    }
    
  /** Extract implicite schema. */
  public void metaSchema() {
    open();
    log.info("Cleaning MetaGraph");
    g().V().hasLabel("MetaGraph").drop().iterate();
    g().E().hasLabel("MetaGraph").drop().iterate();
    Map<String, Set<String>> vMap  = new HashMap<>();
    Map<String, Set<String>> eMap  = new HashMap<>();
    Map<String, String>      evMap = new HashMap<>();
    Set<String> vSet;
    Set<String> eSet;
    Property<Vertex> vP;
    Property<Edge>   eP;
    for (Vertex v : g().V().limit(1000).toList()) {
      vSet = new HashSet<>();
      vMap.put(v.label(), vSet);
      for (Iterator<VertexProperty<Vertex>> i = v.properties(); i.hasNext();) { 
        vP = i.next();
        vSet.add(vP.key());
        }
      }
    for (Edge e : g().E().limit(1000).toList()) {
      eSet = new HashSet<>();
      eMap.put(e.label(), eSet);
      evMap.put(e.label(), e.outVertex().label() + " " + e.inVertex().label());
      for (Iterator<Property<Edge>> i = e.properties(); i.hasNext();) { 
        eP = i.next();
        eSet.add(eP.key());
        }
      }
    log.info(vMap);
    log.info(eMap);
    log.info(evMap);
    commit();
    Vertex v;
    for (Map.Entry<String, Set<String>> entry : vMap.entrySet()) {
      log.info("Adding Vertex " + entry.getKey());
      v = g().addV("MetaGraph").next();
      v.property("MetaLabel", entry.getKey());
      for (String p : entry.getValue()) {
        v.property(p, "");
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
   
  /** Open graph. */
  // BUG: too many opening
  protected GraphTraversalSource open() {
    return open(false);
    }
    
  /** Open graph.
    * @param batch Whether open for batch loading. */
  protected GraphTraversalSource open(boolean batch) {
    log.info("Opening connection to " + _table + "@" + _hostname);
    _graph = JanusGraphFactory.build()
                              .set("storage.backend",       "hbase")
                              .set("storage.hostname",      _hostname)
                              .set("storage.hbase.table",   _table)
                              //.set("storage.batch-loading", true) // only with schema
                              .open();
    _g = _graph.traversal();
    log.info("Connected");
    return _g;
    }
    
  /** Commit transaction. */
  protected void commit() {
    _graph.tx().commit();
    log.info("Commited");
    }
    
  /** Close graph. */
  protected void close() {
    log.info("Closed");
    }
    
  /** Give {@link GraphTraversalSource}.
    * @return {@link GraphTraversalSource}. */
  protected GraphTraversalSource g() {
    return _g;
    }
        
  /** Add a {@link Vertex}, unless it exists
    * @param label         The {@link Vertex} label.
    * @param propertyName  The name of {@link Vertex} property.
    * @param propertyValue The value of {@link Vertex} property.
    * @return              The {@link List} of created {@link Vertex}es. */
  // TBD: allow replacing
  // TBD: isn't it just one ?
  protected List<Vertex> addOrCreate(String label,
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
  protected void addEdge(Vertex v1,
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
  protected boolean checkEdge(Vertex v1,
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
    
  private String _hostname = Info.zookeeper();
  
  private String _table    = Info.hbase_table();
    
  private boolean _found;  
    
  private JanusGraph _graph;
  
  private GraphTraversalSource _g;

  long _t = 0;

  /** Logging . */
  private static Logger log = Logger.getLogger(JanusClient.class);

  }
