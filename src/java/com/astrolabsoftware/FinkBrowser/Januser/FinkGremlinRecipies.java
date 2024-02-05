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
    
  /** Expand tree under <em>SourcesOfInterest</em> with alerts
    * filles with requested HBase columns.
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
              alert.property(entry.getKey().split(":")[1], entry.getValue());
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
    * Edges.
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
    
  /** Generate <em>overlaps</em> Edges between <em>SourcesOfInterest</em> Vertices.
    * @param useWeight Whether to use <em>contains</em> property <em>weight</em> to weight <em>source</em> contribution
    *                  to overlap. */
  public void generateSourcesOfInterestCorrelations(boolean useWeight) {
    log.info("Generating correlations for Sources of Interest " + (useWeight ? "" : "not ") + "using weights");
    g().V().has("lbl", "SourcesOfInterest").bothE("overlaps").drop().iterate();
    GraphTraversal<Vertex, Vertex> soiT = g().V().has("lbl", "SourcesOfInterest");
    Map<Pair<String, String>, Set<String>> instances = new HashMap<>();
    Set<String>                            sources   = new HashSet<>();
    Set<String>                            objectIds = new HashSet<>();
    Vertex soi;
    String sourceType;
    Iterator<Edge> containsIt;
    Edge contains;
    Set<String> alerts;
    Vertex source;
    String objectId;
    while (soiT.hasNext()) {
      soi = soiT.next();
      sourceType = soi.property("sourceType").value().toString();
      sources.add(sourceType);
      containsIt = soi.edges(Direction.OUT);
      while (containsIt.hasNext()) {
        contains = containsIt.next();
        alerts = new HashSet<String>();
        for (String instance : contains.property("instances").value().toString().split(",")) {
          alerts.add(instance.trim());
          }
        source = contains.inVertex();
        objectId = source.property("objectId").value().toString();
        objectIds.add(objectId);
        instances.put(Pair.of(sourceType, objectId), alerts);
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
          if (instances.containsKey(Pair.of(soi1, oid)) &&
              instances.containsKey(Pair.of(soi2, oid))) {
            if (useWeight) {
              alerts1 = instances.get(Pair.of(soi1, oid));
              alerts2 = instances.get(Pair.of(soi2, oid));
              for (String instance : alerts1) {
                if (alerts2.contains(instance)) {
                  c12++;
                  }
                }
              }
            else {
              c12++;
              }
            }
          }
        corr.put(Pair.of(soi1, soi2), c12);
        }
      }
    //double s1;
    //for (String soi1 : sources) {
    //  s1 = 0;
    //  for (String soi2 : sources) {
    //    s1 += corr.get(Pair.of(soi1, soi2));
    //    }
    //  sizeInOut.put(soi1, s1);
    //  }
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
                  //new Double[]{corr.get(Pair.of(soi1, soi2)), sizeInOut.get(soi1), sizeInOut.get(soi2)});
                  new Double[]{corr.get(Pair.of(soi1, soi2)), corr.get(Pair.of(soi1, soi1)), corr.get(Pair.of(soi2, soi2)),});
          }
        }
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    }

  /** Logging . */
  private static Logger log = Logger.getLogger(FinkGremlinRecipies.class);

  }
