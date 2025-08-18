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
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.and;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.select;
import static org.apache.tinkerpop.gremlin.process.traversal.P.within;
import static org.apache.tinkerpop.gremlin.process.traversal.P.neq;
import static org.apache.tinkerpop.gremlin.process.traversal.P.eq;
import static org.apache.tinkerpop.gremlin.process.traversal.P.gte;
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
    * Include classification for each neighbour.
    * @param oid0          The <em>objectOd</em> of the <em>source</em>.
    * @param classifier    The classifier name to be used.
    * @param nmax          The number of closest <em>source</em>s to give.
    *                      All are given, if missing.
    * @param metric        The metric to use <tt>JensenShannon, Euclidean or Cosine</tt>.
    *                      Default: <tt>JensenShannon</tt>. Anyhing else gives random metric - for testing.
    * @param climit        The low limit fir the classification ration of the evaluated source.
    *                      Default: <tt>0.0</tt>.
    * @param allClasses    Whether to consider also classes not available in original source.
    *                      Default: <tt>false</tt>.
    * @return              The full neigbouthood information. */
  def Map<Map.Entry<String, Double>, Map<String, Double>> sourceNeighborhood(String  oid0,
                                                                             String  classifier,
                                                                             double  nmax,
                                                                             String  metric = 'JensenShannon',
                                                                             double  climit = 0.0,
                                                                             boolean allClasses = false) {
     return sourceNeighborhood('nmax':nmax,
                       'metric':metric,
                       'climit':climit,
                       'allClasses':allClasses,
                       oid0,
                       classifier)
    }

  /** The same method as {@link #sourceNeighborhood(Map, String, String},
    * appropriate for direct call from Java (instead of Groovy). */
  def Map<Map.Entry<String, Double>, Map<String, Double>> sourceNeighborhood(String oid0,
                                                                             String classifier,
                                                                             Map    args) {
    return sourceNeighborhood(args, oid0, classifier);
    }
        
  /** Give {@link Map} of other <em>source</em>s ordered
    * by distance to the specified <em>source</em> with respect
    * to weights to all (or selected) <em>SourceOfInterest</em> classes.
    * @param oid0          The <em>objectOd</em> of the <em>source</em>.
    * @param classifier    The classifier name to be used.
    * @param nmax          The number of closest <em>source</em>s to give.
    *                      If less then 1, the relative distance cutoff
    *                      (the larger cutoff means more selective, 0 means no selection). 
    *                      All are given, if missing.
    *                      Optional named parameter.
    * @param metric        The metric to use <tt>JensenShannon, Euclidean or Cosine</tt>.
    *                      Default: <tt>JensenShannon</tt>. Anyhing else gives random metric - for testing.
    *                      Optional named parameter.
    * @param climit        The low limit fir the classification ration of the evaluated source.
    *                      Default: <tt>0.0</tt>.
    *                      Optional named parameter.
    * @param allClasses    Whether to consider also classes not available in original source.
    *                      Default: <tt>false</tt>.
    *                      Optional named parameter.
    * @return              The distances to other sources, order by the distance. */
  def Map<Map.Entry<String, Double>, Map<String, Double>> sourceNeighborhood(Map    args = [:],
                                                                             String oid0,
                                                                             String classifier) {
    return sourceNeighborhood(args, oid0, classifier, null, null);
    }

  /** The same method as {@link #sourceNeighborhood(Map, String, String, ListMString>, List<String>},
    * appropriate for direct call from Java (instead of Groovy). */
  def Map<Map.Entry<String, Double>, Map<String, Double>> sourceNeighborhood(String      oid0,
                                                                             String      classifier,
                                                                             Set<String> oidS,
                                                                             Set<String> classes0,
                                                                             Map         args) {
    return sourceNeighborhood(args, oid0, classifier, oidS, classes);
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
    * @param nmax          The number of closest <em>source</em>s to give.
    *                      If less then 1, the relative distance cutoff
    *                      (the larger cutoff means more selective, 0 means no selection). 
    *                      All are given, if missing.
    *                      Optional named parameter.
    * @param metric        The metric to use <tt>JensenShannon, Euclidean or Cosine</tt>.
    *                      Default: <tt>JensenShannon</tt>. Anyhing else gives random metric - for testing.
    *                      Optional named parameter.
    * @param climit        The low limit for the classification ration of the evaluated source.
    *                      Default: <tt>0.0</tt>.
    *                      Optional named parameter.
    * @param allClasses    Whether to consider also classes not available in original source.
    *                      Default: <tt>false</tt>.
    * @return              The distances to other sources, order by the distance. */
  def Map<Map.Entry<String, Double>, Map<String, Double>> sourceNeighborhood(Map         args = [:],
                                                                             String      oid0,
                                                                             String      classifier,
                                                                             Set<String> oidS,
                                                                             Set<String> classes0) {
    def nmax       = args.nmax       ?: Integer.MAX_VALUE;
    def metric     = args.metric     ?: 1;
    def climit     = args.climit     ?: 0.0;
    def allClasses = args.allClasses ?: false;
    def cf = classifierWithFlavor(classifier);
    if (g().V().has('lbl', 'source').has('objectId', oid0).count().next() == 0) {
      log.info(oid0 + " has no registered neighborhood");
      return [:];
      }
    if (classes0 == null || classes0.isEmpty()) {
      classes0 = g().V().has('lbl', 'SoI'       ).
                         has('classifier', cf[0]).
                         has('flavor',     cf[1]).
                         values('cls'           ).
                         toSet();
      }
    def source0 = g().V().has('lbl',      'source').
                          has('objectId', oid0    ).
                          next();
    def m0 = [:];
    g().V(source0).inE().
                   as('e').
                   filter(and(outV().values('classifier').is(eq(cf[0])),
                              outV().values('flavor'    ).is(eq(cf[1])),
                              outV().values('cls'       ).is(within(classes0)))).                   
                   project('cls', 'w').
                   by(select('e').outV().values('cls')).
                   by(select('e').values('weight')).
                   each {it -> m0[it['cls']] = it['w']}
    log.info('calculating source distances from ' + oid0 + m0 + " using " + args);
    if (climit > 0.0) {
      m0.entrySet().removeIf(entry -> entry.getValue() < climit)
      }
    def classes
    if (allClasses) {
      classes = classes0
      }
    else {
      classes = [];
      for (entry : m0.entrySet()) {
        classes += [entry.getKey()];
        }
      log.info("\tsearching only in " + classes);
      }
    def distances = [:]
    def sources;
    if (oidS) {
      log.info("\tsearching only " + oidS);
      sources = g().V().has('lbl', 'source').
                        has('objectId', within(oidS));
      }
    else {
      // NOTE: Janus-all.jar doesn't allow some complex operations
      sources = g().V().has('lbl', 'SoI').
                        has('classifier', cf[0]).
                        has('flavor',     cf[1]).
                        has('cls',        within(classes)).
                        out().
                        has('lbl', 'source').
                        dedup()  
      }
    def distance
    def n = 0
    def t = System.currentTimeMillis()
    sources.each {s -> 
                  def oid = g().V(s).values('objectId').next();
                  def m = [:];
                  g().V(s).inE().
                           as('e').
                           filter(and(inV().values('objectId'   ).is(neq(oid0)),
                                      outV().values('classifier').is(eq(cf[0])),
                                      outV().values('flavor'    ).is(eq(cf[1])),
                                      outV().values('cls'       ).is(within(classes)))).
                           project('cls', 'w').
                           by(select('e').outV().values('cls')).
                           by(select('e').values('weight')).
                           each {it -> m[it['cls']] = it['w']}
                  if (climit > 0.0) {
                    m.entrySet().removeIf(entry -> entry.getValue() < climit)
                    }
                  def dist = sourceDistance(m0, m, allClasses, metric)
                  n++
                  distance = Map.entry(oid, dist)
                  distances[distance] = m
                  }
    t = System.currentTimeMillis() - t
    log.info('distance of ' + n + ' sources evaluated in ' + t / 1000 + ' s')
    return limitMapMap(distances, nmax)
    }
    
  /** Give distance (metric) between two classifier {@link Map}s.
    * @param m0            The first classifier {@link Map} cls to weight.
    * @param mx            The second classifier {@link Map} cls to weight.
    *                      Entries, not present also in m0, will be ignored.
    * @param allClasses    Whether to consider also classes not available in original source.
    *                      Default: <tt>false</tt>.
    * @param metric        The metric to use <tt>JensenShannon, Euclidean or Cosine</tt>.
    *                      Default: <tt>JensenShannon</tt>. Anyhing else gives random metric - for testing.
    *                      <ul>
    *                      <li>Jensen-Shannon:	Comparing probability distributions, Handles missing classes robustly</li>
    *                      <li>Euclidean:	    Interpreting probabilities as points in space, Sensitive to magnitude</li>
    *                      <li>Cosine:         Comparing class pattern rather than strength, 	Ignores magnitude of probabilities</li>
    *                      </ul>
    * @return              The distance between two {@link Map}s. <tt>0-1</tt>*/
  def double sourceDistance(Map<String, Double> m0,
                            Map<String, Double> mx,
                            boolean             allClasses,
                            String              metric = 'JensenShannon') {
    if (m0.isEmpty() && mx.isEmpty()) return 1.0 // or 0 ?
    if (m0.isEmpty() || mx.isEmpty()) return 1.0
    Set<String> keys = new HashSet<>(m0.keySet())
    keys.addAll(mx.keySet())
    double sum0 = m0.values().sum()
    double sumx = mx.values().sum()
    Map<String, Double> p = [:]
    Map<String, Double> q = [:]
    if (allClasses) { // normalise
      keys.each {k -> p[k] = m0.getOrDefault(k, 0.0) / sum0
                      q[k] = mx.getOrDefault(k, 0.0) / sumx}
      }
    else { // complete
      keys.each {k -> p[k] = m0.getOrDefault(k, 0.0)
                      q[k] = mx.getOrDefault(k, 0.0)}
      if (Math.abs(1.0 - sum0) > 0.000001) {
        def newkey = 'others0'
        keys.add(newkey)
        p[newkey] = 1.0 - sum0;
        q[newkey] = 0.0;
        }
      if (Math.abs(1.0 - sumx) > 0.000001) {
        def newkey = 'othersx'
        keys.add(newkey)
        p[newkey] = 0.0;
        q[newkey] = 1.0 - sumx;
        }
      }
    switch(metric) {
      case 'JensenShannon':
        return sourceDistanceJensenShannon(p, q, keys);
        break;
      case 'Euclidean':
        return sourceDistanceEuclidean(p, q, keys);
        break;
      case 'Cosine':
        return sourceDistanceCosine(p, q, keys);
        break;
      default:
        return _random.nextDouble();
        }    
    }
    
 /** Give Jensen Shannon distance (metric) between two classifier {@link Map}s.
    * (see <a href="https://en.wikipedia.org/wiki/Jensen%E2%80%93Shannon_divergence">Jensen–Shannon divergence</a>)
    * @param p             The first classifier {@link Map} cls to weight.
    * @param q             The second classifier {@link Map} cls to weight.
    * @param keys          Unity of keys for m0, mx.
    * @return              The distance between two {@link Map}s. <tt>0-1</tt>*/
  def double sourceDistanceJensenShannon(Map<String, Double> p,
                                         Map<String, Double> q,
                                         Set<String>         keys) {
    Map<String, Double> m = [:]
    keys.each {if (!p.containsKey(it)) {p[it] = 0.0}
               if (!q.containsKey(it)) {q[it] = 0.0}}
    keys.each {k -> m[k] = 0.5 * (p[k] + q[k])}
    def kl = {Map<String, Double> a, Map<String, Double> b ->
               double klDiv = 0.0
               a.each {k, v ->
                 if (v > 0 && b[k] > 0) {
                   klDiv += v * Math.log(v / b[k])
                   }
                 }
               return klDiv
               }
  double jsd = 0.5 * kl(p, m) + 0.5 * kl(q, m)
  return Math.sqrt(jsd)
  }                             
    
  /** Give Euclidean distance (metric) between two classifier {@link Map}s.
    * @param p             The first classifier {@link Map} cls to weight.
    * @param q             The second classifier {@link Map} cls to weight.
    * @param keys          Unity of keys for m0, mx.
    * @return              The distance between two {@link Map}s. <tt>0-1</tt>*/
  def double sourceDistanceEuclidean(Map<String, Double> p,
                                     Map<String, Double> q,
                                     Set<String>         keys) {
    double sumSq = 0.0
    keys.each {k ->
      sumSq += Math.pow(p[k] - q[k], 2)
      }   
   return Math.sqrt(sumSq) / Math.sqrt(2)
   }
    
  /** Give Cosine distance (metric) between two classifier {@link Map}s.
    * @param p             The first classifier {@link Map} cls to weight.
    * @param q             The second classifier {@link Map} cls to weight.
    * @param keys          Unity of keys for m0, mx.
    * @return              The distance between two {@link Map}s. <tt>0-1</tt>*/
  def double sourceDistanceCosine(Map<String, Double> p,
                                  Map<String, Double> q,
                                  Set<String>         keys) {
    double dot = 0.0
    double normp = 0.0
    double normq = 0.0
    keys.each {k ->
      dot += p[k] * q[k]
      normp += p[k] * p[k]
      normq += q[k] * q[k]
      }
    if (normp == 0 || normq == 0) return 1.0  // Max distance if one is zero vector
    double cosineSim = dot / (Math.sqrt(normp) * Math.sqrt(normq))
    return 1.0 - cosineSim
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
    * @param oid        The <em>source objectId</em>.
    * @param classifier The {@link Classifier} to be used.
    *                   Optional. If missing or <tt>null</tt>,
    *                   {@link Classifier}s will be used.
    * @return           The recorded classification calculated
    *                   by number of classified <em>alert</em>s. */
  def List<Map<String, String>> classification(String oid,
                                               String classifier = null) {
    def cf;
    if (classifier != null) {
      cf = classifierWithFlavor(classifier);
      }
    def classified = [];
    g().V().has('lbl', 'source').
            has('objectId', oid).
            inE().
            project('weight', 'classifier', 'flavor', 'class').
            by(values('weight')).
            by(outV().values('classifier')).
            by(outV().values('flavor')).
            by(outV().values('cls')).each {it -> if (classifier == null || (cf[0] == it.classifier && cf[1] == it.flavor)) {
                                                   classified += it;
                                                   }
            }
    return classified;
    }
   
  /** Give recorded classification. Recalculate classes from <tt>srcClassifier</tt>
    * to <tt>dstClassifier</tt>.
    * The truthfulness of the result depends on quality and overlap of used classifications.
    * @param oid The <em>source objectId</em>.
    * @param srcClassifier The classifier to be used for primary classification.
    * @param dstClassifier The classifier to be used to interpret the classification.
    * @param nmax          The number of classes to give.
    *                      If less then 1, the relative weight cutoff
    *                      (the larger cutoff means more selective, 0 means no selection). 
    *                      <tt>10</tt>, if missing.
    * @param check         Whether to check quality of ther reclassification.
    *                      It slows down the calculation and may not be available if
    *                      objectId is not classified in destination classification.
    *                      The deafult is <tt>false</tt>.
    * @return              The recorded classification calculated
    *                      by number of classified <em>alert</em>s. Normalized to 1. */
  def Map<String, Double> reclassification(String  oid,
                                           String  srcClassifier,
                                           String  dstClassifier,
                                           double  nmax  = 10,
                                           boolean check = false) {                        
    def classified = classification(oid, srcClassifier);
    def reclassified = [:];      
    def w;
    def cf = classifierWithFlavor(srcClassifier);
    classified.each {it -> if (it.classifier == cf[0] && it.flavor == cf[1]) {
                             w = reclassify(it.class, 'SoI', srcClassifier, dstClassifier);
                             w.each {cls, intersection -> if (reclassified[cls] == null) {
                                                            reclassified[cls] = 0;
                                                            }
                                                          reclassified[key] += intersection * it.weight;
                               }
                      }
      }
    double total = reclassified.values().sum()
    if (total != 0) {
      reclassified = reclassified.collectEntries {k, v -> [k, v / total]}
      }
    if (check) {
      def classifiedDst = classification(oid, dstClassifier);
      if (classifiedDst.isEmpty()) {
        log.warn('Cannot check quality')
        }
      else {
        def p = [:]
        def q = [:]
        classifiedDst.each{p[it.class] = it.weight}
        reclassified.each{ q[it.key]   = it.value }
        def quality = 1.0 - sourceDistance(p, q, true, 'JensenShannon')
        log.info('quality: ' + quality)
        }
      }
    return limitMap(reclassified, nmax)
    }   
    
  /** Give recorded classification. Recalculate classes from <tt>srcClassifier</tt>
    * to <tt>dstClassifier</tt> passing by <tt>midClassifier</tt>.
    * The truthfulness of the result depends on quality and overlap of used classifications.
    * @param oid The <em>source objectId</em>.
    * @param srcClassifier The classifier to be used for primary classification.
    * @param midClassifier The classifier to be used for intermediate classification.
    * @param dstClassifier The classifier to be used to interpret the classification.
    * @param nmax          The number of classes to give.
    *                      If less then 1, the relative weight cutoff
    *                      (the larger cutoff means more selective, 0 means no selection). 
    *                      <tt>10</tt>, if missing.
    * @param check         Whether to check quality of ther reclassification.
    *                      It slows down the calculation and may not be available if
    *                      objectId is not classified in destination classification.
    *                      The deafult is <tt>false</tt>.
    * @return              The recorded classification calculated
    *                      by number of classified <em>alert</em>s. Normalized to 1. */
  def Map<String, Double> reclassification(String  oid,
                                           String  srcClassifier,
                                           String  midClassifier,
                                           String  dstClassifier,
                                           double  nmax  = 10,
                                           boolean check = false) {                        
    def classified = classification(oid, srcClassifier);
    def reclassified = [:];      
    def w;
    def wMid;
    def cf = classifierWithFlavor(srcClassifier);
    def cg = classifierWithFlavor(midClassifier);
    classified.each {it -> if (it.classifier == cf[0] && it.flavor == cf[1]) {
                             wMid = reclassify(it.class, 'SoI', srcClassifier, midClassifier);
                             wMid.each {clsMid, intersectionMid -> w = reclassify(clsMid, 'SoI', midClassifier, dstClassifier);
                                            w.each {cls, intersection -> if (reclassified[cls] == null) {
                                                                           reclassified[cls] = 0;
                                                                           }
                                                                         reclassified[cls] += intersectionMid * intersection * it.weight;
                                              }
                               }
                      }
      }
    double total = reclassified.values().sum()
    if (total != 0) {
      reclassified = reclassified.collectEntries {k, v -> [k, v / total]}
      }
    if (check) {
      def classifiedDst = classification(oid, dstClassifier);
      if (classifiedDst.isEmpty()) {
        log.warn('Cannot check quality')
        }
      else {
        def p = [:]
        def q = [:]
        classifiedDst.each{p[it.class] = it.weight}
        reclassified.each{ q[it.key]   = it.value }
        def quality = 1.0 - sourceDistance(p, q, true, 'JensenShannon')
        log.info('quality: ' + quality)
        }
      }
    return limitMap(reclassified, nmax)
    }                            

  /** Limit {@link Map} based on its <tt>key.value</tt>.
    * @param map  The fill {@link Map}>
    * @param nmax  The number of closest <em>source</em>s to give.
    *              If less then 1, the relative distance cutoff
    *              (the larger cutoff means more selective, 0 means no selection). 
    * @return     The limited {@link Map}. */
  // TBD: rafactor with limitMap
  def Map<Map.Entry<String, Double>, Map<String, Double>> limitMapMap(Map<Map.Entry<String, Double>, Map<String, Double>> map,
                                                                      double                                              nmax) {
    if (nmax >= 1) {
      return map.entrySet().                        
                 sort{a, b -> a.key.value <=> b.key.value}.  
                 take((int)nmax).
                 collectEntries(new LinkedHashMap<>()) {entry -> [(entry.key): entry.value]}
      }
    else {
      def map1 = [:];
      def entries = map.entrySet().sort{a, b -> a.key.value <=> b.key.value}
      for (int i = 0; i < entries.size(); i++) {
        if (i < 2) {
          map1[entries[i].key] = entries[i].value
          }
        else {
          def v0 = entries[i - 2].key.value
          def v1 = entries[i - 1].key.value
          def v2 = entries[i    ].key.value
          if (v1 != v2 && v1 != v0) {
            def ratio = (v1 - v0) / (v2 - v1)
            if (ratio < nmax) {
              break
              }
            }
          map1[entries[i].key] = entries[i].value
          }
        }
      return map1
      }
    }
     
  /** Limit {@link Map} based on its <tt>value</tt>.
    * @param map  The fill {@link Map}>
    * @param nmax  The number of closest <em>source</em>s to give.
    *              If less then 1, the relative distance cutoff
    *              (the larger cutoff means more selective, 0 means no selection). 
    * @return     The limited {@link Map}. */
  def Map<String, Double> limitMap(Map<String, Double> map,
                                   double              nmax) {
    if (nmax >= 1) {
      return map.sort{-it.value}.  
                 take((int)nmax)
      }
    else {
      def map1 = [:];
      def entries = map.entrySet().sort{-it.value}
      for (int i = 0; i < entries.size(); i++) {
        if (i < 2) {
          map1[entries[i].key] = entries[i].value
          }
        else {
          def v0 = entries[i - 2].value
          def v1 = entries[i - 1].value
          def v2 = entries[i    ].value
          if (v1 != v2 && v1 != v0) {
            def ratio = (v1 - v0) / (v2 - v1)
            if (ratio < nmax) {
              break
              }
            }
          map1[entries[i].key] = entries[i].value
          }
        }
      return map1
      }
    }
   
  /** Give all overlaps.
    * Using accumulated data in graph.
    * @param lbl        The label of {@link Vertex}es to use for overlap search.
    *                   Optional named parameter.
    * @param classifier The name of classifier to use for overlap search.
    *                   Optional named parameter. 
    * @param outputCSV  The filename for CSV file with overlaps.
    *                   Optional named parameter.
    * @return           The overlaps. */
  def Map<String, Double> overlaps(Map args) {
    def lbl        = args?.lbl;
    def classifier = args?.classifier;
    def outputCSV  = args?.outputCSV;
    def overlaps = [:];
    def cf = classifierWithFlavor(classifier);
    g().E().has('lbl', 'overlaps').
            order().
            by('intersection', asc).
            project('xlbl', 'xclassifier', 'xflavor', 'xcls', 'ylbl', 'yclassifier', 'yflavor', 'ycls', 'intersection').
            by(inV().values('lbl')).
            by(inV().values('classifier')).
            by(inV().values('flavor')).
            by(inV().values('cls')).
            by(outV().values('lbl')).
            by(outV().values('classifier')).
            by(outV().values('flavor')).
            by(outV().values('cls')).
            by(values('intersection')).
            each {v -> 
                  if ((lbl        == null ||  v['xlbl'].equals(lbl) || v['ylbl'].equals(lbl)) &&
                      (classifier == null || (v['xclassifier'].equals(cf[0]) &&
                                              v['yclassifier'].equals(cf[0]) &&
                                              v['xflavor'    ].equals(cf[1]) && 
                                              v['yflavor'    ].equals(cf[1])))) {
                    overlaps[v['xlbl'] + ':' + v['xclassifier'] + ':' + v['xflavor'] + ':' + v['xcls'] + ' * ' + v['ylbl'] + ':' + v['yclassifier'] + ':' + v['yflavor'] + ':' + v['ycls']] = v['intersection'];
                    }
                  };
    overlaps = overlaps.sort{-it.value};
    if (outputCSV != null) {
      def csv = "type1,classifier1,flavor1,class1,type2,classifier2,flavor2,class2,overlap\n";
      overlaps.each{o -> csv += o.getKey().replaceAll(" \\* ", ",").replaceAll(":", ",") + "," + o.getValue() + "\n"};
      new File(outputCSV).text = csv;
      return null;
      }
    return overlaps;
    }
    
  /** Give classification from another {@link Classifier}.
    * Using accumulated data in graph.
    * @param cls           The class in the source classifier. 
    * @param lbl           The label of collection {@link Vertex}es. 
    * @param srcClassifier The name of classifier of the source (known) class.
    * @param dstClassifier The name of classifier of the destination (required) class.
    * @return              The new classification. */
  def Map<String, Double> reclassify(String cls,
                                     String lbl,
                                     String srcClassifier,
                                     String dstClassifier) {
    def classification = [:];
    def srcCf = classifierWithFlavor(srcClassifier);
    def dstCf = classifierWithFlavor(dstClassifier);
    g().E().has('lbl', 'overlaps').
            order().
            by('intersection', asc).
            project('xlbl', 'xclassifier', 'xflavor', 'xcls', 'ylbl', 'yclassifier', 'yflavor', 'ycls', 'intersection').
            by(inV().values('lbl')).
            by(inV().values('classifier')).
            by(inV().values('flavor')).
            by(inV().values('cls')).
            by(outV().values('lbl')).
            by(outV().values('classifier')).
            by(outV().values('flavor')).
            by(outV().values('cls')).
            by(values('intersection')).
            each {v -> 
                  if (v['xlbl'       ].equals(lbl     ) &&
                      v['ylbl'       ].equals(lbl     ) &&
                      v['xcls'       ].equals(cls     ) &&
                      v['xclassifier'].equals(srcCf[0]) &&
                      v['yclassifier'].equals(dstCf[0]) &&
                      v['xflavor'    ].equals(srcCf[1]) &&
                      v['yflavor'    ].equals(dstCf[1])) {
                    classification[v['ycls']] = v['intersection'];
                    }
                  };
    classification = classification.sort{-it.value};
    return classification;
    }
    
  /** Export all <em/>SoI</em>
    * {@link Vertex}es with connecting <em>overlaps</em> {@link Edge}s
    * into <em>GraphML</em> file.
    * @param fn The full filename of the output <em>GraphML</em> file. */
  def exportSoI(String       fn) {  
    g().V().has('lbl', 'SoI').
            outE().
            has('lbl', 'overlaps').
            subgraph('x').
            cap('x').
            next().
            io(IoCore.graphml()).
            writeGraph(fn);
    }
    
  /** Give classifier as an array of classifiar name and flavor.
    * @param classifier The classifier. Can contain flavor after <em>=</em> sign.
    * @return           The array of classifier and flavor (empty string if not specified). */
  def String[] classifierWithFlavor(String classifier) {
    if (classifier == null) {
      return new String[] {null, ''};
      }
    if (!classifier.contains('=')) {
      return new String[]{classifier, ''};
      }
    return classifier.split('=');
    }
    
  def Random _random = new Random()
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkGremlinRecipiesGT.class);

  }
