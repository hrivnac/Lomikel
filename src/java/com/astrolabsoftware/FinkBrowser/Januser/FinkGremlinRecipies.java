package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.Januser.GremlinRecipies;
import com.Lomikel.Januser.ModifyingGremlinClient;
import com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.V;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.fold;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.out;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.repeat;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.addV;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;

// Janus Graph
import org.janusgraph.core.SchemaViolationException;
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
import java.util.Iterator;
import java.util.Optional;

// Log4J
import org.apache.log4j.Logger;

/** <code>FinkGremlinRecipies</code> provides various recipies to handle
  * and modify Gramlin Graphs fir Fink.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: check precodition for methods, wgich doesn't work with 'client' creation
public class FinkGremlinRecipies extends GremlinRecipies {
    
  /** Create and attach to {@link GraphTraversalSource}.
    * @param g The attached {@link GraphTraversalSource}. */
  public FinkGremlinRecipies(GraphTraversalSource g) {
    super(g);
    }
    
  /** Create and attach to {@link ModifyingGremlinClient}.
    * @param client The attached  {@link ModifyingGremlinClient}. */
  public FinkGremlinRecipies(ModifyingGremlinClient client) {
    super(client);
    }
    
  /** Register  <em>source</em> in <em>SourcesOfInterest</em>.
    * @param sourceType The type of <em>SourcesOfInterest</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param objectId   The objectId of the new <em>Source</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param weight     The weight of the connection.
    *                   Usualy the number of <em>Alerts</em> of this type. 
    * @param instanceS  The <em>jd</em> of related <em>Alerts</em> as strings separated by comma.
    *                   Potential square brackets are removed.
    * @param hbaseUrl   The url of the HBase carrying full <em>Alert</em> data
    *                   as <tt>ip:port:table:schema</tt>. */
  public void registerSourcesOfInterest(String      sourceType,
                                        String      objectId,
                                        double      weight,
                                        String      instancesS,
                                        String      hbaseUrl) {   
    Set<Double> instances = new HashSet<>();
    for (String instance : instancesS.replaceAll("\\[", "").replaceAll("]", "").split(",")) {
      instances.add(Double.parseDouble(instance));
      }
    registerSourcesOfInterest(sourceType, objectId, weight, instances, hbaseUrl);
    }
    
  /** Register  <em>source</em> in <em>SourcesOfInterest</em>.
    * @param sourceType The type of <em>SourcesOfInterest</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param objectId   The objectId of the new <em>Source</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param weight     The weight of the connection.
    *                   Usualy the number of <em>Alerts</em> of this type. 
    * @param instance   The <em>jd</em> of related <em>Alerts</em>.
    * @param hbaseUrl   The url of the HBase carrying full <em>Alert</em> data
    *                   as <tt>ip:port:table:schema</tt>. */
  public void registerSourcesOfInterest(String      sourceType,
                                        String      objectId,
                                        double      weight,
                                        Set<Double> instances,
                                        String      hbaseUrl) {   
    log.info("Registering " + objectId + " as " + sourceType);
    Vertex soi = g().V().has("SourcesOfInterest", "lbl", "SourcesOfInterest").
                         has("sourceType", sourceType).
                         fold().
                         coalesce(unfold(), 
                                  addV("SourcesOfInterest").
                                  property("lbl",        "SourcesOfInterest").
                                  property("sourceType", sourceType         ).
                                  property("technology", "HBase"            ).
                                  property("url",        hbaseUrl           )).
                         next();
    Vertex s = g().V().has("source", "lbl", "source").
                       has("objectId", objectId).
                       fold().
                       coalesce(unfold(), 
                                addV("source").
                                property("lbl",      "source").
                                property("objectId", objectId)).
                       next();
    g().V(soi).addE("contains").
               to(V(s)).
               property("lbl",       "contains").
               property("weight",    weight    ).
               property("instances", instances ).
               iterate();
    commit();
    }
    
        
  /** Expand tree under all <em>SourcesOfInterest</em> with alerts
    * filled with requested HBase columns.
    * @param columns    The HBase columns to be filled inti alerts.
    * @throws LomikelException If anything goes wrong. */
  public void enhanceSourcesOfInterest(String columns) throws LomikelException {
    log.info("Expanding all SourcesOfInterest and enhancing them with " + columns);
    for (Object soi : g().V().has("lbl", "SourcesOfInterest").values("sourceType").toSet()) {
      enhanceSourcesOfInterest(soi.toString().trim(), columns);
      }
    }

  /** Expand tree under <em>SourcesOfInterest</em> with alerts
    * filled with requested HBase columns.
    * @param sourceType The type of <em>SourcesOfInterest</em>.
    * @param columns    The HBase columns to be filled inti alerts.
    * @throws LomikelException If anything goes wrong. */
  public void enhanceSourcesOfInterest(String sourceType,
                                       String columns) throws LomikelException {
    log.info("Expanding " + sourceType + " SourcesOfInterest and enhancing them with " + columns);
    Vertex soi = g().V().has("lbl",        "SourcesOfInterest").
                         has("sourceType", sourceType).
                         next();
    String[] url = soi.property("url").value().toString().split(":");
    String ip     = url[0];
    String port   = url[1];
    String table  = url[2];
    String schema = url[3];
    FinkHBaseClient client = new FinkHBaseClient(ip, port);
    client.connect(table, schema);
    Iterator<Edge> containsIt = soi.edges(Direction.OUT);
    Edge contains;
    Vertex source;
    Vertex alert;
    String objectId;
    double weight;
    String[] jds;
    String key;
    List<Map<String, String>> results;
    int n = 0;
    while (containsIt.hasNext()) {
      contains = containsIt.next();
      source = contains.inVertex();
      objectId = source.property("objectId").value().toString();
      weight = (Double)(contains.property("weight").value());
      jds = contains.property("instances").value().toString().replaceFirst("\\[", "").replaceAll("]", "").split(",");
      for (String jd : jds) {
        n++;
        key = objectId + "_" + jd.trim();
        results = client.results2List(client.scan(key,
                                                  null,
                                                  columns,
                                                  0,
                                                  false,
                                                  false));        
        alert = g().V().has("alert", "lbl", "alert").
                        has("objectId", objectId).
                        has("jd",       jd).
                        fold().
                        coalesce(unfold(), 
                                 addV("alert").
                                 property("lbl",     "alert"  ).
                                 property("objectId", objectId).
                                 property("jd",       jd      )).
                        next();
        for (Map<String, String> result : results) {
          for (Map.Entry<String, String> entry : result.entrySet()) {
            if (!entry.getKey().split(":")[0].equals("key")) {
              try {
                alert.property(entry.getKey().split(":")[1], entry.getValue());
                }
              catch (SchemaViolationException e) {
                log.error("Cannot enhance " + objectId + "_" + jd + ": " + entry.getKey() + " => " + entry.getValue() + "\n\t" + e.getMessage());
                }
              }
            }
          }
        addEdge(source, alert, "has");
        }
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    log.info("" + n + " alerts added");
    }
    
  /** Clean tree under <em>SourcesOfInterest</em>.
    * Drop alerts. Alerts are dropped even if they have other
    * {@link Edge}s.
    * @param sourceType The type of <em>SourcesOfInterest</em>.
    * @throws LomikelException If anything goes wrong. */
  public void cleanSourcesOfInterest(String sourceType) throws LomikelException {
    log.info("Cleaning " + sourceType + " SourcesOfInterest");
    g().V().has("lbl",        "SourcesOfInterest").
            has("sourceType", sourceType).
            out().
            out().
            drop().
            iterate();
    g().getGraph().tx().commit(); // TBD: should use just commit()
    }
    
  /** Generate <em>overlaps</em> Edges between <em>SourcesOfInterest</em> Vertices.*/
  public void generateSourcesOfInterestCorrelations() {
    log.info("Generating correlations for Sources of Interest");
    g().V().has("lbl", "SourcesOfInterest").bothE("overlaps").drop().iterate();
    GraphTraversal<Vertex, Vertex> soiT = g().V().has("lbl", "SourcesOfInterest");
    Map<Pair<String, String>, Double>      weights   = new HashMap<>();
    Set<String>                            sources   = new HashSet<>();
    Set<String>                            objectIds = new HashSet<>();
    Vertex soi;
    String sourceType;
    Iterator<Edge> containsIt;
    Edge contains;
    double weight;
    Vertex source;
    String objectId;
    while (soiT.hasNext()) {
      soi = soiT.next();
      sourceType = soi.property("sourceType").value().toString();
      sources.add(sourceType);
      containsIt = soi.edges(Direction.OUT);
      while (containsIt.hasNext()) {
        contains = containsIt.next();
        weight = (Double)(contains.property("weight").value());
        source = contains.inVertex();
        objectId = source.property("objectId").value().toString();
        objectIds.add(objectId);
        weights.put(Pair.of(sourceType, objectId), weight);
        }
      }
    Map<Pair<String, String>, Double> corr      = new HashMap<>();
    Map<String, Double>               sizeInOut = new HashMap<>();
    double c12;
    Set<String> alerts1;
    Set<String> alerts2;
    for (String soi1 : sources) {
      for (String soi2 : sources) {
        c12 = 0;
        for (String oid : objectIds) {
          if (weights.containsKey(Pair.of(soi1, oid)) &&
              weights.containsKey(Pair.of(soi2, oid))) {
            c12++;
            }
          }
        corr.put(Pair.of(soi1, soi2), c12);
        }
      }
    for (String soi1 : sources) {
      c12 = 0;
      for (String oid : objectIds) {
        if (weights.containsKey(Pair.of(soi1, oid))) {
          c12++;
          }
        }
      sizeInOut.put(soi1, c12);
      }
    int i1 = 0;
    int i2 = 0;
    for (String soi1 : sources) {
      i1++;
      i2 = 0;
      for (String soi2 : sources) {
        i2++;
        if (i2 < i1) {
          addEdge(g().V().has("lbl", "SourcesOfInterest").has("sourceType", soi1).next(),
                  g().V().has("lbl", "SourcesOfInterest").has("sourceType", soi2).next(),
                  "overlaps",
                  new String[]{"intersection",                "sizeIn",            "sizeOut"          },
                  new Double[]{corr.get(Pair.of(soi1, soi2)), sizeInOut.get(soi1), sizeInOut.get(soi2)});
          }
        }
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    }
    
  /** TBD */
  public void assembleAlertsOfInterest() {
    log.info("Assembling AlertsOfInterest");
    GraphTraversal<Vertex, Vertex> alertT = g().V().has("lbl", "alert");
    Vertex alert;
    String jd;
    Vertex source;
    String objectId;
    Iterator<Edge> containsIt;
    Edge contains;
    String instances;
    String sourceType;
    String hbaseUrl = "";
    String key;
    Vertex aoi;
    while (alertT.hasNext()) {
      alert = alertT.next();
      jd = alert.property("jd").value().toString();
      source = alert.edges(Direction.IN).next().outVertex();
      objectId = source.property("objectId").value().toString();
      containsIt =  source.edges(Direction.IN);
      sourceType = null;
      while (containsIt.hasNext()) {
        contains = containsIt.next();
        instances = contains.property("instances").value().toString();
        if (instances.contains(jd)) { // just one SourceOfInterest contains each alert
          sourceType = contains.outVertex().property("sourceType").value().toString();
          hbaseUrl   = contains.outVertex().property("url").value().toString();
          }
        }
      key = objectId + "_" + jd;
      aoi = g().V().has("AlertsOfInterest", "lbl", "AlertsOfInterest").
                           has("alertType", sourceType).
                           fold().
                           coalesce(unfold(), 
                                    addV("AlertsOfInterest").
                                    property("lbl",        "AlertsOfInterest").
                                    property("alertType",  sourceType        ).
                                    property("technology", "HBase"           ).
                                    property("url",        hbaseUrl          )).
                           next();
      g().V(aoi).addE("contains").
                 to(V(alert)).
                 property("lbl",       "contains").
                 property("weight",    1         ).
                 iterate();
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    }
    
  /** Generate <em>overlaps</em> Edges between <em>SourcesOfInterest</em> Vertices.*/
  public void generateAlertsOfInterestCorrelations() {
    log.info("Generating correlations for Alerts of Interest");
    g().V().has("lbl", "AlertsOfInterest").bothE("overlaps").drop().iterate();
    GraphTraversal<Vertex, Vertex> aoiT = g().V().has("lbl", "AlertsOfInterest");
    Map<Pair<String, String>, Integer> weights   = new HashMap<>();
    Map<String, Integer>               sizes     = new HashMap<>();
    Set<String>                        sources   = new HashSet<>();
    Vertex aoi;
    Iterator<Edge> containsAIt;
    Iterator<Edge> containsSIt;
    Edge containsA;
    Edge containsS;
    Vertex alert;
    Vertex soi;
    String alertType;
    String sourceType;
    Pair types;
    int weight;
    while (aoiT.hasNext()) { // AlertsOfInterest
      aoi = aoiT.next();
      alertType = aoi.property("alertType").value().toString();
      sources.add(alertType);
      containsAIt = aoi.edges(Direction.OUT);
      while (containsAIt.hasNext()) { // AlertsOfInterest.contains
        containsA = containsAIt.next();
        alert = containsA.inVertex();
        containsSIt = alert.edges(Direction.IN).  // has
                            next().               // (just one)
                            outVertex().          // source
                            edges(Direction.IN);  // contains
        while (containsSIt.hasNext()) {
          containsS = containsSIt.next();
          soi = containsS.outVertex();
          if (soi.property("lbl").value().toString().equals("SourcesOfInterest")) {
            sourceType = soi.property("sourceType").value().toString();
            sources.add(sourceType);
            types = Pair.of(alertType, sourceType);
            if (!weights.containsKey(types)) {
              weights.put(types, 1);
              }
            weight = weights.get(types);
            weights.put(types, weight + 1);
            }
          }
        }
      }
    int sz;
    for (String a : sources) {
      sz = 0;
      for (String s : sources) {
        sz += weights.containsKey(Pair.of(a, s)) ? weights.get(Pair.of(a, s)) : 0;
        }
      sizes.put(a, sz);
      }
    System.out.println(weights);
    System.out.println(sizes);
    }
    

  /** Logging . */
  private static Logger log = Logger.getLogger(FinkGremlinRecipies.class);

  }
