package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Januser.ModifyingGremlinClient;
import com.Lomikel.Januser.GremlinRecipies;
import com.Lomikel.Januser.GremlinRecipiesGT;
import com.Lomikel.Phoenixer.PhoenixProxyClient;
import com.Lomikel.HBaser.HBaseClient;
import static com.Lomikel.Utils.Constants.π;

import com.astrolabsoftware.FinkBrowser.HBaser.FinkHBaseClient;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.io.IoCore;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.V;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.fold;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.has;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.not;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.out;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.in;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.repeat;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.values;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.count;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.addV;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.outV;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.constant;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.identity;
import static org.apache.tinkerpop.gremlin.process.traversal.P.within;
import static org.apache.tinkerpop.gremlin.process.traversal.Order.asc;

// Janus Graph
import org.janusgraph.core.SchemaViolationException;
import org.janusgraph.graphdb.vertices.StandardVertex;
import org.janusgraph.graphdb.database.StandardJanusGraph;
import static org.janusgraph.core.attribute.Geo.geoWithin;

// Groovy SQL
import groovy.sql.Sql;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>FinkGremlinRecipiesG</code> provides various recipies to handle
  * and modify Gremlin Graphs for Fink.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public trait FinkGremlinRecipiesGT extends GremlinRecipiesGT {

  /** TBD */
  def GraphTraversal geosearch(double ra,
                               double dec,
                               double ang,
                               double jdmin,
                               double jdmax,
                               int    limit) {
    def lat = dec;
    def lon = ra - 180;
    def dist = ang * 6371.0087714 * π / 180;
    def nDir = g().V().has('direction', geoWithin(Geoshape.circle(lat, lon, dist))).count().next();
    def nJD  = g().V().has('direction', geoWithin(Geoshape.circle(lat, lon, dist))).limit(nDir).has('jd', inside(jdmin, jdmax)).count().next();
    if (limit < nJD) {
      nJD = limit;
      }
    return g().V().has('direction', geoWithin(Geoshape.circle(lat, lon, dist))).limit(nDir).has('jd', inside(jdmin, jdmax)).limit(nJD);
    }
        
  /** Give {@link Map} of other <em>source</em>s ordered
    * by distance to the specified <em>source</em> with respect
    * to weights to all (or selected) <em>SourceOfInterest</em> classes.
    * @param oid0          The <em>objectOd</em> of the <em>source</em>.
    * @param classifier    The classifier name to be used.
    * @param ignorePartial Whether ignore entries when one value is <tt>0</tt>.
    *                      Default: <tt>false</tt>.
    *                      Optional named parameter.
    * @param nmax          The number of closest <em>source</em>s to give.
    *                      All are given, if missing.
    *                      Optional named parameter.
    * @return              The distances to other sources, order by the distance. */
  def Map<String, Double> sourceNeighborhood(Map          args = [:],
                                             String       oid0,
                                             String       classifier) {
    return sourceNeighborhood(args, oid0, classifier, null, null);
    }
        
  /** Give {@link Map} of other <em>source</em>s ordered
    * by distance to the specified <em>source</em> with respect
    * to weights to all (or selected) <em>SourceOfInterest</em> classes.
    * @param oid0          The <em>objectOd</em> of the <em>source</em>.
    * @param classifier    The classifier name to be used.
    * @param oidS          A {@link List} of <em>source</em> objectIds to only avaluated.
    *                      If <tt>null</tt>, all <em>source</em>s will be evaluated.
    * @param classes0      A {@link List} of <em>SourceOfInterest</em> classes to be
    *                      used in comparison.
    *                      All <em>SourceOfInterest</em> classes of the specified
    *                      <em>source</em> will be used if <tt>null</tt>.
    * @param ignorePartial Whether ignore entries when one value is <tt>0</tt>.
    *                      Optional naamed parameter.
    *                      Default: <tt>false</tt>.
    * @param nmax          The number of closest <em>source</em>s to give.
    *                      All are given, if missing.
    *                      Optional named parameter.
    * @return              The distances to other sources, order by the distance. */
  def Map<String, Double> sourceNeighborhood(Map          args = [:],
                                             String       oid0,
                                             String       classifier,
                                             List<String> oidS,
                                             List<String> classes0) {
    def ignorePartial = args.ignorePartial ?: false;
    def nmax          = args.nmax          ?: Integer.MAX_VALUE;
    if (g().V().has('lbl', 'source').has('objectId', oid0).count().next() == 0) {
      log.info(oid0 + " has no registered neighborhood");
      return [:];
      }
    def classifierClasses = g().V().has('lbl', 'SourcesOfInterest').has('classifier', classifier).values('cls').toSet();
    def source0 = g().V().has('lbl', 'source').has('objectId', oid0).next();
    def m0 = [:];
    g().V(source0).inE().
                   project('classifier', 'cls', 'w').
                   by(outV().values('classifier')).
                   by(outV().values('cls')).
                   by(values('weight')).
                   each {it ->
                         if (it['classifier'] == classifier) {
                           if (it['cls'] in classifierClasses) {
                             if (classes0 == null || it['cls'] in classes0) {
                               m0[it['cls']] = it['w'];
                               }
                             }
                           }
                         }
    def classes = [];
    for (entry : m0.entrySet()) {
      classes += [entry.getKey()];
      }
    log.info('calculating source distances from ' + oid0 + m0 + " ...");
    m0 = normalizeMap(m0);
    def distances = [:]
    def sources;
    if (oidS) {
      log.info("\tsearching only " + oidS);
      sources = g().V().has('lbl', 'source').
                        has('objectId', within(oidS));
      }
    else {
      // BUG: Janus-all.jar doesn't allow complex operations
      if (client() == null) {
        sources = g().V().has('lbl', 'SourcesOfInterest').
                          has('classifier', classifier).
                          has('cls', within(classes)).
                          out().
                          has('lbl', 'source').
                          dedup();  
        }
      else {
        sources = g().V().has('lbl', 'source');
        }
      }
    sources.each {s -> 
                  def oid = g().V(s).values('objectId').next();
                  def m = [:];
                  g().V(s).inE().
                           project('cls', 'classifier', 'w').
                           by(outV().values('cls')).
                           by(outV().values('classifier')).
                           by(values('weight')).
                           each {it ->
                                 if (it['classifier'].equals(classifier) &&
                                     it['cls'] in classes) {
                                   m[it['cls']] = it['w'];
                                   }
                                 }
                  m = normalizeMap(m);
                  def dist = sourceDistance(m0, m, ignorePartial);
                  if (dist > 0) {
                    distances[oid] = dist;
                    }     
                  }
    return distances.sort{it.value}.take(nmax);
    }
    
  /** Give distance (metric) between two classifier {@link Map}s.
    * @param m0            The first classifier {@link Map}.
    * @param m             The second classifier {@link Map}.
    *                      Entries, not present also in m0, will be ignored.
    * @param ignorePartial Whether ignore entries when one value is <tt>0</tt>.
    *                      Default: <tt>false</tt>.
    * @return              The distance between two {@link Map}s. */
  // BUG: 0 if close also if not related
  def double sourceDistance(Map<String, Double> m0,
                            Map<String, Double> m,
                            boolean             ignorePartial = false) {
    def dist = 0;
    def key1;
    def key2;
    def w01;
    def w02;
    def w012;
    def wx12;
    def wx1;
    def wx2;
    def w0;
    def wx;
    for (entry1 : m0.entrySet()) {
      for (entry2 : m0.entrySet()) {
        key1 = entry1.getKey();
        key2 = entry2.getKey();
        if (key1 > key2) {
          w01 = entry1.getValue();
          w02 = entry2.getValue();
          wx1 = m[key1] == null ? 0 : m[key1];
          wx2 = m[key2] == null ? 0 : m[key2];
          if (!ignorePartial || (w01 != 0 && w02 != 0 && wx1 != 0 && wx2 != 0)) {
            w012 = w01 + w02;
            wx12 = wx1 + wx2;
            w0 = (w012 == 0 ? 0 : Math.abs(w01 - w02) / w012);
            wx = (wx12 == 0 ? 0 : Math.abs(wx1 - wx2) / wx12);
            dist += Math.pow(w0 - wx, 2);
            }
          }
        }
      }
    return dist;
    }
    
  /** Normalize {@link Map}.
    * @param inputMap The {@link Map} to be normalized.
    * @return         The normalized {@link Map}. */
  def normalizeMap(Map<String, Double> inputMap) {
    def sum = inputMap.values().sum();
    def normalizedMap = inputMap.collectEntries {key, value -> [(key): value / sum]}
    return normalizedMap;
    }
  
    
  /** Drop all {@link Vertex} with specified <em>importDate</em>.
    * @param importDate The <em>importDate</em> of {@link Vertex}es to drop.
    *                   It's format should be like <tt>Mon Feb 14 05:51:20 UTC 2022</tt>.
    * @param nCommit    The number of {Vertex}es to drop before each commit.
    * @param tWait      The times (in <tt>s</tt>) to wait after each commit. */
  def drop_by_date(String importDate,
                   int    nCommit,
                   int    tWait) {
    def i = 0;
    def tot = 0;
    def nMax = g().V().has('importDate', importDate).count().next();
    log.info('' + nMax + ' vertexes to drop');
    def t0 = System.currentTimeMillis();
    while(true) {
      g().V().has('importDate', importDate).limit(nCommit).out().out().drop().iterate();
      g().V().has('importDate', importDate).limit(nCommit).out().drop().iterate();
      g().V().has('importDate', importDate).limit(nCommit).drop().iterate();
      graph().traversal().tx().commit();
      Thread.sleep(tWait)
      tot = nCommit * ++i;
      def dt = (System.currentTimeMillis() - t0) / 1000;
      def per = 100 * tot / nMax;
      def freq = tot / dt;
      def rest = (nMax - tot) / freq / 60 /60;
      log.info(tot + ' = ' + per + '% at ' + freq + 'Hz, ' + rest + 'h to go');
      }
    }
    
  /** Give status of importing from the <em>Import</em> {@link Vetex}es.
    * @return The status of importing from the <em>Import</em> {@link Vetex}es. */
  def String importStatus() {
    def txt = '';
    txt += 'Imported:\n';
    g().V().has('lbl', 'Import').
            has('nAlerts', neq(0)).
            order().
            by('importSource').
            valueMap('importSource', 'importDate', 'nAlerts').
            each {txt += '\t' + it + '\n'};
    txt += 'Importing:\n';
    g().V().has('lbl', 'Import').
            hasNot('complete').
            order().
            by('importSource').
            valueMap('importSource', 'importDate').
            each {txt += '\t' + it + '\n'};
    return txt;
    }

  /** Give recorded classification for all {@link Classifiers}.
    * @param oid The <em>source objectId</em>.
    * @return    The recorded classification calculated
    *            by number of classified <em>alert</em>s. */
  // TBD: handle missing oids
  def List classification(String oid,
                          String classifier = null) {
    return g().V().has('lbl', 'source').
                   has('objectId', oid).
                   choose(constant(classifier).is(null),
                          identity(),
                          has('classifier', classifier)).
                   inE().
                   project('weight', 'classifier', 'class').
                   by(values('weight')).
                   by(outV().values('classifier')).
                   by(outV().values('cls')).
                   toList();
    }    
    
  /** Give recorded classification. Recalculate classes from <tt>srcClassifier</tt>
    * to <tt>dstClassifier</tt>.
    * @param oid The <em>source objectId</em>.
    * @param srcClassifier The classifier to be used for primary classification.
    * @param dstClassifier The classifier to be used to interpret the classification.
    * @return    The recorded classification calculated
    *            by number of classified <em>alert</em>s. */
  // TBD: handle missing oids
  def Map reclassification(String oid,
                           String srcClassifier,
                           String dstClassifier) {                        
    def classified = classification(oid, srcClassifier);
    def reclassified = [:];      
    def w;
    classified.each {it ->  w = classify(it.class, lbl, srcClassifier, dstClassifier);
                            w.each {key, value -> if (reclassified[key] == null) {
                                                    reclassified[key] = 0;
                                                    }
                                                  reclassified[key] += value;
                            }
      }
    reclassified = reclassified.sort{-it.value};
    return reclassified;
    }
    
  /** Give all overlaps.
    * Using accumulated data in graph.
    * @param lbl        The label of {@link Vertex}es to use for overlap search.
    *                   Optional.
    * @param classifier The name of classifier to use for overlap search.
    *                   Optional. 
    * @param outputCSV  The filename for CSV file with overlaps.
    *                   Optional.
    * @return           The overlaps. */
  def Map overlaps(Map args) {
    def lbl        = args?.lbl;
    def classifier = args?.classifier;
    def outputCSV  = args?.outputCSV;
    def overlaps = [:];
    g().E().has('lbl', 'overlaps').
            order().
            by('intersection', asc).
            project('xlbl', 'xclassifier', 'xcls', 'ylbl', 'yclassifier', 'ycls', 'intersection').
            by(inV().values('lbl')).
            by(inV().values('classifier')).
            by(inV().values('cls')).
            by(outV().values('lbl')).
            by(outV().values('classifier')).
            by(outV().values('cls')).
            by(values('intersection')).
            each {v -> 
                  if ((lbl        == null ||  v['xlbl'       ].equals(lbl       ) || v['ylbl'       ].equals(lbl       )) &&
                      (classifier == null || (v['xclassifier'].equals(classifier) && v['yclassifier'].equals(classifier)))) {
                    overlaps[v['xlbl'] + ':' + v['xclassifier'] + ':' + v['xcls'] + ' * ' + v['ylbl'] + ':' + v['yclassifier'] + ':' + v['ycls']] = v['intersection'];
                    }
                  };
    overlaps = overlaps.sort{-it.value};
    if (outputCSV != null) {
      def csv = "type1,classifier1,class1,type2,classifier2,class2,overlap\n";
      overlaps.each{o -> csv += o.getKey().replaceAll(" \\* ", ",").replaceAll(":", ",") + "," + o.getValue() + "\n"};
      new File(outputCSV).text = csv;
      return null;
      }
    return overlaps;
    }
    
  /** Give classification from another {@link Classifier}.
    * Using accumulated data in graph.
    * @param cls           The class in the source classifier. 
    * @param lbl           The label of {@link Vertex}es. 
    * @param srcClassifier The name of classifier of the source (known) class.
    * @param dstClassifier The name of classifier of the destination (required) class.
    * @return           The new classification. */
  def Map classify(String cls,
                   String lbl,
                   String srcClassifier,
                   String dstClassifier) {
    def classification = [:];
    g().E().has('lbl', 'overlaps').
            order().
            by('intersection', asc).
            project('xlbl', 'xclassifier', 'xcls', 'ylbl', 'yclassifier', 'ycls', 'intersection').
            by(inV().values('lbl')).
            by(inV().values('classifier')).
            by(inV().values('cls')).
            by(outV().values('lbl')).
            by(outV().values('classifier')).
            by(outV().values('cls')).
            by(values('intersection')).
            each {v -> 
                  if (v['xlbl'       ].equals(lbl          ) &&
                      v['ylbl'       ].equals(lbl          ) &&
                      v['xcls'       ].equals(cls          ) &&
                      v['xclassifier'].equals(srcClassifier) &&
                      v['yclassifier'].equals(dstClassifier)) {
                    classification[v['ycls']] = v['intersection'];
                    }
                  };
    classification = classification.sort{-it.value};
    return classification;
    }
    
  /** Export all <em>AlertsOfInterest</em> and <em?SourcesOfInterest</em>
    * {@link Vertex}es with connecting <em>overlaps</em> {@link Edge}s
    * into <em>GraphML</em> file.
    * @param fn          The full filename of the output <em>GraphML</em> file.
    * @param collections The {@link List} of collective {@link Vetex}es names.
    *                    If missing, empty or <tt>tt</tt> null,
    *                    is used <em>AlertsOfInterest,SourcesOfInterest</em>. */
  def exportAoISoI(String       fn,
                   List<String> collections = null) {  
    if (collections == null || collections.isEmpty()) {
      collections = ['AlertsOfInterest', 'SourcesOfInterest'];
      }
    g().V().has('lbl', within(collections)).
            outE().
            has('lbl', 'overlaps').
            subgraph('x').
            cap('x').
            next().
            io(IoCore.graphml()).
            writeGraph(fn);
    }  
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkGremlinRecipiesGT.class);

  }