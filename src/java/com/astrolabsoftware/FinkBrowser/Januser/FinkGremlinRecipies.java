package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.Januser.GremlinRecipies;
import com.Lomikel.Januser.ModifyingGremlinClient;
import com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
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

  /** Logging . */
  private static Logger log = Logger.getLogger(FinkGremlinRecipies.class);

  }
