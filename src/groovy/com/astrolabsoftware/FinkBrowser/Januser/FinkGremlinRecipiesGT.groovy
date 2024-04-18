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
  public Map<String, Double> sourceNeighborhood(String   oid0,
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
  public Map<String, Double> sourceNeighborhood(String       oid0,
                                                List<String> oidS,
                                                List<String> classes0,
                                                int          nmax) {
    def source0 = g().V().has('lbl', 'source').has('objectId', oid0).next();
    def m0 = [:];
    g().V(source0).inE().
                   project('cls', 'w').
                   by(outV().values('cls')).
                   by(values('weight')).each {it ->
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

  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkGremlinRecipiesG.class);

  }