package com.Lomikel.Januser;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.fold;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.out;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.repeat;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV;


// Java
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Common graph mutation and lifecycle recipes.
  *
  * <p>The package documentation describes client modes, transaction ownership,
  * and the native-label/{@code lbl} invariant used by these operations.</p>
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
  
  /** Start a traversal over all vertices.
    * @return A traversal over all vertices in the attached graph. */
  public GraphTraversal<Vertex, Vertex> allV() {
    return g().V();
    }


  /** Extract the implicit schema into the meta-schema subgraph. */
  public void createMetaSchema() {
    GraphTraversalSource source = g();
    log.info("Cleaning MetaGraph");
    source.V().hasLabel("MetaGraph").drop().iterate();
    source.E().hasLabel("MetaGraph").drop().iterate();
    commit();
    Map<String, Set<String>> vMap  = new HashMap<>();
    Map<String, Set<String>> eMap  = new HashMap<>();
    Map<String, Set<List<String>>> evMap = new HashMap<>();
    Set<String> vSet;
    Set<String> eSet;
    Property<Vertex> vP;
    Property<Edge>   eP;
    log.info("Scanning Vertexes");
    GraphTraversal<Vertex, Vertex> vertexes = source.V();
    while (vertexes.hasNext()) {
      Vertex vertex = vertexes.next();
      vSet = vMap.computeIfAbsent(vertex.label(), key -> new HashSet<>());
      for (Iterator<VertexProperty<Vertex>> i = vertex.properties(); i.hasNext();) {
        vP = i.next();
        vSet.add(vP.key());
        }
      }
    log.info("Scanning Edges");
    GraphTraversal<Edge, Edge> edges = source.E();
    while (edges.hasNext()) {
      Edge edge = edges.next();
      eSet = eMap.computeIfAbsent(edge.label(), key -> new HashSet<>());
      evMap.computeIfAbsent(edge.label(), key -> new HashSet<>()).
            add(List.of(edge.outVertex().label(), edge.inVertex().label()));
      for (Iterator<Property<Edge>> i = edge.properties(); i.hasNext();) {
        eP = i.next();
        eSet.add(eP.key());
        }
      }
    Map<String, Vertex> metaVertexes = new HashMap<>();
    Vertex v;
    for (Map.Entry<String, Set<String>> entry : vMap.entrySet()) {
      log.info("Adding Vertex " + entry.getKey());
      try {
        v = source.addV("MetaGraph").property("lbl", "MetaGraph").next();
        v.property("MetaLabel", entry.getKey());
        for (String p : entry.getValue()) {
          if (!p.equals("lbl")) {
            v.property(p, "");
            }
          }
        metaVertexes.put(entry.getKey(), v);
        }
      catch (Exception e) {
        log.error("... failed");
        }
      }
    Edge e;
    for (Map.Entry<String, Set<List<String>>> entry : evMap.entrySet()) {
      for (List<String> endpoints : entry.getValue()) {
        log.info("Adding Edge " + entry.getKey() + " : " + endpoints);
        Vertex outVertex = metaVertexes.get(endpoints.get(0));
        Vertex inVertex  = metaVertexes.get(endpoints.get(1));
        if (outVertex == null) {
          outVertex = source.V().has("MetaGraph", "MetaLabel", endpoints.get(0)).next();
          }
        if (inVertex == null) {
          inVertex = source.V().has("MetaGraph", "MetaLabel", endpoints.get(1)).next();
          }
        e = outVertex.addEdge("MetaGraph", inVertex);
        e.property("lbl", "MetaGraph");
        e.property("MetaLabel", entry.getKey());
        for (String p : eMap.get(entry.getKey())) {
          if (!p.equals("lbl")) {
            e.property(p, "");
            }
          }
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
      Set<Object> visited = new HashSet<>(g().V().has("lbl", label).
                                                   has(propertyName, propertyValue).
                                                   id().
                                                   toList());
      Set<Object> frontier = new HashSet<>(visited);
      while (!frontier.isEmpty()) {
        Set<Object> next = new HashSet<>();
        for (Object id : g().V(frontier.toArray()).out().id().toList()) {
          if (visited.add(id)) {
            next.add(id);
            }
          }
        frontier = next;
        }
      List<Object> batch = new ArrayList<>(1000);
      for (Object id : visited) {
        batch.add(id);
        if (batch.size() == 1000) {
          g().V(batch.toArray()).drop().iterate();
          batch.clear();
          }
        }
      if (!batch.isEmpty()) {
        g().V(batch.toArray()).drop().iterate();
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
  public GraphTraversal<Vertex, Vertex> getOrCreate(String label,
                                                    String propertyName,
                                                    Object propertyValue) {
     return getOrCreate(label, new String[]{propertyName}, new Object[]{propertyValue});
     }
                
  /** Get {@link Vertex}es, create them if necessary.
    * @param label          The {@link Vertex} label.
    * @param propertyNames  The name of {@link Vertex} properties.
    * @param propertyValues The value of {@link Vertex} properties ({@code *} will skip search for that value).
    * @return               The created {@link Vertex}es. */
  public synchronized GraphTraversal<Vertex, Vertex> getOrCreate(String   label,
                                                                 String[] propertyNames,
                                                                 Object[] propertyValues) {
     if (propertyNames.length != propertyValues.length) {
       log.error("Wrong number of search values: " + propertyValues.length + ", should be: " + propertyNames.length);
       return null;
       }
     GraphTraversal<Vertex, Vertex> vertexes = hasProperties(g().V().hasLabel(label).has("lbl", label),
                                                              propertyNames, propertyValues);
     Vertex vertex;
     if (vertexes.hasNext()) {
       vertex = vertexes.next();
       _found.set(true);
       }
     else {
       vertex = addProperties(g().addV(label).property("lbl", label),
                              propertyNames, propertyValues).next();
       _found.set(false);
       }
     return g().V(vertex.id());
     }
    
  /** Add an {@link Edge} between two {@link Vertex}s,
    * unless it exists.
    * @param v1       The first {@link Vertex}.
    * @param v2       The second {@link Vertex}.
    * @param relation The {@link Edge} name. */
  public synchronized void addEdge(Vertex v1,
                      Vertex v2,
                      String relation) {
    v1 = Wertex.unwrap(v1);
    v2 = Wertex.unwrap(v2);
    if (!checkEdge(v1, v2, relation)) {
      v1.addEdge(relation, v2).
         property("lbl", relation);
      }
    }
    
  /** Add an {@link Edge} between two {@link Vertex}s,
    * unless it exists. Add properties.
    * @param v1       The first {@link Vertex}.
    * @param v2       The second {@link Vertex}.
    * @param relation The {@link Edge} name.
    * @param names    The names of the properties to be added.
    * @param values   The values of the properties to be added. 
    * @param reset    Whether reset properties of already existing {@link Edge}. */
  public synchronized void addEdge(Vertex  v1,
                      Vertex  v2,
                      String  relation,
                      String[] names,
                      Double[] values,
                      boolean  reset) {
    validateEdgeProperties(names, values);
    v1 = Wertex.unwrap(v1);
    v2 = Wertex.unwrap(v2);
    boolean create = !checkEdge(v1, v2, relation);
    if (create) {
      Edge e = v1.addEdge(relation, v2);
      e.property("lbl", relation);
      for (int i = 0; i < names.length; i++) {
        if (!names[i].equals("lbl")) {
          e.property(names[i], values[i]);
          }
        }
      }
    if (!create && reset) {
      List<Edge> edges = getEdge(v1, v2, relation);
      if (edges.size() != 1) {
        log.error("" + edges.size() + " " + relation + " edges exists, none modified");
        }
      else {
        Edge e = edges.get(0);
        e.property("lbl", relation);
        for (int i = 0; i < names.length; i++) {
          if (!names[i].equals("lbl")) {
            e.property(names[i], values[i]);
            }
          }
        }
      }
    }
    
  /** Add an {@link Edge} between two {@link Vertex}s,
    * unless it exists. Add properties.
    * @param v1       The first {@link Vertex}.
    * @param v2       The second {@link Vertex}.
    * @param relation The {@link Edge} name.
    * @param names    The names of the properties to be added.
    * @param values   The values of the properties to be added. 
    * @param reset    Whether reset properties of already existing {@link Edge}. */
  public synchronized void addEdge(Vertex   v1,
                      Vertex   v2,
                      String   relation,
                      String[] names,
                      String[] values,
                      boolean  reset) {
    validateEdgeProperties(names, values);
    v1 = Wertex.unwrap(v1);
    v2 = Wertex.unwrap(v2);
    boolean create = !checkEdge(v1, v2, relation);
    if (create) {
      Edge e = v1.addEdge(relation, v2);
      e.property("lbl", relation);
      for (int i = 0; i < names.length; i++) {
        if (!names[i].equals("lbl")) {
          e.property(names[i], values[i]);
          }
        }
      }
    if (!create && reset) {
      List<Edge> edges = getEdge(v1, v2, relation);
      if (edges.size() != 1) {
        log.error("" + edges.size() + " edges exists, none modified");
        }
      else {
        Edge e = edges.get(0);
        e.property("lbl", relation);
        for (int i = 0; i < names.length; i++) {
          if (!names[i].equals("lbl")) {
            e.property(names[i], values[i]);
            }
          }
        }
      }
    }
    
  /** Add an edge with heterogeneously typed properties.
    * @param v1       The outgoing endpoint.
    * @param v2       The incoming endpoint.
    * @param relation The native edge label, also copied to {@code lbl}.
    * @param names    The property names.
    * @param values   The property values in the same order as {@code names}.
    * @param reset    Whether to replace properties on one existing edge. */
  public synchronized void addEdge(Vertex   v1,
                      Vertex   v2,
                      String   relation,
                      String[] names,
                      Object[] values,
                      boolean  reset) {
    validateEdgeProperties(names, values);
    v1 = Wertex.unwrap(v1);
    v2 = Wertex.unwrap(v2);
    boolean create = !checkEdge(v1, v2, relation);
    if (create) {
      Edge e = v1.addEdge(relation, v2);
      e.property("lbl", relation);
      for (int i = 0; i < names.length; i++) {
        if (!names[i].equals("lbl")) {
          e.property(names[i], values[i]);
          }
        }
      }
    if (!create && reset) {
      List<Edge> edges = getEdge(v1, v2, relation);
      if (edges.size() != 1) {
        log.error("" + edges.size() + " edges exists, none modified");
        }
      else {
        Edge e = edges.get(0);
        e.property("lbl", relation);
        for (int i = 0; i < names.length; i++) {
          if (!names[i].equals("lbl")) {
            e.property(names[i], values[i]);
            }
          }
        }
      }
    }

  /** Validate parallel edge-property arrays before any graph mutation. */
  private static void validateEdgeProperties(String[] names, Object[] values) {
    if (names == null || values == null || names.length != values.length) {
      throw new IllegalArgumentException("Edge property names and values must have equal lengths");
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
    Vertex source = Wertex.unwrap(v1);
    Vertex target = Wertex.unwrap(v2);
    Iterator<Vertex> vertices = source.vertices(Direction.OUT, relation);
    while (vertices.hasNext()) {
      if (vertices.next().equals(target)) {
        _found.set(true);
        return true;
        }
      }
    _found.set(false);
    return false;
    }
    
  /** Give all {@link Edge} between {@link Vertex}es..
    * @param v1       The source {@link Vertex}.
    * @param v2       The destination {@link Vertex}.
    * @param relation The {@link Edge} name.
    * @return         The {@link List} of found {@link Edge}es. */
  public List<Edge> getEdge(Vertex v1,
                            Vertex v2,
                            String relation) {
  v1 = Wertex.unwrap(v1);
  v2 = Wertex.unwrap(v2);
  List<Edge> edges = g().V(v1).outE(relation).filter(inV().is(v2)).toList();
  return edges;
  }
    
  /** Give {@link GraphTraversalSource}.
    * @return {@link GraphTraversalSource}. */
  public GraphTraversalSource g() {
    return _client == null ? _g : _client.g();
    }
    
  /** Give {@link Graph}.
    * @return {@link Graph}. */
  public Graph graph() {
    return g().getGraph();
    }
  
  
  /** Commit. */
  public void commit() {
    if (_client != null) {
      _client.commit();
      }
    else if (g().getGraph().features().graph().supportsTransactions()) {
      g().getGraph().tx().commit();
      }
    }

  /** Whether the attached client or graph supports rollback-capable transactions.
    * @return {@code true} when a logical operation can be committed or rolled back atomically. */
  public boolean supportsTransactions() {
    if (_client != null) {
      return _client instanceof TransactionalGremlinClient;
      }
    return g().getGraph().features().graph().supportsTransactions();
    }

  /** Roll back the current transaction. */
  public void rollback() {
    if (_client != null) {
      if (!(_client instanceof TransactionalGremlinClient)) {
        throw new UnsupportedOperationException("Gremlin client does not support rollback-capable transactions");
        }
      ((TransactionalGremlinClient)_client).rollback();
      }
    else {
      g().getGraph().tx().rollback();
      }
    }
    
  /** Close, if operating via {@link ModifyingGremlinClient},
    * do nothing otherwise. */
  private void close() {
    if (_client != null) {
      _client.close();
      }
    }
    
  /** Return the owning {@link ModifyingGremlinClient}.
    * @return The owning client, or {@code null} when this recipe was attached
    *         directly to a traversal source. */
  public ModifyingGremlinClient client() {
    return _client;
    }
    
  /** Check multiple properties.
    * @param v      The {@link GraphTraversal} carrying {@link Vertex}es.
    * @param names  The properties names.
    * @param values The property values ({@code null} or {@code "*"} will skip that property).
    * @return       The resulting  {@link GraphTraversal} carrying {@link Vertex}es. */
  private GraphTraversal<Vertex, Vertex> hasProperties(GraphTraversal<Vertex, Vertex> v,
                                                       String[]                       names,
                                                       Object[]                       values) {
     if (names.length != values.length) {
       log.error("Wrong number of search values: " + values.length + ", should be: " + names.length);
       return v;
       }
    for (int i = 0; i < names.length; i++) {
      if (!names[i].equals("lbl") && !skipProperty(values[i])) {
        v = v.has(names[i], values[i]);
        }
      }
    return v;
    }
    
  /** Add multiple properties.
    * @param v      The {@link GraphTraversal} carrying {@link Vertex}es.
    * @param names  The properties names.
    * @param values The property values ({@code null} or {@code "*"} will skip that property).
    * @return       The resulting {@link GraphTraversal} carrying {@link Vertex}es. */
  private GraphTraversal<Vertex, Vertex> addProperties(GraphTraversal<Vertex, Vertex> v,
                                                       String[]                       names,
                                                       Object[]                       values) {
     if (names.length != values.length) {
       log.error("Wrong number of search values: " + values.length + ", should be: " + names.length);
       return v;
       }
    for (int i = 0; i < names.length; i++) {
      if (!names[i].equals("lbl") && !skipProperty(values[i])) {
        v.property(names[i], values[i]);
        }
      }
    return v;
    }

  /** Whether a property value is the documented lookup/creation wildcard. */
  private boolean skipProperty(Object value) {
    return value == null || "*".equals(value);
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
    
  /** Return the status of the most recent creation operation in this thread.
    * @return Whether the most recent {@code getOrCreate} or {@code checkEdge}
    *         operation created a new element. */
  public boolean created() {
    return !_found.get();
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
    *                   without going back. If {@code true}, each step will
    *                   traverse both directions.
    * @param onlyLabels Restrict replication to some labels. Can be <code>null</code>.
    * @return           The cloned {@link Vertex} or <code> null</code>. */
  public Vertex gimme(Vertex               v,
                      GraphTraversalSource g1,
                      int                  depthIn,
                      int                  depthOut,
                      boolean              inclCycles,
                      String[]             onlyLabels) {
    return gimme(v, g1, depthIn, depthOut, inclCycles, onlyLabels,
                 new HashMap<>(), new HashSet<>(), new HashMap<>());
    }

  /** Clone one vertex within a single replication operation. */
  private Vertex gimme(Vertex               v,
                       GraphTraversalSource g1,
                       int                  depthIn,
                       int                  depthOut,
                       boolean              inclCycles,
                       String[]             onlyLabels,
                       Map<Object, Vertex>  replicatedVertices,
                       Set<Object>          replicatedEdges,
                       Map<Object, List<int[]>> explorationFrontiers) {
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
    Object id = v.id();
    Vertex v1 = replicatedVertices.get(id);
    if (v1 == null) {
      v1 = g1.addV(label).next();
      VertexProperty<Object> clonedLabel =
        v1.property(VertexProperty.Cardinality.single, "lbl", label);
      Iterator<VertexProperty<Object>> sourceLabels = v.properties("lbl");
      while (sourceLabels.hasNext()) {
        VertexProperty<Object> sourceLabel = sourceLabels.next();
        for (String metaKey : sourceLabel.keys()) {
          clonedLabel.property(metaKey, sourceLabel.property(metaKey).value());
          }
        }
      replicatedVertices.put(id, v1);
      for (String key : v.keys()) {
        if (key.equals("lbl")) {
          continue;
          }
        List<VertexProperty<Object>> sourceProperties = new ArrayList<>();
        Iterator<VertexProperty<Object>> it = v.properties(key);
        while (it.hasNext()) {
          sourceProperties.add(it.next());
          }
        VertexProperty.Cardinality cardinality =
          v.graph().features().vertex().getCardinality(key);
        if (sourceProperties.size() > 1 && cardinality == VertexProperty.Cardinality.single) {
          cardinality = VertexProperty.Cardinality.list;
          }
        for (VertexProperty<Object> sourceProperty : sourceProperties) {
          VertexProperty<Object> clonedProperty =
            v1.property(cardinality, key, sourceProperty.value());
          for (String metaKey : sourceProperty.keys()) {
            clonedProperty.property(metaKey, sourceProperty.property(metaKey).value());
            }
          }
        }
      }
    List<int[]> frontier = explorationFrontiers.computeIfAbsent(id, key -> new ArrayList<>());
    for (int[] explored : frontier) {
      if (explored[0] >= depthIn && explored[1] >= depthOut) {
        return v1;
        }
      }
    final int requestedIn = depthIn;
    final int requestedOut = depthOut;
    frontier.removeIf(explored -> explored[0] <= requestedIn && explored[1] <= requestedOut);
    frontier.add(new int[] {depthIn, depthOut});
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
        ve1 = gimme(ve, g1, depthIn - 1, inclCycles ? depthOut : 0,
                    inclCycles, onlyLabels, replicatedVertices, replicatedEdges,
                    explorationFrontiers);
        if (ve1 != null && replicatedEdges.add(e.id())) {
          e1 = ve1.addEdge(e.label(), v1);
          e1.property("lbl", e.label());
          for (String key : e.keys()) {
            if (!key.equals("lbl")) {
              e1.property(key, e.property(key).value());
              }
            }
          }
        }
      }
    if (depthOut > 0) {
      edges = v.edges(Direction.OUT);
      while (edges.hasNext()) {
        e = edges.next();
        ve = e.inVertex();
        ve1 = gimme(ve, g1, inclCycles ? depthIn : 0, depthOut - 1,
                    inclCycles, onlyLabels, replicatedVertices, replicatedEdges,
                    explorationFrontiers);
        if (ve1 != null && replicatedEdges.add(e.id())) {
          e1 = v1.addEdge(e.label(), ve1);
          e1.property("lbl", e.label());
          for (String key : e.keys()) {
            if (!key.equals("lbl")) {
              e1.property(key, e.property(key).value());
              }
            }
          }
        }
      }
    return v1;
    }
    
  private GraphTraversalSource _g;
    
  private ModifyingGremlinClient _client;

  private ThreadLocal<Boolean> _found = ThreadLocal.withInitial(() -> false);

  /** Logging . */
  private static Logger log = LogManager.getLogger(GremlinRecipies.class);

  }
