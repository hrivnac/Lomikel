package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.SmallHttpClient;
import com.Lomikel.Utils.NotifierURL;
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
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.has;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.not;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.out;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.repeat;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.values;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.count;
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

// org.json
import org.json.JSONArray;
import org.json.JSONObject;

// Java Mail
import javax.mail.MessagingException;

// Java
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;
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
    
  /** Execute full chain of new sources correlations analyses.
    * @param hbaseUrl   The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @param hbaseLimit The maximal number of alerts getting from HBase.
    * @param timeLimit  How far into the past the search should search (in minutes).
    * @param columns    HBase columns to be copied into graph alerts. May be <tt>null</tt>.
    * @throws LomikelException If anhything fails. */
  public void processSourcesOfInterest(String hbaseUrl,
                                       int    hbaseLimit,
                                       int    timeLimit,
                                       String columns) throws LomikelException {
    fillSourcesOfInterest(hbaseUrl, hbaseLimit, timeLimit, columns);
    g().E().has("lbl", "overlaps").drop().iterate();
    g().V().has("lbl", "AlertsOfInterest").not(has("alertType")).drop().iterate();
    g().V().has("lbl", "SourcesOfInterest").not(has("sourceType")).drop().iterate();
    generateSourcesOfInterestCorrelations();
    generateAlertsOfInterestCorrelations();
    Set stat = g().V().group().by(values("lbl")).by(count()).toSet();
    stat.addAll(g().E().group().by(values("lbl")).by(count()).toSet());
    NotifierURL.notify(stat.toString());
    }
    
  /** Fill graph with <em>SourcesOfInterest</em> and expand them to alerts.
    * @param hbaseUrl   The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @param hbaseLimit The maximal number of alerts getting from HBase.
    * @param timeLimit  How far into the past the search should search (in minutes).
    * @param columns    HBase columns to be copied into graph alerts. May be <tt>null</tt>.
    * @throws LomikelException If anhything fails. */
  public void fillSourcesOfInterest(String hbaseUrl,
                                    int    hbaseLimit,
                                    int    timeLimit,
                                    String columns) throws LomikelException {
    log.info("Filling SourcesOfInterest from " + hbaseUrl + ", hbaseLimit = " + hbaseLimit + ", timeLimit = " + timeLimit);
    log.info("\tenhancing with " + columns);
    SmallHttpClient httpClient = new SmallHttpClient();
    fhclient(hbaseUrl);
    fhclient().setLimit(hbaseLimit);
    Set<String> results = fhclient().latests("i:objectId",
                                             null,
                                             timeLimit,
                                             true);
    String request;
    String answer;
    JSONArray ja;
    JSONObject jo;
    Map<String, Set<Double>> classes; // class/sourceType -> [jd]
    String cl;
    double jd;
    Set<Double> jds;
    String key;
    Set<Double> val;
    int weight;
    // loop over all sources
    for (String oid : results) {
      request = new JSONObject().put("objectId", oid).put("output-format", "json").toString();
      try {
        answer = httpClient.postJSON(FINK_OBJECTS_WS,
                                     request,
                                     null,
                                     null);
        ja = new JSONArray(answer);
        classes =  new TreeMap<>();
        // get all alerts (jd) and their classes
        for (int i = 0; i < ja.length(); i++) {
          jo = ja.getJSONObject(i);
          cl = jo.getString("v:classification");
          jd = jo.getDouble("i:jd");
          if (!cl.equals("Unknown")) {
            if (classes.containsKey(cl)) {
              jds = classes.get(cl);
              jds.add(jd);
              }
            else {
              jds = new TreeSet<Double>();
              jds.add(jd);
              classes.put(cl, jds);
              }
            }
          }
        log.info(oid + ":");
        for (Map.Entry<String, Set<Double>> cls : classes.entrySet()) {
          key = cls.getKey();
          val = cls.getValue();
          weight = val.size();
          log.info("\t" + key + " in " + weight + " alerts");
          registerSourcesOfInterest(key, oid, weight, val, hbaseUrl, true, columns);
          }
        }
      catch (LomikelException e) {
        log.error("Cannot get classification for " + oid);
        }
      }
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
    *                   as <tt>ip:port:table:schema</tt>. 
    * @param enhance    Whether expand tree under all <em>SourcesOfInterest</em> with alerts
    *                   filled with requested HBase columns.
    * @param columns    The HBase columns to be filled into alerts. May be <tt>null</tt>.
    *                   Ignored if enhancement not requested. */
  public void registerSourcesOfInterest(String      sourceType,
                                        String      objectId,
                                        double      weight,
                                        String      instancesS,
                                        String      hbaseUrl,
                                        boolean     enhance,
                                        String      columns) {   
    Set<Double> instances = new HashSet<>();
    for (String instance : instancesS.replaceAll("\\[", "").replaceAll("]", "").split(",")) {
      instances.add(Double.parseDouble(instance));
      }
    registerSourcesOfInterest(sourceType, objectId, weight, instances, hbaseUrl, enhance, columns);
    }
    
  /** Register  <em>source</em> in <em>SourcesOfInterest</em>.
    * @param sourceType The type of <em>SourcesOfInterest</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param objectId   The objectId of the new <em>Source</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param weight     The weight of the connection.
    *                   Usualy the number of <em>Alerts</em> of this type. 
    * @param instances  The <em>jd</em> of related <em>Alerts</em>.
    * @param hbaseUrl   The url of the HBase carrying full <em>Alert</em> data
    *                   as <tt>ip:port:table:schema</tt>.
    * @param enhance    Whether expand tree under all <em>SourcesOfInterest</em> with alerts
    *                   filled with requested HBase columns.
    * @param columns The HBase columns to be filled into alerts. May be <tt>null</tt>.
    *                   Ignored if enhancement not requested. */
  public void registerSourcesOfInterest(String      sourceType,
                                        String      objectId,
                                        double      weight,
                                        Set<Double> instances,
                                        String      hbaseUrl,
                                        boolean     enhance,
                                        String      columns) {   
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
    addEdge(g().V(soi).next(),
            g().V(s).next(),
            "contains",
            new String[]{"weight",    "instances"                                                     },
            new String[]{"" + weight, instances.toString().replaceFirst("\\[", "").replaceAll("]", "")});
    if (enhance) {
      try {
        fhclient(hbaseUrl);
        enhanceSource(s, instances.toString().replaceFirst("\\[", "").replaceAll("]", "").split(","), columns);
        }
      catch (LomikelException e) {
        log.error("Cannot enhance source", e);
        }
      }
    commit(); // not needed if enhancing
    }
        
  /** Expand tree under all <em>SourcesOfInterest</em> with alerts
    * filled with requested HBase columns.
    * @param columns The HBase columns to be filled into alerts. May be <tt>null</tt>.
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
    * @param columns    The HBase columns to be filled into alerts. May be <tt>null</tt>.
    * @throws LomikelException If anything goes wrong. */
  public void enhanceSourcesOfInterest(String sourceType,
                                       String columns) throws LomikelException {
    log.info("Expanding " + sourceType + " SourcesOfInterest and enhancing them with " + columns);
    Vertex soi = g().V().has("lbl",        "SourcesOfInterest").
                         has("sourceType", sourceType).
                         next();
    fhclient(soi.property("url").value().toString());
    Iterator<Edge> containsIt = soi.edges(Direction.OUT);
    Edge contains;
    Vertex source;
    String objectId;
    String[] jds;
    while (containsIt.hasNext()) {
      contains = containsIt.next();
      source = contains.inVertex();
      objectId = source.property("objectId").value().toString();
      jds = contains.property("instances").value().toString().replaceFirst("\\[", "").replaceAll("]", "").split(",");
      enhanceSource(source, jds, columns);
      }
    }
 
  /** Expand tree under <em>SourcesOfInterest</em> with alerts
    * filled with requested HBase columns. It also assembles
    * related AlertsOfInterest.
    * @param sourceType The type of <em>SourcesOfInterest</em>.
    * @param jds        The <em>jd</em> of related <em>Alerts</em>.
    * @param columns    The HBase columns to be filled into alerts. May be <tt>null</tt>.
    * @throws LomikelException If anything goes wrong. */
  public void enhanceSource(Vertex   source,
                            String[] jds,
                            String   columns) throws LomikelException {
    String objectId = source.property("objectId").value().toString();
    int n = 0;
    String key;
    Vertex alert;
    List<Map<String, String>> results;
    for (String jd : jds) {
      n++;
      key = objectId + "_" + jd.trim();
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
      if (columns != null) {
        results = fhclient().results2List(fhclient().scan(key,
                                                          null,
                                                          columns,
                                                          0,
                                                          false,
                                                          false));        
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
        }
      addEdge(source, alert, "has");
      assembleAlertsOfInterest(alert);
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
        if (c12 > 0) {
          corr.put(Pair.of(soi1, soi2), c12);
          }
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
          if (corr.containsKey(Pair.of(soi1, soi2))) {
            addEdge(g().V().has("lbl", "SourcesOfInterest").has("sourceType", soi1).next(),
                    g().V().has("lbl", "SourcesOfInterest").has("sourceType", soi2).next(),
                    "overlaps",
                    new String[]{"intersection",                "sizeIn",            "sizeOut"          },
                    new Double[]{corr.get(Pair.of(soi1, soi2)), sizeInOut.get(soi1), sizeInOut.get(soi2)});
            }
          }
        }
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    }
    
  /** Assemble AlertsOfInterest from all existing alerts. */
  public void assembleAlertsOfInterest() {
    log.info("Assembling AlertsOfInterest");
    GraphTraversal<Vertex, Vertex> alertT = g().V().has("lbl", "alert");
    Vertex alert;
    while (alertT.hasNext()) {
      alert = alertT.next();
      assembleAlertsOfInterest(alert);
      }
    }
    
  /** Assemble AlertsOfInterest from an alert.
    * @param alert The existing alert.*/
  public void assembleAlertsOfInterest(Vertex alert) {
    String jd = alert.property("jd").value().toString();
    Vertex source = alert.edges(Direction.IN).next().outVertex();
    String objectId = source.property("objectId").value().toString();
    Iterator<Edge> containsIt =  source.edges(Direction.IN);
    String sourceType = null;
    Edge contains;
    String instances = "";
    String hbaseUrl = "";
    String key;
    Vertex aoi;
    while (containsIt.hasNext()) {
      contains = containsIt.next();
      instances = contains.property("instances").value().toString();
      if (instances.contains(jd)) { // just one SourceOfInterest contains each alert
        sourceType = contains.outVertex().property("sourceType").value().toString();
        hbaseUrl   = contains.outVertex().property("url").value().toString();
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
      addEdge(g().V(aoi).next(),
              g().V(alert).next(),
              "contains",
              new String[]{"weight", "instances"},
              new String[]{"1",      instances  });
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    }
    
  /** Generate <em>overlaps</em> Edges between <em>AlertsOfInterest</em> and <em>SourcesOfInterest</em>.*/
  public void generateAlertsOfInterestCorrelations() {
    log.info("Generating correlations for Alerts of Interest");
    GraphTraversal<Vertex, Vertex> aoiT = g().V().has("lbl", "AlertsOfInterest");
    Map<Pair<String, String>, Integer> weights = new HashMap<>();
    Map<String, Integer>               sizesA  = new HashMap<>();
    Map<String, Integer>               sizesS  = new HashMap<>();
    Set<String>                        types   = new HashSet<>();
    Vertex aoi;
    Iterator<Edge> containsAIt;
    Iterator<Edge> containsSIt;
    Edge containsA;
    Edge containsS;
    Vertex alert;
    Vertex soi;
    String alertType;
    String sourceType;
    Pair rel;
    int weight;
    while (aoiT.hasNext()) { // AlertsOfInterest
      aoi = aoiT.next();
      alertType = aoi.property("alertType").value().toString();
      types.add(alertType);
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
            types.add(sourceType);
            rel = Pair.of(alertType, sourceType);
            if (!weights.containsKey(rel)) {
              weights.put(rel, 1);
              }
            weight = weights.get(rel);
            weights.put(rel, weight + 1);
            }
          }
        }
      }
    int sz;
    for (String a : types) {
      sz = 0;
      for (String s : types) {
        sz += weights.containsKey(Pair.of(a, s)) ? weights.get(Pair.of(a, s)) : 0;
        }
      sizesA.put(a, sz);
      }
    for (String s : types) {
      sz = 0;
      for (String a : types) {
        sz += weights.containsKey(Pair.of(a, s)) ? weights.get(Pair.of(a, s)) : 0;
        }
      sizesS.put(s, sz);
      }
    GraphTraversal<Vertex, Vertex> alertT;
    GraphTraversal<Vertex, Vertex> sourceT;
    for (String a : types) {
      for (String s : types) {
        alertT  = g().V().has("lbl", "AlertsOfInterest" ).has("alertType",  a);
        sourceT = g().V().has("lbl", "SourcesOfInterest").has("sourceType", s);
        if (alertT.hasNext() && sourceT.hasNext() && weights.containsKey(Pair.of(a, s))) {
          addEdge(alertT.next(),
                  sourceT.next(),
                  "overlaps",
                  new String[]{"intersection",
                               "sizeIn",
                               "sizeOut"            },
                  new Double[]{(double)weights.get(Pair.of(a, s)),
                               (double)sizesA.get(a),
                               (double)sizesS.get(s)});
          }
        }
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    }
    
  /** Create a new {@link FinkHBaseClient}. Singleton when url unchanged.
    * @param hbaseUrl The HBase url as <tt>ip:port:table:schema</tt>.
    * @return          The corresponding {@link FinkHBaseClient}, created and initialised if needed.
    * @throws LomikelException If cannot be created. */
  private FinkHBaseClient fhclient(String hbaseUrl) throws LomikelException {
    if (hbaseUrl == null || hbaseUrl.equals(_fhclientUrl)) {
      return _fhclient;
      }
    _fhclientUrl = hbaseUrl;
    String[] url = hbaseUrl.split(":");
    String ip     = url[0];
    String port   = url[1];
    String table  = url[2];
    String schema = url[3];
    _fhclient = new FinkHBaseClient(ip, port);
    _fhclient.connect(table, schema);
    return _fhclient;
    }
    
  /** Get existing {@link FinkHBaseClient}.
    * @return The corresponding {@link FinkHBaseClient}.
    * @throws LomikelException If not yet created. */
  private FinkHBaseClient fhclient() throws LomikelException {
    if (_fhclient == null) {
      throw new LomikelException("FinkHBaseClient not initialised");
      }
    return _fhclient;
    }
    
  private FinkHBaseClient _fhclient;
  
  private String _fhclientUrl;
  
  private static String FINK_OBJECTS_WS = "https://fink-portal.org/api/v1/objects";

  /** Logging . */
  private static Logger log = Logger.getLogger(FinkGremlinRecipies.class);

  }
