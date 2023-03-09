package com.Lomikel.Januser;

import com.Lomikel.DB.Schema;
import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.LomikelException;

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
import org.apache.tinkerpop.gremlin.structure.Graph;

// Janus Graph
import org.janusgraph.graphdb.vertices.StandardVertex;
import org.janusgraph.graphdb.database.StandardJanusGraph;

// HBase
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Get;

// Java
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Iterator;
import java.util.Optional;

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
    * @param g The attached {@link GraphTraversalSource}. */
  public GremlinRecipies(GraphTraversalSource g) {
    _g = g;
    }
    
  /** Create and attach to {@link ModifyingGremlinClient}.
    * @param client The attached  {@link ModifyingGremlinClient}. */
  public GremlinRecipies(ModifyingGremlinClient client) {
    _client = client;
    _g      = client.g();
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
       
  /** Get {@link Vertex}es, create it if necessary.
    * @param label         The {@link Vertex} label.
    * @param propertyName  The name of {@link Vertex} property.
    * @param propertyValue The value of {@link Vertex} property.
    * @return              The created {@link Vertex}es.
    *                      If multiple {@link Vertex}es exist, only thee first one is given. */
  public List<Vertex> getOrCreate(String label,
                                  String propertyName,
                                  Object propertyValue) {
     return getOrCreate(label, new String[]{propertyName}, new Object[]{propertyValue});
     }
                
  /** Get {@link Vertex}es, create them if necessary.
    * @param label          The {@link Vertex} label.
    * @param propertyNames  The name of {@link Vertex} properties.
    * @param propertyValues The value of {@link Vertex} properties (<tt>*</tt> will skip search for that value).
    * @return               The created {@link Vertex}es. */
  public List<Vertex> getOrCreate(String   label,
                                  String[] propertyNames,
                                  Object[] propertyValues) {
     if (propertyNames.length != propertyValues.length) {
       log.error("Wrong number of search values: " + propertyValues.length + ", should be: " + propertyNames.length);
       return null;
       }
     List<Vertex> vertexes = hasProperties(g().V().has("lbl", label), propertyNames, propertyValues).toList();
     if (!vertexes.isEmpty()) {
       //log.debug(""  + vertexes.size() + " existing vertexes " + label + " found");
       _found = true;
       }
     else {
       //log.debug("No existing vertexes " + label + " found - searching backend databases");
       vertexes = addProperties(g().addV(label).property("lbl", label), propertyNames, propertyValues).toList();
       _found = false;
       }
     return vertexes;
     }
    
  /** Add an {@link Edge} between two {@link Vertex}s,
    * unless it exists.
    * @param v1       The first {@link Vertex}.
    * @param v2       The second {@link Vertex}.
    * @param relation The {@link Edge} name.
    * @return         Whether the {@link Edge} was added (as it didn'r eexiust yet). */
  public void addEdge(Vertex v1,
                      Vertex v2,
                      String relation) {
    boolean exists = checkEdge(v1, v2, relation)'
    if (!exists) {
      v1.addEdge(relation, v2);
      }
    return !exists;
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
    else {
      g().getGraph().tx().commit();
      }
    }
    
  /** Close, if operating via {@link ModifyingGremlinClient},
    * do nothing otherwise. */
  private void close() {
    if (_client != null) {
      _client.close();
      }
    }
    
  /** Check multiple properties.
    * @param v      The {@link GraphTraversal} carrying {@link Vertex}es.
    * @param names  The properties names.
    * @param values The proerties values (<tt>null</tt> will skip search for that value).
    * @return       The resulting  {@link GraphTraversal} carrying {@link Vertex}es. */
  private GraphTraversal<Vertex, Vertex> hasProperties(GraphTraversal<Vertex, Vertex> v,
                                                       String[]                       names,
                                                       Object[]                       values) {
     if (names.length != values.length) {
       log.error("Wrong number of search values: " + values.length + ", should be: " + names.length);
       return v;
       }
    for (int i = 0; i < names.length; i++) {
      if (values[i] != null) {
        v = v.has(names[i], values[i]);
        }
      }
    return v;
    }
    
  /** Add multiple properties.
    * @param v      The {@link GraphTraversal} carrying {@link Vertex}es.
    * @param names  The properties names.
    * @param values The properties values (<tt>null</tt> will skip search for that value).
    * @return       The resulting  {@link GraphTraversal} carrying {@link Vnew String(value)ertex}es. */
  private GraphTraversal<Vertex, Vertex> addProperties(GraphTraversal<Vertex, Vertex> v,
                                                       String[]                       names,
                                                       Object[]                       values) {
     if (names.length != values.length) {
       log.error("Wrong number of search values: " + values.length + ", should be: " + names.length);
       return v;
       }
    for (int i = 0; i < names.length; i++) {
      if (values[i] != null) {
        v.property(names[i], values[i]);
        }
      }
    return v;
    }
    
  /** Attach <em>datalink</em> {@link Vertex} to an existing {@link Vertex}.
    * @param vertex      The master {@link Vertex}.
    * @param name        The name of the datalink.
    * @param technology  The external database technology.
    * @param url         The url of the external database.
    * @param query       The query to get data from the external database.
    */
  public void attachDataLink(Vertex vertex,
                             String name,
                             String technology,
                             String url,
                             String query) {
  Vertex datalink = _g.addV("datalink").
                       property("lbl",        "datalink").
                       property("name",       name).
                       property("technology", technology).
                       property("url",        url).
                       property("query",      query).
                       next();
    addEdge(vertex, datalink, "from");
    }
    
  /** Give status of the most recent creation operation.
    * @return Whether the most recent <tt>#getOrCreate</tt>
    * or <tt>#checkEdge</tt> operation created new object. */
  public boolean created() {
    return !_found;
    }  

  /** Create {@link Edge} structure evaluating relations between pairs of {@link Vertex}es.
    * @param gt               The {@link GraphTraversal} to be pair-wise evaluated.
    * @param formula          The formula giving double value. It can contain any variables
    *                         from both {@link Vertex}es, they can be accessed as <code>variable[0]</code>
    *                         <code>variable[1]</code>. 
    * @param variables        The blank-separated list of variables, if they are not available from {@link Schema}.
    * @param threshold        The (high) threshold of the formula result for creation of the {@link Edge}
    *                         between {@link Vertex}es. I.e. the maximum distance between two {@link Vertex}es
    *                         to be included in the cluster.
    *                         <tt>0</tt> means no threshold.
    * @param edgeName         The name of the created {@link Edge}.
    * @param edgePropertyName The name of the {@link Edge} property carrying formula result.
    * @param commitN          The number of new {@link Edge}s for intermediate commit.
    *                         If total number of new {@link Edge}s is lower then <code>commitN</code>,
    *                         no commit is done. */
  public void structurise(GraphTraversal<Vertex, Vertex> gt,
                          String                         formula,
                          String                         variables,
                          double                         threshold,
                          String                         edgeName,
                          String                         edgePropertyName,
                          int                            commitN) {
    if (threshold == 0) {
      threshold = Double.MAX_VALUE;
      }
    formula = "Math.abs(" + formula + ")";
    Optional<Graph> o = gt.asAdmin().getGraph();
    if (!o.isPresent()) {
      log.error("Graph is not available");
      return;
      }
    Graph graph = o.get();
    if (!(graph instanceof StandardJanusGraph)) {
      log.error("" + graph + " is not StandardJanusGraph");
      return;
      }
    GremlinSchema schema = new GremlinSchema("schema", (StandardJanusGraph)graph);
    GremlinEvaluator evaluator = null;
    try {
      evaluator = new GremlinEvaluator(schema);
      }
    catch (LomikelException e) {
      log.error("Cannot create GremlonEvaluatir", e);
      return;
      }
    evaluator.setVariables(formula);
    if (variables != null) {
      evaluator.forceVariables(variables);
      }
    // Accumulate Vertex ids
    log.info("Accumulating Vertex ids...");
    Vertex v;
    Property<Vertex> p;
    Object id;
    Map<Object, Map<String, Object>> vMap = new HashMap<>(); // id -> (key -> value)
    Map<String, Object> pMap;
    while (gt.hasNext()) {
      v = gt.next();
      id = v.id();
      pMap = new HashMap<>();
      vMap.put(id, pMap);
      for (Iterator<VertexProperty<Vertex>> i = v.properties(); i.hasNext();) { 
        p = i.next();
        if (evaluator.hasVariable(p.key())) {
          pMap.put(p.key(), p.value());
          }
        }
      }
    log.info("" + vMap.size() + " ids accumulated");
    // Calculate scores
    log.info("Calculating scores...");
    Map<String, String> values;
    Map<String, Double> scores = new HashMap<>(); // id id -> score 
    double score = 0;
    List<Map.Entry<Object, Map<String, Object>>> entries = new ArrayList<>(vMap.entrySet());
    Map.Entry<Object, Map<String, Object>> entry1;
    Map.Entry<Object, Map<String, Object>> entry2;
    for (int i = 0; i < entries.size(); i++) {
      for (int j = i + 1; j < entries.size(); j++) {
        entry1 = entries.get(i);
        entry2 = entries.get(j);
        values = new HashMap<>();
        for (String var : evaluator.variables()) {
          evaluator.setVariable(var, new String[]{entry1.getValue().get(var).toString(), entry2.getValue().get(var).toString()});
          }
        try {
          score = evaluator.evalDouble(null, formula);
          if (score <= threshold) {
            scores.put(entry1.getKey() + " " + entry2.getKey(), score);
            log.info("" + i + " * " + j + " : " + score);
            }
          }
        catch (LomikelException e) {
          log.error("Cannot evaluate " + formula, e);
          }
        }
      }
    log.info("" + scores.size() + " scores calculated");
    // Sort scores
    log.info("Sorting scores...");
    scores = MapUtil.sortByValue(scores);
    // Create Edges
    log.info("Creating Edges...");
    Vertex v1;
    Vertex v2;
    String[] ids;
    int n = 0;
    for (Map.Entry<String, Double> entry : scores.entrySet()) {
      ids = entry.getKey().split(" ");
      score = entry.getValue();
      v1 = g().V(ids[0]).next();
      v2 = g().V(ids[1]).next();
      v1.addEdge(edgeName, v2, "lbl", edgeName, edgePropertyName, score);
      if (n++ % commitN == 0) {
        commit();
        }
      }
    if (n <= commitN) {
      commit();
      }
    log.info("" + n + " new Edges " + edgeName + " created");
    }   
    
  /** Clone a {@link Vertex} to another {@link GraphTraversalSource},
    * including connected {@link Vertex}es.
    * @param v          The {@link Vertex} to clone.
    * @param g1         The {@link GraphTraversalSource} to clone {@link Vertex} to.
    * @param depthIn    The depth of the parent {@link Vertex}es to clone
    *                   (negative value will clone the full up-tree).
    *                   The parents will not have their children cloned
    *                   unless <code>inclCycles = true</code>.
    * @param depthOut   The depth of the child {@link Vertex}es to clone
    *                   (negative value will clone the full down-tree).
    *                   The children will not have their parents cloned
    *                   unless <code>inclCycles = true</code>.
    * @param inclCycles Whether include cycles. If <code>false</code>,
    *                   function will only traverse in one direction (in or out),
    *                   without going back. If <true>true</code>, each step will
    *                   both directions.
    * @param onlyLabels Restrict replication to some labels. Can be <code>null</code>.
    * @return           The cloned {@link Vertex} or <code> null</code>. */
  public Vertex gimme(Vertex               v,
                      GraphTraversalSource g1,
                      int                  depthIn,
                      int                  depthOut,
                      boolean              inclCycles,
                      String[]             onlyLabels) {
    if (depthIn < 0) {
      depthIn = Integer.MAX_VALUE;
      }
    if (depthOut < 0) {
      depthOut = Integer.MAX_VALUE;
      }
    String label = v.label();
    if (onlyLabels != null && !Arrays.asList(onlyLabels).contains(label)) {
      return null;
      }
    long id = 0;
    if (inclCycles) {
      id = (Long)(v.id());
      if (_replicatedIds.containsKey(id)) {
        return g1.V(_replicatedIds.get(id)).next();
        }
      }
    Vertex v1 = g1.addV(label).next();
    if (inclCycles) {
      _replicatedIds.put(id, (Long)(v1.id()));
      }
    for (String key : v.keys()) {
      Iterator<VertexProperty<Double>> it = v.properties(key);
      while (it.hasNext()) {
        v1.property(key, it.next().value());
        }
      }
    Iterator<Edge> edges;
    Edge e;
    Edge e1;
    Vertex ve;
    Vertex ve1;
    if (depthIn > 0) {
      edges = v.edges(Direction.IN);
      while (edges.hasNext()) {
        e = edges.next();
        ve = e.outVertex();
        ve1 = gimme(ve, g1, depthIn - 1, inclCycles ? depthOut : 0, inclCycles, onlyLabels);
        if (ve1 != null) {
          e1 = ve1.addEdge(e.label(), v1);
          for (String key : e.keys()) {
            e1.property(key, e.property(key).value());
            }
          }
        }
      }
    if (depthOut > 0) {
      edges = v.edges(Direction.OUT);
      while (edges.hasNext()) {
        e = edges.next();
        ve = e.inVertex();
        ve1 = gimme(ve, g1, inclCycles ? depthIn : 0, depthOut - 1, inclCycles, onlyLabels);
        if (ve1 != null) {
          //e1 = v1.addEdge(e.label(), ve1);
          if (addEdge(v1, ve1, e.label())) {
            for (String key : e.keys()) {
              e1.property(key, e.property(key).value());
              }
            }
          }
        }
      }
    return v1;
    }
    
  private Map<Long, Long> _replicatedIds = new TreeMap<>(); // original id -> replicated id
     
  private GraphTraversalSource _g;
    
  private ModifyingGremlinClient _client;

  private boolean _found;

  /** Logging . */
  private static Logger log = Logger.getLogger(GremlinRecipies.class);

  }
