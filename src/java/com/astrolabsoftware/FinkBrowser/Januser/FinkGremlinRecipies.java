package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.SmallHttpClient;
import com.Lomikel.Utils.NotifierURL;
import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.HBaser.HBaseClient;
import com.Lomikel.Januser.GremlinRecipies;
import com.Lomikel.Januser.ModifyingGremlinClient;
import com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient;
import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.otherV;
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
import static org.apache.tinkerpop.gremlin.process.traversal.P.within;
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
import java.util.stream.Collectors;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import java.util.NoSuchElementException;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>FinkGremlinRecipies</code> provides various recipies to handle
  * and modify Gremlin Graphs for Fink.
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
    * @param classifierNames The names of the {@link Classifier} to be used.
    * @param filter          The HBase evaluation formula to be applied.
    *                        Ignored if <tt>clss</tt> are specified.
    * @param hbaseUrl        The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @param nLimit          The maximal number of alerts getting from HBase or Fink Portal.
    *                        <tt>0</tt> means no limit.
    * @param timeLimit       How far into the past the search should search (in minutes).
    * @param clss            An array of <em>classes</em> taken from {@link FPC},
    *                        if contains <tt>Anomaly</tt>, get anomalies from {@link FPC},                  
    *                        if <tt>null</tt>, analyse <em>sources</em> from HBase database.
    * @param enhance         Whether expand tree under all <em>SoI</em> with alerts
    *                        possibly filled with requested HBase columns.
    * @param columns         HBase columns to be copied into graph alerts. May be <tt>null</tt>.
    * @throws LomikelException If anything fails. */
  public void processSoI(String[] classifierNames,
                                       String   filter,
                                       String   hbaseUrl,
                                       int      nLimit,
                                       int      timeLimit,
                                       String[] clss,
                                       boolean  enhance,
                                       String   columns) throws LomikelException {
    Classifiers[] classifiers = new Classifiers[classifierNames.length];
    for (int i = 0; i < classifierNames.length; i++) {
      classifiers[i] = Classifiers.valueOf(classifierNames[i]);
      }
    fillSoI(classifiers, filter, hbaseUrl, nLimit, timeLimit, clss, enhance, columns);
    generateCorrelations(classifiers);
    }
        
  /** Fill graph with <em>SoI</em> and expand them to alerts (if requested).
    * @param classifiers The {@link Classifiers}s to be used.
    * @param filter          The HBase evaluation formula to be applied.
    *                        Ignored if <tt>clss</tt> are specified.
    * @param hbaseUrl    The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @param nLimit      The maximal number of alerts getting from HBase or Fink Portal.
    *                    <tt>0</tt> means no limit.
    * @param timeLimit   How far into the past the search should search (in minutes).
    * @param clss        An array of <em>classes</em> taken from {@link FPC},
    *                    if contains <tt>Anomaly</tt>, get anomalies from {@link FPC},                  
    *                    if <tt>null</tt>, analyse <em>sources</em> from HBase database.
    * @param enhance     Whether expand tree under all <em>SoI</em> with alerts
    *                    possibly filled with requested HBase columns.
    * @param columns     The HBase columns to be copied into graph alerts. May be <tt>null</tt>.
    * @throws LomikelException If anything fails. */
  public void fillSoI(Classifiers[] classifiers,
                                    String        filter,
                                    String        hbaseUrl,
                                    int           nLimit,
                                    int           timeLimit,
                                    String[]      clss,
                                    boolean       enhance,
                                    String        columns) throws LomikelException {
    String clssDesc = "";
    if (clss != null) {
      clssDesc = "of " + Arrays.toString(clss);
      }
    log.info("Filling SoI " + clssDesc + " using " + Arrays.toString(classifiers) + " classifiers, nLimit = " + nLimit + ", timeLimit = " + timeLimit);
    log.info("Importing from " + hbaseUrl + ":");
    fhclient(hbaseUrl);
    if (enhance) {
      log.info("\tenhancing with " + columns);
      }
    Set<String> oids = new HashSet<>();;
    if (clss == null) { 
      fhclient().setEvaluation(filter);
      if (nLimit > 0) {
        fhclient().setLimit(nLimit);
        }
      oids = fhclient().latestsT("i:objectId",
                                 null,
                                 timeLimit,
                                 true);
      fhclient().setEvaluation(null);
      }
    else {
      Calendar cal;
      Date d;
      String sd;
      JSONArray ja;
      JSONObject jo;
      for (String cls : clss) {
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
        log.info("*** " + cls + "[" + ja.length() + "]:");
        }
      }
    classifySources(classifiers, oids, hbaseUrl, enhance, columns);
    }
    
  /** Classify <em>source</em> and expand them to alerts (if requested).
    * @param classifiers The {@link Classifiers}s to be used.
    * @param oids        The {@link Set} of <tt>objectId</tt>s of source to be added.
    * @param hbaseUrl    The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @param enhance     Whether expand tree under all <em>SoI</em> with alerts
    *                    possibly filled with requested HBase columns.
    * @param columns     The HBase columns to be copied into graph alerts. May be <tt>null</tt>. 
    * @throws LomikelException If anything fails. */
  public void classifySources(Classifiers[] classifiers,
                              Set<String>   oids,
                              String        hbaseUrl,
                              boolean       enhance,
                              String        columns) throws LomikelException {
    int size = oids.size();
    int n = 0;
    long dt;
    double freq;
    long startTime = System.currentTimeMillis();
    // loop over all sources
    for (String oid : oids) {
      log.info(oid + " (" + n + " of " + size + "):");
      for (Classifiers classifier : classifiers) {
        try {
          classifySource(classifier, oid, hbaseUrl, enhance, columns);
          }
        catch (LomikelException e) {
          log.error("Cannot get classification for " + oid, e);
          }
        }
      n++;
      dt = (System.currentTimeMillis() - startTime) / 1000;
      freq = (double)n / (double)dt;
      log.info("\t\twith " + String.format("%.2f", freq) + " Hz");
      }
    }
    
  /** Classify <em>source</em> and expand them to alerts (if requested).
    * @param classifier The {@link Classifier} to be used.
    * @param objectId   The <tt>objectId</tt> of source to be added.
    * @param hbaseUrl   The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @param enhance    Whether expand tree under all <em>SoI</em> with alerts
    *                   possibly filled with requested HBase columns.
    * @param columns    The HBase columns to be copied into graph alerts. May be <tt>null</tt>. 
    * @throws LomikelException If anything fails. */
  public void classifySource(Classifiers classifier,
                             String      objectId,
                             String      hbaseUrl,
                             boolean     enhance,
                             String      columns) throws LomikelException {  
    if (g().V().has("lbl", "source").has("objectId", objectId).hasNext()) {
      Vertex v1 = g().V().has("lbl", "source").has("objectId", objectId).next();
      List<Vertex> v2s = g().V(v1).in().
                                   has("lbl", "SoI").
                                   has("classifier", classifier.name()).
                                   toList();
      Iterator<Edge> edges;
      for (Vertex v2 : v2s) {
        edges = g().V(v1).inE().
                          has("lbl", "deepcontains").
                          where(otherV().
                          is(v2)).
                          toStream().
                          iterator();
        while (edges.hasNext()) {
          edges.next().remove(); 
          }
        }        
      // will be commited in registration
      }
    classifier.instance().classify(this, objectId, enhance, columns);
    }
       
  /** Register  <em>source</em> in <em>SoI</em>.
    * @param classifier The {@link Classifier} to be used.
    * @param cls        The type (class) of <em>SoI</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param objectId   The objectId of the new <em>Source</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param weight     The weight of the connection.
    *                   Usualy the number of <em>Alerts</em> of this type. 
    * @param instanceS  The <em>jd</em> of related <em>Alerts</em> as strings separated by comma.
    *                   Potential square brackets are removed.
    *                   May be <tt>null</tt> or empty.
    * @param enhance    Whether expand tree under all <em>SoI</em> with alerts
    *                   possibly filled with requested HBase columns.
    * @param columns    The HBase columns to be filled into alerts. May be <tt>null</tt>.
    *                   Ignored if enhancement not requested. */
  public void registerSoI(Classifiers classifier,
                                        String      cls,
                                        String      objectId,
                                        double      weight,
                                        String      instancesS,
                                        boolean     enhance,
                                        String      columns) {   
    Set<Double> instances = new HashSet<>();
    if (instancesS != null && !instancesS.trim().equals("")) {
      for (String instance : instancesS.replaceAll("\\[", "").replaceAll("]", "").split(",")) {
        instances.add(Double.parseDouble(instance));
        }
      }
    registerSoI(classifier, cls, objectId, weight, instances, enhance, columns);
    }
    
  /** Register <em>source</em> in <em>SoI</em>.
    * @param classifier The {@link Classifier} to be used.
    * @param cls        The type (class) of <em>SoI</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param objectId   The objectId of the new <em>Source</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param weight     The weight of the connection.
    *                   Usualy the number of <em>Alerts</em> of this type. 
    * @param instances  The <em>jd</em> of related <em>Alerts</em>.
    * @param enhance    Whether expand tree under all <em>SoI</em> with alerts
    *                   filled with requested HBase columns.
    * @param columns The HBase columns to be filled into alerts. May be <tt>null</tt>.
    *                   Ignored if enhancement not requested. */
  public void registerSoI(Classifiers classifier,
                                        String      cls,
                                        String      objectId,
                                        double      weight,
                                        Set<Double> instances,
                                        boolean     enhance,
                                        String      columns) {   
    log.info("\tregistering " + objectId + " as " + cls + " with weight " + weight);
    Vertex soi = g().V().has("lbl",        "SoI").
                         has("classifier", classifier.name()).
                         has("cls",        cls).
                         fold().
                         coalesce(unfold(), 
                                  addV("SoI").
                                  property("lbl",        "SoI").
                                  property("classifier", classifier.name()  ).
                                  property("cls",        cls                ).
                                  property("technology", "HBase"            ).
                                  property("url",        hbaseUrl()         )).
                         next();
    Vertex s = g().V().has("lbl", "source").
                       has("objectId", objectId).
                       fold().
                       coalesce(unfold(), 
                                addV("source").
                                property("lbl",      "source").
                                property("objectId", objectId)).
                       property("importDate", _now).
                       next();
    addEdge(g().V(soi).next(),
            g().V(s).next(),
            "deepcontains",
            new String[]{"weight",    "instances"                                                     },
            new String[]{"" + weight, instances.toString().replaceFirst("\\[", "").replaceAll("]", "")},
            true);
    if (enhance) {
      try {
        enhanceSource(classifier, s, instances.toString().replaceFirst("\\[", "").replaceAll("]", "").split(","), columns);
        }
      catch (LomikelException e) {
        log.error("Cannot enhance source", e);
        }
      }
    commit(); // TBD: not needed if enhancing
    }
        
  /** Expand tree under all <em>SoI</em> with alerts
    * filled with requested HBase columns.
    * @param classifier The {@link Classifier} to be used.
    * @param columns    The HBase columns to be filled into alerts. May be <tt>null</tt>.
    * @throws LomikelException If anything goes wrong. */
  public void enhanceSoI(Classifiers classifier,
                                       String      columns) throws LomikelException {
    log.info("Expanding all SoI and enhancing them with " + columns);
    for (Object soi : g().V().has("lbl",        "SoI").
                              has("classifier", classifier.name()  ).
                              values("cls"                         ).
                              toSet()) {
      enhanceSoI(classifier, soi.toString().trim(), columns);
      }
    }

  /** Expand tree under <em>SoI</em> with alerts
    * filled with requested HBase columns.
    * @param classifier The {@link Classifier} to be used.
    * @param cls        The type (class) of <em>SoI</em>.
    * @param columns    The HBase columns to be filled into alerts. May be <tt>null</tt>.
    * @throws LomikelException If anything goes wrong. */
  public void enhanceSoI(Classifiers classifier,
                                       String      cls,
                                       String      columns) throws LomikelException {
    log.info("Expanding " + cls + " SoI and enhancing them with " + columns);
    Vertex soi = g().V().has("lbl",        "SoI").
                         has("classifier", classifier.name()  ).
                         has("cls",        cls                ).
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
      enhanceSource(classifier, source, jds, columns);
      }
    }
 
  /** Expand tree under <em>source</em> with alerts
    * filled with requested HBase columns. It also assembles
    * related AoI.
    * @param classifier The {@link Classifier} to be used.
    * @param source     The source.
    * @param jds        The <em>jd</em> of related <em>Alerts</em>.
    * @param columns    The HBase columns to be filled into alerts. May be <tt>null</tt>.
    * @throws LomikelException If anything goes wrong. */
  public void enhanceSource(Classifiers classifier,
                            Vertex      source,
                            String[]    jds,
                            String      columns) throws LomikelException {
    String objectId = source.property("objectId").value().toString();
    int n = 0;
    String key;    
    Vertex alert;
    List<Map<String, String>> results;
    for (String jd : jds) {
      n++;
      key = objectId + "_" + jd.trim();
      alert = g().V().has("lbl",      "alert").
                      has("objectId", objectId).
                      has("jd",       jd).
                      fold().
                      coalesce(unfold(), 
                               addV("alert").
                               property("lbl",     "alert"  ).
                               property("objectId", objectId).
                               property("jd",       jd      )).
                      property("importDate", _now).
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
      assembleAoI(classifier, alert);
      }
    commit();
    log.info("\t\t" + n + " alerts added");
    }
   
  /** Clean tree under <em>SoI</em>.
    * Drop alerts. Alerts are dropped even if they have other
    * {@link Edge}s.
    * @param classifier The {@link Classifier} to be used.
    * @param cls        The type (class) of <em>SoI</em>.
    * @throws LomikelException If anything goes wrong. */
  public void cleanSoI(Classifiers classifier,
                       String      cls) throws LomikelException {
    log.info("Cleaning " + cls + " SoI");
    g().V().has("lbl",        "SoI").
            has("classifier", classifier.name()  ).
            has("cls",        cls                ).
            out().
            out().
            drop().
            iterate();
    commit();
    }
        
  /** Assemble AoI from all existing alerts. 
    * @param classifier The {@link Classifier} to be used. */ 
  public void assembleAoI(Classifiers classifier) {
    log.info("Assembling AoI");
    GraphTraversal<Vertex, Vertex> alertT = g().V().has("lbl", "alert");
    Vertex alert;
    while (alertT.hasNext()) {
      alert = alertT.next();
      assembleAoI(classifier, alert);
      }
    }
    
  /** Assemble AoI from an alert.
    * @param classifier The {@link Classifier} to be used. 
    * @param alert      The existing alert. */
  public void assembleAoI(Classifiers classifier,
                          Vertex      alert) {
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
        aoi = g().V().has("lbl",        "AoI").
                      has("classifier", classifier.name() ).
                      has("cls",        cls               ).
                      fold().
                      coalesce(unfold(), 
                               addV("AoI").
                               property("lbl",        "AoI").
                               property("classifier", classifier.name() ).
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
    commit();
    }
        
  /** Generate <em>overlaps</em> Edges between <em>AoI</em> and <em>SoI</em>.
    * Possibly between two {@link Classifier}s.
    * @param classifiers The {@link Classifier}s to be used. */
  public void generateCorrelations(Classifiers... classifiers) {
    String[] classifierNames = Arrays.stream(classifiers).map(c -> c.name()).toArray(String[]::new);
    log.info("Generating correlations for Sources and Alerts of Interest for " + Arrays.toString(classifierNames));
    for (String classifierName : classifierNames) {
      // Clean all correlations 
      g().V().has("lbl", "AoI" ).has("classifier", classifierName).bothE().has("lbl", "overlaps").drop().iterate();
      g().V().has("lbl", "SoI").has("classifier", classifierName).bothE().has("lbl", "overlaps").drop().iterate();
      // Remove wrong SoI, AoI
      g().V().has("lbl", "AoI" ).has("classifier", classifierName).not(has("cls")).drop().iterate();
      g().V().has("lbl", "SoI").has("classifier", classifierName).not(has("cls")).drop().iterate();
      }
    commit();
    // Accumulate correlations and sizes
    Map<String, Double>               weights0 = new HashMap<>(); // cls -> weight (for one source)
    Map<Pair<String, String>, Double> corrS    = new HashMap<>(); // [cls1, cls2] -> weight (for all sources between SoI-SoI)
    Map<Pair<String, String>, Double> corrA    = new HashMap<>(); // [cls1, cls2] -> weight (for all sources between AoI-AoI)
    Map<String, Double>               sizeS    = new HashMap<>(); // cls -> total (for all sources of SoI)
    Map<String, Double>               sizeA    = new HashMap<>(); // cls -> total (for all sources of AoI)
    SortedSet<String>                 types0   = new TreeSet<>(); // [cls] (for one source)
    SortedSet<String>                 types    = new TreeSet<>(); // [cls] (for all sources)
    Vertex source;
    Iterator<Edge> deepcontainsIt;
    Edge deepcontains;
    double weight;
    double weight1;
    double weight2;
    double cor;
    Vertex soi;
    Vertex soi1;
    Vertex soi2;
    Vertex aoi1;
    Vertex aoi2;
    String cls;
    Pair<String, String> rel;
    // Loop over sources and accumulated weights to each source
    GraphTraversal<Vertex, Vertex> sourceT = g().V().has("lbl", "source");
    while (sourceT.hasNext()) {
      weights0.clear();
      types0.clear();
      source = sourceT.next();
      deepcontainsIt = source.edges(Direction.IN);
      // Get all weights to this source
      while (deepcontainsIt.hasNext()) {
        deepcontains = deepcontainsIt.next();
        weight = Double.parseDouble(deepcontains.property("weight").value().toString());
        soi1 = deepcontains.outVertex();
        cls = soi1.property("cls").value().toString();
        types0.add(cls);
        types.add(cls);
        weights0.put(cls, weight);
        }
      // Double loop over accumulated weights and fill weights between SoIs/AoIs
      for (String cls1 : types0) {
        weight1 = weights0.get(cls1);
        for (String cls2 : types0) {
          weight2 = weights0.get(cls2);
          rel = Pair.of(cls1, cls2);
          // SoI-SoI
          if (!corrS.containsKey(rel)) {
            corrS.put(rel, 0.0);
            }
          cor = corrS.get(rel);
          corrS.put(rel, cor + 1.0);
          // AoI-AoI
          if (!corrA.containsKey(rel)) {
            corrA.put(rel, 0.0);
            }
          cor = corrA.get(rel);
          corrA.put(rel, cor + weight2);
          }
        }
      }
    // Fill total sizes
    double sizeS0;
    double sizeA0;
    for (String cls1 : types) {
      sizeS0 = 0.0;
      sizeA0 = 0.0;
      for (String cls2 : types) {
        if (corrS.containsKey(Pair.of(cls1, cls2))) {
          sizeS0 += corrS.get(Pair.of(cls1, cls2));
          }
        if (corrA.containsKey(Pair.of(cls1, cls2))) {
          sizeA0 += corrA.get(Pair.of(cls1, cls2));
          }
        }
      sizeS.put(cls1, sizeS0);
      sizeA.put(cls1, sizeA0);
      }
    // Create overlaps
    int ns = 0;
    int na = 0;
    // Loop over SoI and create AoI
    String hbaseUrl;
    String classifierName;
    GraphTraversal<Vertex, Vertex> soiT = g().V().has("lbl", "SoI");
    while (soiT.hasNext()) {
      soi = soiT.next();
      hbaseUrl = soi.property("url").value().toString();
      classifierName = soi.property("classifier").value().toString();
      cls = soi.property("cls").value().toString();
      g().V().has("lbl",        "AoI").
              has("classifier", classifierName    ).
              has("cls",        cls               ).
              fold().
              coalesce(unfold(), 
                       addV("AoI").
                       property("lbl",        "AoI").
                       property("classifier", classifierName    ).
                       property("cls",        cls               ).
                       property("technology", "HBase"           ).
                       property("url",        hbaseUrl          )).
              iterate();
      }
    // Double-loop over SoI and create overlaps Edge SoI-SoI if non empty 
    for (String cls1 : types) {
      try {
        soi1 = g().V().has("lbl",        "SoI"    ).
                       has("classifier", within(classifierNames)).
                       has("cls",        cls1                   ).
                       next();
        for (String cls2 : types) {
          if (corrS.containsKey(Pair.of(cls1, cls2))) {
            try {
              soi2 = g().V().has("lbl",        "SoI"    ).
                             has("classifier", within(classifierNames)).
                             has("cls",        cls2                   ).
                             next();
              addEdge(g().V(soi1).next(),
                      g().V(soi2).next(),
                      "overlaps",
                      new String[]{"intersection",                
                                   "sizeIn",            
                                   "sizeOut"},
                      new Double[]{corrS.get(Pair.of(cls1, cls2)),
                                   sizeS.get(cls1),
                                   sizeS.get(cls2)},
                      true);
              ns++;
              }
            catch (NoSuchElementException e) {
              log.debug("SoI for " + cls2 + " doesn't exist");
              }          
            }  
          }
        }
      catch (NoSuchElementException e) {
        log.debug("SoI for " + cls1 + " doesn't exist");
        }          
      }
    // Double-loop over AoI and create overlaps Edge AoI-AoI if non empty 
    for (String cls1 : types) {
      try {
        aoi1 = g().V().has("lbl",        "AoI"     ).
                       has("classifier", within(classifierNames)). 
                       has("cls",        cls1                   ).
                       next();
        for (String cls2 : types) {
          if (corrA.containsKey(Pair.of(cls1, cls2))) {
            try {
              aoi2 = g().V().has("lbl",        "AoI"     ).
                             has("classifier", within(classifierNames)).
                             has("cls",        cls2                   ).
                             next();
              addEdge(g().V(aoi1).next(),
                      g().V(aoi2).next(),
                      "overlaps",
                      new String[]{"intersection",                
                                   "sizeIn",            
                                   "sizeOut"},
                      new Double[]{corrA.get(Pair.of(cls1, cls2)),
                                   sizeA.get(cls1),
                                   sizeA.get(cls2)},
                      true);
              na++;
              }
            catch (NoSuchElementException e) {
              log.debug("AoI for " + cls2 + " doesn't exist");
              }          
            }  
          }
        }
      catch (NoSuchElementException e) {
        log.debug("SoI for " + cls1 + " doesn't exist");
        }          
      }
    commit();
    log.info("" + ns + ", " + na + " source-source and source-alert correlations generated");
    }
        
  /** Create a new {@link FinkHBaseClient}. Singleton when url unchanged.
    * @param hbaseUrl The HBase url as <tt>ip:port:table[:schema]</tt>.
    * @return          The corresponding {@link FinkHBaseClient}, created and initialised if needed.
    * @throws LomikelException If cannot be created. */
  public FinkHBaseClient fhclient(String hbaseUrl) throws LomikelException {
    if (hbaseUrl == null || hbaseUrl.equals(_fhclientUrl)) {
      return _fhclient;
      }
    _fhclientUrl = hbaseUrl;
    String[] url = hbaseUrl.split(":");
    String ip     = url[0];
    String port   = url[1];
    String table  = url[2];
    String schema = "";
    if (url.length >= 4) {
      schema = url[3];
      }
    _fhclient = new FinkHBaseClient(ip, port);
    _fhclient.connect(table, schema);
    return _fhclient;
    }
    
  /** Get existing {@link FinkHBaseClient}.
    * @return The corresponding {@link FinkHBaseClient}.
    * @throws LomikelException If not yet created. */
  public FinkHBaseClient fhclient() throws LomikelException {
    if (_fhclient == null) {
      throw new LomikelException("FinkHBaseClient not initialised");
      }
    return _fhclient;
    }
    
  /** Give HBase url.
    * @return The HBase url as <tt>ip:port:table[:schema]</tt>. */
  public String hbaseUrl() {
    return _fhclientUrl;
    }
    
  private FinkHBaseClient _fhclient;
  
  private String _fhclientUrl;
   
  private String _now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()).toString();
 
  private static String FINK_OBJECTS_WS = "https://api.fink-portal.org/api/v1/objects";
  private static String FINK_LATESTS_WS = "https://api.fink-portal.org/api/v1/latests";
  private static String FINK_ANOMALY_WS = "https://api.fink-portal.org/api/v1/anomaly";

  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkGremlinRecipies.class);

  }
