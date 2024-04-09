package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.SmallHttpClient;
import com.Lomikel.Utils.NotifierURL;
import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.Januser.GremlinRecipies;
import com.Lomikel.Januser.ModifyingGremlinClient;
import com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient;
import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;

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
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Optional;
import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;

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
    * @param nLimit The maximal number of alerts getting from HBase or Fink Portal.
    * @param timeLimit  How far into the past the search should search (in minutes).
    * @param clss       An array of <em>classes</em> taken from {@link FPC},
    *                   if contains <tt>Anomaly</tt>, get anomalies from {@link FPC},                  
    *                   if <tt>null</tt>, analyse <em>sources</em> from HBase database.
    * @param enhance    Whether expand tree under all <em>SourcesOfInterest</em> with alerts
    *                   possibly filled with requested HBase columns.
    * @param columns    HBase columns to be copied into graph alerts. May be <tt>null</tt>.
    * @throws LomikelException If anhything fails. */
  public void processSourcesOfInterest(String   hbaseUrl,
                                       int      nLimit,
                                       int      timeLimit,
                                       String[] clss,
                                       boolean  enhance,
                                       String   columns) throws LomikelException {
    fillSourcesOfInterest(hbaseUrl, nLimit, timeLimit, clss, enhance, columns);
    generateCorrelations();
    }
        
  /** Fill graph with <em>SourcesOfInterest</em> and expand them to alerts.
    * @param hbaseUrl   The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @param nLimit     The maximal number of alerts getting from HBase or Fink Portal.
    * @param timeLimit  How far into the past the search should search (in minutes).
    * @param clss       An array of <em>classes</em> taken from {@link FPC},
    *                   if contains <tt>Anomaly</tt>, get anomalies from {@link FPC},                  
    *                   if <tt>null</tt>, analyse <em>sources</em> from HBase database.
    * @param enhance    Whether expand tree under all <em>SourcesOfInterest</em> with alerts
    *                   possibly filled with requested HBase columns.
    * @param columns    HBase columns to be copied into graph alerts. May be <tt>null</tt>.
    * @throws LomikelException If anything fails. */
  public void fillSourcesOfInterest(String   hbaseUrl,
                                    int      nLimit,
                                    int      timeLimit,
                                    String[] clss,
                                    boolean  enhance,
                                    String   columns) throws LomikelException {
    if (clss == null) {
      log.info("Filling SourcesOfInterest from " + hbaseUrl + ", nLimit = " + nLimit + ", timeLimit = " + timeLimit);
      }
    else {
      log.info("Filling SourcesOfInterest of " + Arrays.toString(clss) + " from Fink Portal , nLimit = " + nLimit + ", timeLimit = " + timeLimit);
      }
    if (enhance) {
      log.info("\tenhancing with " + columns);
      }
    Set<String> oids = new HashSet<>();;
    if (clss == null) {
      log.info("*** " + hbaseUrl + ":");
      SmallHttpClient httpClient = new SmallHttpClient();
      fhclient(hbaseUrl);
      fhclient().setLimit(nLimit);
      oids = fhclient().latests("i:objectId",
                                null,
                                timeLimit,
                                true);
      }
    else {
      Calendar cal;
      Date d;
      String sd;
      JSONArray ja;
      JSONObject jo;
      for (String cls : clss) {
        log.info("*** " + cls + ":");
        cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -nLimit);
        d = cal.getTime();
        sd = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(d);
        if (cls.equals("*")) {
          ja = FPC.anomaly(new JSONObject().put("n",             nLimit).
                                            put("startdate",     sd).
                                            put("columns",       "i:objectId").
                                            put("output-format", "json"));
          }
        else {
          ja = FPC.latests(new JSONObject().put("n",             nLimit).
                                            put("class",         cls).
                                            put("startdate",     sd).
                                            put("columns",       "i:objectId").
                                            put("output-format", "json"));
          }
        for (int i = 0; i < ja.length(); i++) {
          jo = ja.getJSONObject(i);
          oids.add(jo.getString("i:objectId"));
          }
        }
      }
    int size = oids.size();
    int n = 0;
    long dt;
    double freq;
    long startTime = System.currentTimeMillis();
    JSONArray ja;
    JSONObject jo;
    Map<String, Set<Double>> classes; // cls -> [jd]
    String cl;
    double jd;
    Set<Double> jds;
    String key;
    Set<Double> val;
    int weight;
    // loop over all sources
    for (String oid : oids) {
      try {
        ja = FPC.objects(new JSONObject().put("objectId",      oid   ).
                                          put("output-format", "json"));
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
        n++;
        dt = (System.currentTimeMillis() - startTime) / 1000;
        freq = (double)n / (double)dt;
        log.info(oid + " (" + n + " of " + size + " with " + String.format("%.2f", freq) + " Hz):");
        for (Map.Entry<String, Set<Double>> cls : classes.entrySet()) {
          key = cls.getKey();
          val = cls.getValue();
          weight = val.size();
          log.info("\t" + key + " in " + weight + " alerts");
          registerSourcesOfInterest(key, oid, weight, val, hbaseUrl, enhance, columns);
          }
        }
      catch (LomikelException e) {
        log.error("Cannot get classification for " + oid);
        }
      }
    }
    
  /** Register  <em>source</em> in <em>SourcesOfInterest</em>.
    * @param cls        The type (class) of <em>SourcesOfInterest</em> {@link Vertex}.
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
    *                   possibly filled with requested HBase columns.
    * @param columns    The HBase columns to be filled into alerts. May be <tt>null</tt>.
    *                   Ignored if enhancement not requested. */
  public void registerSourcesOfInterest(String      cls,
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
    registerSourcesOfInterest(cls, objectId, weight, instances, hbaseUrl, enhance, columns);
    }
    
  /** Register <em>source</em> in <em>SourcesOfInterest</em>.
    * @param cls        The type (class) of <em>SourcesOfInterest</em> {@link Vertex}.
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
  public void registerSourcesOfInterest(String      cls,
                                        String      objectId,
                                        double      weight,
                                        Set<Double> instances,
                                        String      hbaseUrl,
                                        boolean     enhance,
                                        String      columns) {   
    log.info("\t\tregistering " + objectId + " as " + cls);
    Vertex soi = g().V().has("SourcesOfInterest", "lbl", "SourcesOfInterest").
                         has("cls", cls).
                         fold().
                         coalesce(unfold(), 
                                  addV("SourcesOfInterest").
                                  property("lbl",        "SourcesOfInterest").
                                  property("cls",        cls         ).
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
            "deepcontains",
            new String[]{"weight",    "instances"                                                     },
            new String[]{"" + weight, instances.toString().replaceFirst("\\[", "").replaceAll("]", "")},
            true);
    if (enhance) {
      try {
        fhclient(hbaseUrl);
        enhanceSource(s, instances.toString().replaceFirst("\\[", "").replaceAll("]", "").split(","), columns);
        }
      catch (LomikelException e) {
        log.error("Cannot enhance source", e);
        }
      }
    commit(); // TBD: not needed if enhancing
    }
        
  /** Expand tree under all <em>SourcesOfInterest</em> with alerts
    * filled with requested HBase columns.
    * @param columns The HBase columns to be filled into alerts. May be <tt>null</tt>.
    * @throws LomikelException If anything goes wrong. */
  public void enhanceSourcesOfInterest(String columns) throws LomikelException {
    log.info("Expanding all SourcesOfInterest and enhancing them with " + columns);
    for (Object soi : g().V().has("lbl", "SourcesOfInterest").values("cls").toSet()) {
      enhanceSourcesOfInterest(soi.toString().trim(), columns);
      }
    }

  /** Expand tree under <em>SourcesOfInterest</em> with alerts
    * filled with requested HBase columns.
    * @param cls     The type (class) of <em>SourcesOfInterest</em>.
    * @param columns The HBase columns to be filled into alerts. May be <tt>null</tt>.
    * @throws LomikelException If anything goes wrong. */
  public void enhanceSourcesOfInterest(String cls,
                                       String columns) throws LomikelException {
    log.info("Expanding " + cls + " SourcesOfInterest and enhancing them with " + columns);
    Vertex soi = g().V().has("lbl", "SourcesOfInterest").
                         has("cls", cls).
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
 
  /** Expand tree under <em>source</em> with alerts
    * filled with requested HBase columns. It also assembles
    * related AlertsOfInterest.
    * @param source     The source.
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
      addEdge(source, alert, "sends");
      assembleAlertsOfInterest(alert);
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    log.info("\t\t" + n + " alerts added");
    }
   
  /** Clean tree under <em>SourcesOfInterest</em>.
    * Drop alerts. Alerts are dropped even if they have other
    * {@link Edge}s.
    * @param cls The type (class) of <em>SourcesOfInterest</em>.
    * @throws LomikelException If anything goes wrong. */
  public void cleanSourcesOfInterest(String cls) throws LomikelException {
    log.info("Cleaning " + cls + " SourcesOfInterest");
    g().V().has("lbl", "SourcesOfInterest").
            has("cls", cls).
            out().
            out().
            drop().
            iterate();
    g().getGraph().tx().commit(); // TBD: should use just commit()
    }
    
  /** Generate <em>overlaps</em> Edges between <em>SourcesOfInterest</em> Vertices.*/
  public void generateSourcesOfInterestCorrelations() {
    log.info("Generating correlations for Sources of Interest");
    // Accumulating weights
    GraphTraversal<Vertex, Vertex> soiT = g().V().has("lbl", "SourcesOfInterest");
    Map<Pair<String, String>, Double>      weights   = new HashMap<>(); // cls, objectId -> weight
    Set<String>                            types     = new HashSet<>(); // [cls]
    Set<String>                            objectIds = new HashSet<>(); // [objectId]
    Vertex soi;
    String cls;
    Iterator<Edge> containsIt;
    Edge contains;
    double weight;
    Vertex source;
    String objectId;
    // loop over SoI
    while (soiT.hasNext()) {
      soi = soiT.next();
      cls = soi.property("cls").value().toString();
      types.add(cls);
      containsIt = soi.edges(Direction.OUT);
      // loop over contained sources
      while (containsIt.hasNext()) {
        contains = containsIt.next();
        source = contains.inVertex();
        if (source.label().equals("source")) {
          weight = (Double)(contains.property("weight").value());
          objectId = source.property("objectId").value().toString();
          objectIds.add(objectId);
          weights.put(Pair.of(cls, objectId), weight);
          }
        }
      }
    // Calculation correlations and sizes
    Map<Pair<String, String>, Double> corr      = new HashMap<>(); // cls, cls -> correlation
    Map<String, Double>               sizeInOut = new HashMap<>(); // cls -> size
    double c12;
    Set<String> alerts1;
    Set<String> alerts2;
    // double-loop over SoI 
    for (String soi1 : types) {
      for (String soi2 : types) {
        c12 = 0;
        // loop over all sources and add them into weights if contained in both SoI
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
    // loop over SoI 
    for (String soi1 : types) {
      c12 = 0;
      // loop over all sources and add them into size in SoI
      for (String oid : objectIds) {
        if (weights.containsKey(Pair.of(soi1, oid))) {
          c12++;
          }
        }
      sizeInOut.put(soi1, c12);
      }
    // Creating overlaps
    int i1 = 0;
    int i2 = 0;
    int n = 0;
    // double-loop over SoI and create overlaps Edge if non empty 
    for (String soi1 : types) {
      i1++;
      i2 = 0;
      for (String soi2 : types) {
        i2++;
        if (i2 < i1) {
          if (corr.containsKey(Pair.of(soi1, soi2))) {
            n++;
            addEdge(g().V().has("lbl", "SourcesOfInterest").has("cls", soi1).next(),
                    g().V().has("lbl", "SourcesOfInterest").has("cls", soi2).next(),
                    "overlaps",
                    new String[]{"intersection",                
                                 "sizeIn",            
                                 "sizeOut"},
                    new Double[]{corr.get(Pair.of(soi1, soi2)),
                                 sizeInOut.get(soi1),
                                 sizeInOut.get(soi2)},
                    true);
            }
          }
        }
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    log.info("" + n + " correlations generated");
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
    Vertex source = alert.edges(Direction.IN).next().outVertex(); // BUG: should check lbl == source
    String objectId = source.property("objectId").value().toString();
    Iterator<Edge> containsIt =  source.edges(Direction.IN); // BUG: should check lbl == SoI
    String cls = null;
    Edge contains;
    String instances = "";
    String hbaseUrl = "";
    String key;
    Vertex aoi;
    // loop over all Edges to SoI
    while (containsIt.hasNext()) {
      contains = containsIt.next();
      instances = contains.property("instances").value().toString();
      // BUG: jd should not be compared as strings
      // if alert jd presend in this Soi Edge => create AoI and connect it to alert
      if (instances.contains(jd)) { // just one SourceOfInterest contains each alert
        cls      = contains.outVertex().property("cls").value().toString();
        hbaseUrl = contains.outVertex().property("url").value().toString();
        key = objectId + "_" + jd;
        aoi = g().V().has("AlertsOfInterest", "lbl", "AlertsOfInterest").
                      has("cls", cls).
                      fold().
                      coalesce(unfold(), 
                               addV("AlertsOfInterest").
                               property("lbl",        "AlertsOfInterest").
                               property("cls",        cls               ).
                               property("technology", "HBase"           ).
                               property("url",        hbaseUrl          )).
                      next();
        addEdge(g().V(aoi).next(),
                g().V(alert).next(),
                "contains",
                new String[]{},
                new String[]{},
                true);
        }
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    }
    
  /** Generate <em>overlaps</em> Edges between <em>AlertsOfInterest</em> and <em>SourcesOfInterest</em>.*/
  public void generateCorrelations() {
    log.info("Generating correlations for Alerts of Interest");
    // Clean all correlations
    g().E().has("lbl", "overlaps").drop().iterate();
    g().V().has("lbl", "AlertsOfInterest").not(has("cls")).drop().iterate();
    g().V().has("lbl", "SourcesOfInterest").not(has("cls")).drop().iterate();
    // Accumulate correlations and sizes
    Map<String, Double>               weights = new HashMap<>(); // cls -> weight (for one source)
    Map<Pair<String, String>, Double> corrSS  = new HashMap<>(); // [cls1, cls2] -> weight (for all sources between SoI-SoI)
    Map<Pair<String, String>, Double> corrSA  = new HashMap<>(); // [cls1, cls2] -> weight (for all sources between SoI-AoI)
    SortedSet<String>                 types0  = new TreeSet<>(); // [cls] (for one source)
    SortedSet<String>                 types   = new TreeSet<>(); // [cls] (for all sources)
    GraphTraversal<Vertex, Vertex> sourceT = g().V().has("lbl", "source");
    Vertex source;
    Iterator<Edge> deepcontainsIt;
    Edge deepcontains;
    double weight;
    double weight1;
    double weight2;
    double cor;
    Vertex soi1;
    Vertex soi2;
    Vertex aoi2;
    String cls;
    Pair<String, String> rel;
    // loop over sources and accumulated weights to each source
    while (sourceT.hasNext()) {
      source = sourceT.next();
      deepcontainsIt = source.edges(Direction.IN);
      types0.clear();
      weights.clear(); 
      // get all weights to this source
      while (deepcontainsIt.hasNext()) {
        deepcontains = deepcontainsIt.next();
        weight = (Double)(deepcontains.property("weight").value());
        soi1 = deepcontains.outVertex();
        cls = soi1.property("cls").value().toString();
        types0.add(cls);
        types.add(cls);
        weights.put(cls, weight);
        }
      // double loop over accumulated weights and fill weights between SoIs
      for (String cls1 : types0) {
        weight1 = weights.get(cls1);
        for (String cls2 : types0) {
          weight2 = weights.get(cls2);
          rel = Pair.of(cls1, cls2);
          // SoI-SoI
          if (!corrSS.containsKey(rel)) {
            corrSS.put(rel, 1.0);
            }
          cor = corrSS.get(rel);
          corrSS.put(rel, cor + 1.0);
          // SoI-AoI
          if (!corrSA.containsKey(rel)) {
            corrSA.put(rel, weight2);
            }
          cor = corrSA.get(rel);
          corrSA.put(rel, cor + weight2);
          }
        }
      }
    // Creating overlaps
    String hbaseUrl = "";
    int nss = 0;
    int nsa = 0;
    // loop over SoI and create AoI
    for (String cls1 : types) {
      g().V().has("AlertsOfInterest", "lbl", "AlertsOfInterest").
                            has("cls", cls1).
                            fold().
                            coalesce(unfold(), 
                                     addV("AlertsOfInterest").
                                     property("lbl",        "AlertsOfInterest").
                                     property("cls",        cls1              ).
                                     property("technology", "HBase"           ).
                                     property("url",        hbaseUrl          )).
                            iterate();
      }
    // double-loop over SoI and create overlaps Edge SoI-SoI if non empty 
    for (String cls1 : types) {
      soi1 = g().V().has("lbl", "SourcesOfInterest").has("cls", cls1).next();
      for (String cls2 : types) {
        if (corrSS.containsKey(Pair.of(cls1, cls2))) {
          nss++;
          soi2 = g().V().has("lbl", "SourcesOfInterest").has("cls", cls2).next();
          hbaseUrl = soi2.property("url").value().toString();
          addEdge(g().V(soi1).next(),
                  g().V(soi2).next(),
                  "overlaps",
                  new String[]{"intersection",                
                               "sizeIn",            
                               "sizeOut"},
                  new Double[]{corrSS.get(Pair.of(cls1, cls2)),
                               corrSS.get(Pair.of(cls1, cls1)),
                               corrSS.get(Pair.of(cls2, cls2))},
                  true);
          }  
        }
      }
    // double-loop over SoI and create overlaps Edge SoI-AoI if non empty 
    for (String cls1 : types) {
      soi1 = g().V().has("lbl", "SourcesOfInterest").has("cls", cls1).next();
      for (String cls2 : types) {
        if (corrSA.containsKey(Pair.of(cls1, cls2))) {
          nsa++;
          soi2 = g().V().has("lbl", "SourcesOfInterest").has("cls", cls2).next();
          hbaseUrl = soi2.property("url").value().toString();
          aoi2 = g().V().has("AlertsOfInterest", "lbl", "AlertsOfInterest").
                         has("cls", cls2).
                         fold().
                         coalesce(unfold(), 
                                  addV("AlertsOfInterest").
                                  property("lbl",        "AlertsOfInterest").
                                  property("cls",        cls2              ).
                                  property("technology", "HBase"           ).
                                  property("url",        hbaseUrl          )).
                         next();
          addEdge(g().V(soi1).next(),
                  g().V(aoi2).next(),
                  "overlaps",
                  new String[]{"intersection",                
                               "sizeIn",            
                               "sizeOut"},
                  new Double[]{corrSA.get(Pair.of(cls1, cls2)),
                               corrSA.get(Pair.of(cls1, cls1)),
                               corrSA.get(Pair.of(cls2, cls2))},
                  true);
          }  
        }
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    log.info("" + nss + ", " + nsa + " source-source and source-alert correlations generated");
    }
    
  /** Generate <em>overlaps</em> Edges between <em>AlertsOfInterest</em> and <em>SourcesOfInterest</em>.*/
  public void generateAlertsOfInterestCorrelations() {
    log.info("Generating correlations for Alerts of Interest");
    GraphTraversal<Vertex, Vertex> aoiT = g().V().has("lbl", "AlertsOfInterest");
    Map<Pair<String, String>, Integer> weights = new HashMap<>(); // cls, objectId -> weight
    Map<String, Integer>               sizesA  = new HashMap<>(); // cls -> size (of alerts)
    Map<String, Integer>               sizesS  = new HashMap<>(); // cls -> size (of sources = sum of alerts)
    Set<String>                        types   = new HashSet<>(); // [cls]
    Vertex aoi;
    Iterator<Edge> containsAIt;
    Iterator<Edge> containsSIt;
    Edge containsA;
    Edge containsS;
    Vertex alert;
    Vertex soi;
    String clsA;
    String clsS;
    Pair rel;
    int weight;
    // loop over AoI
    while (aoiT.hasNext()) {
      aoi = aoiT.next();
      clsA = aoi.property("cls").value().toString();
      types.add(clsA);
      containsAIt = aoi.edges(Direction.OUT);
      // loop over alerts in AoI
      while (containsAIt.hasNext()) { 
        containsA = containsAIt.next();
        alert = containsA.inVertex();
        // TBD: check lbls
        containsSIt = alert.edges(Direction.IN).  // has
                            next().               // (just one)
                            outVertex().          // source
                            edges(Direction.IN);  // deepcontains
        // loop over sources containing those alerts and sum their contributions
        while (containsSIt.hasNext()) {
          containsS = containsSIt.next();
          soi = containsS.outVertex();
          if (soi.property("lbl").value().toString().equals("SourcesOfInterest")) {
            clsS = soi.property("cls").value().toString();
            types.add(clsS);
            rel = Pair.of(clsA, clsS);
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
    // loop over types in alerts and sources and fill source sizes
    for (String a : types) {
      sz = 0;
      for (String s : types) {
        sz += weights.containsKey(Pair.of(a, s)) ? weights.get(Pair.of(a, s)) : 0;
        }
      sizesA.put(a, sz);
      }
    // loop over types in alerts and sources and fill alert sizes
    for (String s : types) {
      sz = 0;
      for (String a : types) {
        sz += weights.containsKey(Pair.of(a, s)) ? weights.get(Pair.of(a, s)) : 0;
        }
      sizesS.put(s, sz);
      }
    GraphTraversal<Vertex, Vertex> alertT;
    GraphTraversal<Vertex, Vertex> sourceT;
    int n = 0;
    // loop over types in alerts and sources and fill overlaps
    for (String a : types) {
      for (String s : types) {
        alertT  = g().V().has("lbl", "AlertsOfInterest" ).has("cls", a);
        sourceT = g().V().has("lbl", "SourcesOfInterest").has("cls", s);
        if (alertT.hasNext() && sourceT.hasNext() && weights.containsKey(Pair.of(a, s))) {
          n++;
          addEdge(alertT.next(),
                  sourceT.next(),
                  "overlaps",
                  new String[]{"intersection",
                               "sizeIn",
                               "sizeOut"            },
                  new Double[]{(double)weights.get(Pair.of(a, s)),
                               (double)sizesA.get(a),
                               (double)sizesS.get(s)},
                  true);
          }
        }
      }
    g().getGraph().tx().commit(); // TBD: should use just commit()
    log.info("" + n + " correlations generated");
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
    _fhclient.connect(table); // latest schema
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
  private static String FINK_LATESTS_WS = "https://fink-portal.org/api/v1/latests";
  private static String FINK_ANOMALY_WS = "https://fink-portal.org/api/v1/anomaly";

  /** Logging . */
  private static Logger log = Logger.getLogger(FinkGremlinRecipies.class);

  }
