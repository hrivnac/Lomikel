package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.Metrics;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.Januser.GremlinRecipies;
import com.Lomikel.Januser.ModifyingGremlinClient;
import com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient;
import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
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
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;

// HBase
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Get;

// org.json
import org.json.JSONArray;
import org.json.JSONObject;

// Java
import java.lang.Math;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
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
    
  /** Execute full chain of new <em>object</em> correlations analyses.
    * @param classifiers The {@link Classifier}s to be used.
    *                        They can contain the {@link Classifier} flavor after <em>=</em> symbol.
    * @param filter          The HBase evaluation formula to be applied.
    *                        Ignored if <tt>clss</tt> are specified.
    * @param hbaseUrl        The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @param nLimit          The maximal number of alerts getting from HBase or Fink Portal.
    *                        <tt>0</tt> means no limit.
    * @param timeLimit       How far into the past the search should search (in minutes).
    * @param clss            An array of <em>classes</em> taken from {@link FPC},
    *                        if contains <tt>Anomaly</tt>, get anomalies from {@link FPC},                  
    *                        if <tt>null</tt>, analyse <em>object</em>s from HBase database.
    * @throws LomikelException If anything fails. */
  public void processOCol(Classifier[] classifiers,
                          String       filter,
                          String       hbaseUrl,
                          int          nLimit,
                          int          timeLimit,
                          String[]     clss) throws LomikelException {
    fillOCol(classifiers, filter, hbaseUrl, nLimit, timeLimit, clss);
    generateCorrelations(classifiers);
    }
        
  /** Fill graph with <em>OCol</em>.
    * @param classifiers The {@link Classifier}s to be used.
    * @param filter      The HBase evaluation formula to be applied.
    *                    Ignored if <tt>clss</tt> are specified.
    * @param hbaseUrl    The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @param nLimit      The maximal number of alerts getting from HBase or Fink Portal.
    *                    <tt>0</tt> means no limit.
    * @param timeLimit   How far into the past the search should search (in minutes).
    * @param clss        An array of <em>classes</em> taken from {@link FPC},
    *                    if contains <tt>Anomaly</tt>, get anomalies from {@link FPC},                  
    *                    if <tt>null</tt>, analyse <em>object</em>s from HBase database.

    * @throws LomikelException If anything fails. */
  public void fillOCol(Classifier[] classifiers,
                       String       filter,
                       String       hbaseUrl,
                       int          nLimit,
                       int          timeLimit,
                       String[]     clss) throws LomikelException {
    String clssDesc = "";
    if (clss != null) {
      clssDesc = "of " + Arrays.toString(clss);
      }
    log.info("Filling OCol " + clssDesc + " using " + Arrays.toString(classifiers) + " classifiers, nLimit = " + nLimit + ", timeLimit = " + timeLimit);
    log.info("Importing from " + hbaseUrl + ":");
    fhclient(hbaseUrl);
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
    classifySources(classifiers, oids, hbaseUrl);
    }
    
  /** Classify <em>object</em> .
    * @param classifiers The {@link Classifier}s to be used.
    * @param oids        The {@link Set} of <tt>objectId</tt>s of <em>object</em> to be added.
    * @param hbaseUrl    The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @throws LomikelException If anything fails. */
  public void classifySources(Classifier[] classifiers,
                              Set<String>  oids,
                              String       hbaseUrl) throws LomikelException {
    int size = oids.size();
    int n = 0;
    long dt;
    double freq;
    long startTime = System.currentTimeMillis();
    // loop over all objects
    for (String oid : oids) {
      log.info(oid + " (" + n + " of " + size + "):");
      for (Classifier classifier : classifiers) {
        try {
          classifySource(classifier, oid, hbaseUrl);
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
    
   /** Classify <em>object</em>.
    * @param classifier The {@link Classifier} to be used.
    * @param objectId   The <tt>objectId</tt> of <em>object</em> to be added.
    * @param hbaseUrl   The url of HBase with alerts as <tt>ip:port:table:schema</tt>.
    * @throws LomikelException If anything fails. */
  public void classifySource(Classifier classifier,
                             String     objectId,
                             String     hbaseUrl) throws LomikelException {  
    fhclient(hbaseUrl);
    classifySource(classifier, objectId);
    }
   
  /** Classify <em>object</em>.
    * @param classifier The {@link Classifier} to be used.
    * @param objectId   The <tt>objectId</tt> of <em>object</em> to be added.
    * @throws LomikelException If anything fails. */
  public void classifySource(Classifier classifier,
                             String     objectId) throws LomikelException {  
    if (g().V().has("lbl", "object").has("objectId", objectId).hasNext()) {
      Vertex v1 = g().V().has("lbl", "object").has("objectId", objectId).next();
      List<Vertex> v2s = g().V(v1).in().
                                   has("lbl",        "OCol").
                                   has("survey",     classifier.survey()).
                                   has("classifier", classifier.name()  ).
                                   has("flavor",     classifier.flavor()).
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
    classifier.classify(this, objectId);
    }
       
  /** Register  <em>object</em> in <em>OCol</em>.
    * @param classifier The {@link Classifier} to be used.
    * @param cls        The type (class) of <em>OCol</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param objectId   The objectId of the new <em>Source</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param weight     The weight of the connection.
    *                   Usualy the number of <em>Alerts</em> of this type. 
    * @param instanceS  The <em>jd</em> of related <em>Alerts</em> as strings separated by comma.
    *                   Potential square brackets are removed.
    *                   May be <tt>null</tt> or empty. */
  public void registerOCol(Classifier classifier,
                           String     cls,
                           String     objectId,
                           double     weight,
                           String     instancesS,
                           String     weightsS) {   
    List<String> instances = new ArrayList<>();
    List<Double> weights   = new ArrayList<>();
    if (instancesS != null && !instancesS.trim().equals("")) {
      for (String instance : instancesS.replaceAll("\\[", "").replaceAll("]", "").split(",")) {
        instances.add(instance);
        }
      }
    if (weightsS != null && !weightsS.trim().equals("")) {
      for (String weighs : weightsS.replaceAll("\\[", "").replaceAll("]", "").split(",")) {
        weights.add(Double.valueOf(weight));
        }
      }
    registerOCol(classifier, cls, objectId, weight, instances, weights);
    }
    
  /** Register <em>object</em> in <em>OCol</em>. Replace possible existing registration.
    * @param classifier The {@link Classifier} to be used.
    * @param cls        The type (class) of <em>OCol</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param objectId   The objectId of the new <em>Source</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param weight     The total weight of the connection.
    *                   Usualy the number of <em>Alerts</em> of this type. 
    * @param instances  The <em>jd</em> of related <em>Alerts</em>.
    * @param weights    The weights of related <em>Alerts</em>. */
  public void registerOCol(Classifier   classifier,
                           String       cls,
                           String       objectId,
                           double       weight,
                           List<String> instances,
                           List<Double> weights) { 
    Map<String, String> attributes = new HashMap<>();
    attributes.put("weight",    "" + weight);
    attributes.put("instances", instances.toString().replaceFirst("\\[", "").replaceAll("]", ""));
    attributes.put("weights",   weights.toString().replaceFirst("\\[", "").replaceAll("]", ""));
    registerOCol(classifier, cls, objectId, attributes, true);
    }
    
  /** Register <em>object</em> in <em>OCol</em>.
    * @param classifier The {@link Classifier} to be used.
    * @param cls        The type (class) of <em>OCol</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param objectId   The objectId of the new <em>Source</em> {@link Vertex}.
    *                   It will be created if not yet exists.
    * @param attributes The additional {@link Edge} attributes.    
    * @param replace    Whether to replace existing resistration. */
  public void registerOCol(Classifier          classifier,
                           String              cls,
                           String              objectId,
                           Map<String, String> attributes,
                           boolean             replace) {   
    //log.info("\tregistering " + objectId + " as " + classifier + " / " + cls + " with attributes " + attributes + ", replace = " + replace);
    log.info("\tregistering " + objectId + " as " + classifier + " / " + cls + " with weight = " + attributes.get("weight") + ", replace = " + replace);
    Vertex ocol = g().V().has("lbl",        "OCol"             ).
                          has("survey",     classifier.survey()).
                          has("classifier", classifier.name()  ).
                          has("flavor",     classifier.flavor()).
                          has("cls",        cls                ).
                          fold().
                          coalesce(unfold(), 
                                  addV("OCol").
                                  property("lbl",        "OCol").
                                  property("survey",     classifier.survey()).
                                  property("classifier", classifier.name()  ).
                                  property("flavor",     classifier.flavor()).
                                  property("cls",        cls                )).
                         next();
    Vertex s = g().V().has("lbl",      "object").
                       has("objectId", objectId).
                       fold().
                       coalesce(unfold(), 
                                addV("object").
                                property("lbl",      "object").
                                property("objectId", objectId)).
                       property("importDate", _now).
                       next();
    if (replace) {
      addEdge(g().V(ocol).next(),
              g().V(s).next(),
              "deepcontains",
              attributes.keySet().toArray(new String[0]),
              attributes.values().toArray(new String[0]),
              true);
      }
    else {
      Edge e = ocol.addEdge("deepcontains", s);
      e.property("lbl", "deepcontains");
      for (Map.Entry<String, String> attribute : attributes.entrySet()) {
        e.property(attribute.getKey(), attribute.getValue());
        }
      }
    commit();
    }
   
  /** Clean tree under <em>OCol</em>.
    * Drop alerts. Alerts are dropped even if they have other
    * {@link Edge}s.
    * @param classifier The {@link Classifier} to be used.
    *                   All its <em>flavors</em> are handled.
    * @param cls        The type (class) of <em>OCol</em>.
    * @throws LomikelException If anything goes wrong. */
  public void cleanOCol(Classifier classifier,
                        String     cls) throws LomikelException {
    log.info("Cleaning " + cls + " OCol");
    g().V().has("lbl",        "OCol").
            has("survey",     classifier.survey()).
            has("classifier", classifier.name()  ).
            has("flavor",     classifier.flavor()).
            has("cls",        cls                ).
            out().
            out().
            drop().
            iterate();
    commit();
    }
        
  /** Generate <em>overlaps</em> Edges between <em>OCol</em>.
    * Possibly between two {@link Classifier}s.
    * @param classifier The {@link Classifier}s to be used. */
  public void generateCorrelations(Classifier... classifiers) {
    log.info("Generating correlations for OCol of " + Arrays.asList(classifiers));
    List<String> surveysL = new ArrayList<>();
    List<String> namesL   = new ArrayList<>();
    List<String> flavorsL = new ArrayList<>();
    for (Classifier classifier : classifiers) {
      surveysL.add(classifier.survey());
      namesL.add(  classifier.name()  );
      flavorsL.add(classifier.flavor());
      // Clean all correlations 
      g().V().has("lbl",        "OCol"             ).
              has("survey",     classifier.survey()).
              has("classifier", classifier.name()  ).
              has("flavor",     classifier.flavor()).
              bothE().
              has("lbl", "overlaps").
              drop().
              iterate();
      // Remove wrong OCol
      g().V().has("lbl",        "OCol"             ).
              has("survey",     classifier.survey()).
              has("classifier", classifier.name()  ).
              has("flavor",     classifier.flavor()).
              not(has("cls")).
              drop().
              iterate();
      }
    String[] surveys = surveysL.toArray(String[]::new);
    String[] names   = namesL.toArray(  String[]::new);
    String[] flavors = flavorsL.toArray(String[]::new);
    commit();
    // Accumulate correlations and sizes
    Map<String, Double>               weights0 = new HashMap<>(); // cls -> weight (for one objext)
    Map<Pair<String, String>, Double> corrS    = new HashMap<>(); // [cls1, cls2] -> weight (for all object between OCol-OCol)
    Map<String, Double>               sizeS    = new HashMap<>(); // cls -> total (for all objects of OCol)
    SortedSet<String>                 types0   = new TreeSet<>(); // [cls] (for one object)
    SortedSet<String>                 types    = new TreeSet<>(); // [cls] (for all objects)
    Vertex object;
    Iterator<Edge> deepcontainsIt;
    Edge deepcontains;
    double weight;
    double weight1;
    double weight2;
    double cor;
    Vertex ocol;
    Vertex ocol1;
    Vertex ocol2;
    String cls;
    Pair<String, String> rel;
    // Loop over objets and accumulated weights to each object
    GraphTraversal<Vertex, Vertex> objectT = g().V().has("lbl", "object");
    while (objectT.hasNext()) {
      weights0.clear();
      types0.clear();
      object = objectT.next();
      deepcontainsIt = object.edges(Direction.IN);
      // Get all weights to this object
      while (deepcontainsIt.hasNext()) {
        deepcontains = deepcontainsIt.next();
        weight = Double.parseDouble(deepcontains.property("weight").value().toString());
        ocol1 = deepcontains.outVertex();
        cls = ocol1.property("cls").value().toString();
        types0.add(cls);
        types.add(cls);
        weights0.put(cls, weight);
        }
      // Double loop over accumulated weights and fill weights between OCols
      for (String cls1 : types0) {
        weight1 = weights0.get(cls1);
        for (String cls2 : types0) {
          weight2 = weights0.get(cls2);
          rel = Pair.of(cls1, cls2);
          // OCol-OCol
          if (!corrS.containsKey(rel)) {
            corrS.put(rel, 0.0);
            }
          cor = corrS.get(rel);
          //corrS.put(rel, cor + 1.0);
          corrS.put(rel, cor + Math.sqrt(weight1 * weight2));
          }
        }
      }
    // Fill total sizes
    double sizeS0;
    for (String cls1 : types) {
      sizeS0 = 0.0;
      for (String cls2 : types) {
        if (corrS.containsKey(Pair.of(cls1, cls2))) {
          sizeS0 += corrS.get(Pair.of(cls1, cls2));
          }
        }
      sizeS.put(cls1, sizeS0);
      }
    // Create overlaps
    int ns = 0;
    // Double-loop over OCol and create overlaps Edge OCol-OCol if non empty 
    // NOTE: it takes all OCol names, surveys and flavors (even if they are not requested in all combinations)
    for (String cls1 : types) {
      try {
        ocol1 = g().V().has("lbl",        "OCol"          ).
                        has("survey",     within(surveys) ).
                        has("classifier", within(names)  ).
                        //has("flavor",     within(flavors)).
                        has("cls",        cls1           ).
                        next();
        for (String cls2 : types) {
          if (corrS.containsKey(Pair.of(cls1, cls2))) {
            try {
              ocol2 = g().V().has("lbl",        "OCol"         ).
                              has("survey",     within(surveys)).
                              has("classifier", within(names)  ).
                              //has("flavor",     within(flavors)).
                              has("cls",        cls2           ).
                              next();
              addEdge(g().V(ocol1).next(),
                      g().V(ocol2).next(),
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
              log.debug("OCol for " + cls2 + " doesn't exist");
              }          
            }  
          }
        }
      catch (NoSuchElementException e) {
        log.debug("OCol for " + cls1 + " doesn't exist");
        }          
      }
    commit();
    log.info("" + ns + " object-object correlations generated");
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
 
  private static String FINK_OBJECTS_WS = "https://api.ztf.fink-portal.org/api/v1/objects";
  private static String FINK_LATESTS_WS = "https://api.ztf.fink-portal.org/api/v1/latests";
  private static String FINK_ANOMALY_WS = "https://api.ztf.fink-portal.org/api/v1/anomaly";

  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkGremlinRecipies.class);

  }
