package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Januser.ModifyingGremlinClient;
import com.Lomikel.Januser.GremlinRecipies;
import com.Lomikel.Januser.GremlinRecipiesGT;
import com.Lomikel.Phoenixer.PhoenixProxyClient;
import com.Lomikel.HBaser.HBaseClient;

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
    def dist = ang * 6371.0087714 * Math.PI / 180;
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
    * @param oid0      The <em>objectOd</em> of the <em>source</em>.
    * @param oidS      An array of <em>source</em> objectIds to only avaluated.
    *                  If null, all <em>source</em>s will be evaluated.
    * @param classes0A An array of <em>SourceOfInterest</em> classes to be
    *                  used in comparison.
    *                  All <em>SourceOfInterest</em> classes of thr specified
    *                  <em>source</em> will be used if <tt>null</tt>.
    * @param nmax      The number of closest <em>source</em>s to give.
    * @return          The distances to other sources, order by the distance. */
  def  Map<String, Double> sourceNeighborhood(String   oid0,
                                              String[] oidS,
                                              String[] classes0,
                                              int      nmax) {
    return sourceNeighborhood(oid0, Arrays.asList(oidS), Arrays.asList(classes0), nmax);
    }
    
  /** Give {@link Map} of other <em>source</em>s ordered
    * by distance to the specified <em>source</em> with respect
    * to weights to all (or selected) <em>SourceOfInterest</em> classes.
    * @param oid0      The <em>objectOd</em> of the <em>source</em>.
    * @param oidS      A {@link List} of <em>source</em> objectIds to only avaluated.
    *                  If null, all <em>source</em>s will be evaluated.
    * @param classes0A A {@link List} of <em>SourceOfInterest</em> classes to be
    *                  used in comparison.
    *                  All <em>SourceOfInterest</em> classes of thr specified
    *                  <em>source</em> will be used if <tt>null</tt>.
    * @param nmax      The number of closest <em>source</em>s to give.
    * @return          The distances to other sources, order by the distance. */
  def Map<String, Double> sourceNeighborhood(String       oid0,
                                             List<String> oidS,
                                             List<String> classes0,
                                             int          nmax) {
    def source0 = g().V().has('lbl', 'source').has('objectId', oid0).next();
    def m0 = [:];
    g().V(source0).inE().
                   project('cls', 'w').
                   by(outV().values('cls')).
                   by(values('weight')).
                   each {it ->
                         if (classes0 == null || it['cls'] in classes0) {
                           m0[it['cls']] = it['w'];
                           }
                         }
    def classes = [];
    for (entry : m0.entrySet()) {
      classes += [entry.getKey()];
      }
    def n0 = m0.size();
    for (entry : m0.entrySet()) {
      m0[entry.getKey()] = entry.getValue() / n0;
      }
    log.info('calculating source distance from ' + oid0 + ' wrt ' + classes + " ...");
    def distances = [:]
    def sources;
    if (oidS) {
      log.info("\tsearching only " + oidS);
      sources = g().V().has('lbl', 'source').has('objectId', within(oidS));
      }
    else {
      sources = g().V().has('lbl', 'source');   
      }
    sources.each {s -> 
                  def oid = g().V(s).values('objectId').next();
                  def m = [:];
                  g().V(s).inE().
                           project('cls', 'w').
                           by(outV().values('cls')).
                           by(values('weight')).each {it ->
                                                      if (it['cls'] in classes) {
                                                        m[it['cls']] = it['w'];
                                                        }
                                                      }
                  def n = m.size();
                  for (entry : m.entrySet()) {
                    m[entry.getKey()] = entry.getValue() / n;
                    }
                  def dist = 0;
                  def w01;
                  def w02;
                  def wx1;
                  def wx2;
                  def w0;
                  def wx;
                  for (entry1 : m0.entrySet()) {
                    for (entry2 : m0.entrySet()) {
                      if (entry1.getKey() > entry2.getKey()) {
                        w01 = entry1.getValue();
                        w02 = entry2.getValue();
                        wx1 = m[entry1.getKey()] == null ? 0 : m[entry1.getKey()];
                        wx2 = m[entry2.getKey()] == null ? 0 : m[entry2.getKey()];
                        w0 = (w01 + w02) == 0 ? 0 : Math.abs(w01 - w02) / (w01 + w02);
                        wx = (wx1 + wx2) == 0 ? 0 : Math.abs(wx1 - wx2) / (wx1 + wx2);
                        dist += Math.pow(w0 - wx, 2);
                        }
                      }
                    }
                  dist = Math.sqrt(dist) / n / n;
                  if (dist > 0) {
                    distances[oid] = dist;
                    }                                
                  }   
    return distances.sort{it.value}.take(nmax);
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

  /** Give recorded classification.
    * @param oid The <em>source objectId</em>.
    * @return    The recorded classification calculated
    *            by number of classified <em>alert</em>s. */
  // TBD: handle missing oids
  def List classification(String oid) {
    return g().V().has('lbl', 'source').
                   has('objectId', oid).
                   inE().
                   project('weight', 'class').
                   by(values('weight')).
                   by(outV().has('lbl', 'SourcesOfInterest').values('cls')).
                   toList();
    }
    
  /** Give all overlaps.
    * @param lbl The label of {@link Vertex}es to use for overlap search.
    *            Optional. 
    * @return    The overlaps. */
  def Map overlaps(String lbl = null) {
    def overlaps = [:];
    g().E().has('lbl', 'overlaps').
            order().
            by('intersection', asc).
            project('xlbl', 'xcls', 'ylbl', 'ycls', 'intersection').
            by(inV().values('lbl')).
            by(inV().values('cls')).
            by(outV().values('lbl')).
            by(outV().values('cls')).
            by(values('intersection')).
            each {v -> 
                  if (v['xlbl'].equals(lbl) || v['ylbl'].equals(lbl)) {
                    overlaps[v['xlbl'] + ':' + v['xcls'] + ' * ' + v['ylbl'] + ':' + v['ycls']] = v['intersection'];
                    }
                  };
    return overlaps.sort{it.value};
    }
    
  /** Export all <em>AlertsOfInterest</em> and <em?SourcesOfInterest</em>
    * {@link Vertex}es with connecting <em>overlaps</em> {@link Edge}s
    * into <em>GraphML</em> file.
    * @param fn The full filename of the output <em>GraphML</em> file. */
  def exportAoISoI(String fn) {  
    g().V().has('lbl', within('AlertsOfInterest', 'SourcesOfInterest')).
            outE().
            subgraph('x').
            cap('x').
            next().
            io(IoCore.graphml()).
            writeGraph(fn);
    }
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkGremlinRecipiesGT.class);

  }