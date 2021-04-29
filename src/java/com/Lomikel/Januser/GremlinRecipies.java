package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Info;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.fold;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.out;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.repeat;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Direction;

// Janus Graph
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;
import org.janusgraph.graphdb.vertices.StandardVertex;

// HBase
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Get;

// Java
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.IOException;

// Log4J
import org.apache.log4j.Logger;

/** <code>GremlinRecipies</code> provides various recipies to handle and modify Gramlin Graphs.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class GremlinRecipies {
    
  /** Create and attach to {@link GraphTraversalSource}.
    * @param g The attached  {@link GraphTraversalSource}. */
  public GremlinRecipies(GraphTraversalSource g) {
    _g = g;
    }
    
  /** Create and attach to {@link ModifyingGremlinClient}.
    * @param client The attached  {@link ModifyingGremlinClient}. */
  public GremlinRecipies(ModifyingGremlinClient client) {
    _client = client;
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

  /** Drop a {@link Vertex}.
    * @param label         The {@link Vertex} label.
    * @param propertyName  The name of {@link Vertex} property.
    * @param propertyValue The value of {@link Vertex} property.
    * @param deep          Whether to proceed recursively to all children. */
  public void drop(String  label,
                   String  propertyName,
                   Object  propertyValue,
                   boolean deep) {
    if (deep) {
      //g().V().has("lbl", label)
      //       .has(propertyName, propertyValue)
      //       .union(fold().unfold(), repeat(out()).emit())
      //       .drop();
      //g().V().has("lbl", label)
      //       .has(propertyName, propertyValue)
      //       .store("s")
      //       .repeat(out().store("s"))
      //       .cap("s")
      //       .unfold()
      //       .drop();
      List<Object> vv = g().V().has("lbl", label)
                               .has(propertyName, propertyValue)
                               .store("s")
                               .repeat(out().store("s"))
                               .cap("s")
                               .unfold().toList();
      StandardVertex v;
      for (Object o : vv) {
        v = (StandardVertex)o;
        v.remove();
        }
      }
    else {    
      g().V().has("lbl", label)
             .has(propertyName, propertyValue)
             .drop().iterate();
      }
    }              
       
  /** Get a {@link Vertex}, create it if necessary.
    * @param label         The {@link Vertex} label.
    * @param propertyName  The name of {@link Vertex} property.
    * @param propertyValue The value of {@link Vertex} property.
    * @return              The created {@link Vertex}es.
    *                      If multiple {@link Vertex}es exist, only thee first one is given. */
  // TBD: check if it is at least one
  public Vertex getOrCreate(String label,
                            String propertyName,
                            Object propertyValue) {
     return getOrCreates(label, propertyName, propertyValue).get(0);
     }
     
  /** Get a {@link Vertex}, create it if necessary.
    * @param label         The {@link Vertex} label.
    * @param propertyName  The name of {@link Vertex} property.
    * @param propertyValue The value of {@link Vertex} property.
    * @return              The created {@link Vertex}es. */
  // TBD: allow replacing
  // TBD: refactor
  public List<Vertex> getOrCreates(String label,
                                   String propertyName,
                                   Object propertyValue) {
     List<Vertex> vertexes = g().V().has("lbl", label)
                                    .has(propertyName, propertyValue)
                                    .fold()
                                    .coalesce(unfold(), 
                                              g().addV(label)
                                                 .property("lbl", label)
                                                 .property(propertyName, propertyValue)).toList();
     if (vertexes.size() > 1) {
       log.warn("" + vertexes.size() + " vertices found, only the first one returned");
       }
     else if (vertexes.size() == 0) {
       log.error("No vertex found");
       return null;
       }
     return vertexes;
     }
       
  /** Get a {@link Vertex}, create it if necessary.
    * @param label          The {@link Vertex} label.
    * @param propertyNames  The name of {@link Vertex} properties.
    * @param propertyValues The value of {@link Vertex} properties.
    * @return               The created {@link Vertex}. 
    *                       If multiple {@link Vertex}es exist, only thee first one is given. */
  // TBD: check if it is at least one
  public Vertex getOrCreate(String   label,
                            String[] propertyNames,
                            Object[] propertyValues) {
     return getOrCreates(label, propertyNames, propertyValues).get(0);
     }
     
  /** Get {@link Vertex}es, create them if necessary.
    * @param label          The {@link Vertex} label.
    * @param propertyNames  The name of {@link Vertex} properties.
    * @param propertyValues The value of {@link Vertex} properties.
    * @return               The created {@link Vertex}es. */
  // TBD: allow replacing
  public List<Vertex> getOrCreates(String label,
                                   String[] propertyNames,
                                   Object[] propertyValues) {
     List<Vertex> vertexes = hasProperties(g().V().has("lbl", label), propertyNames, propertyValues).fold()
                                                                                                    .coalesce(unfold(), addProperties(g().addV(label).property("lbl", label), propertyNames, propertyValues)).toList();
     if (vertexes.size() > 1) {
       log.warn("" + vertexes.size() + " vertices found, only the first one returned");
       }
     else if (vertexes.size() == 0) {
       log.error("No vertex found");
       return null;
       }
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
    
  /** Give {@link GraphTraversalSource}.
    * @return {@link GraphTraversalSource}. */
  public GraphTraversalSource g() {
    return _client == null ? _g : _client.g();
    }
  
  /** Commit, if operating via {@link ModifyingGremlinClient},
    * do nothing otherwise. */
  private void commit() {
    if (_client != null) {
      _client.commit();
      }
    }
    
  /** Close, if operating via {@link ModifyingGremlinClient},
    * do nothing otherwise. */
  private void close() {
    if (_client != null) {
      _client.close();
      }
    }
    
  /** Add multiple properties.
    * @param v      The {@link GraphTraversal} carrying {@link Vertex}es.
    * @param names  The properties names.
    * @param values The proerties values.
    * @return       The resulting  {@link GraphTraversal} carrying {@link Vertex}es. */
  // TBD check for the same length
  private GraphTraversal<Vertex, Vertex> hasProperties(GraphTraversal<Vertex, Vertex> v,
                                                       String[]                       names,
                                                       Object[]                       values) {
    for (int i = 0; i < names.length; i++) {
      v = v.has(names[i], values[i]);
      }
    return v;
    }
    
  /** Check multiple properties.
    * @param v      The {@link GraphTraversal} carrying {@link Vertex}es.
    * @param names  The properties names.
    * @param values The proerties values.
    * @return       The resulting  {@link GraphTraversal} carrying {@link Vertex}es. */
  // TBD check for the same length
  private GraphTraversal<Vertex, Vertex> addProperties(GraphTraversal<Vertex, Vertex> v,
                                                       String[]                       names,
                                                       Object[]                       values) {
    for (int i = 0; i < names.length; i++) {
      v.property(names[i], values[i]);
      }
    return v;
    }
    
  private GraphTraversalSource _g;
    
  private ModifyingGremlinClient _client;

  private boolean _found;

  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinRecipies.class);

  }
