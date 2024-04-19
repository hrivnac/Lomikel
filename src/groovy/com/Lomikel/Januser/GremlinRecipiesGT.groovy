package com.Lomikel.Januser;

import com.Lomikel.HBaser.HBaseClient
import com.Lomikel.Phoenixer.PhoenixProxyClient

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import org.apache.tinkerpop.gremlin.structure.Graph;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.property;
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
import static org.apache.tinkerpop.gremlin.process.traversal.Scope.local;

// JanusGraph
import org.janusgraph.core.JanusGraphFactory;
// Groovy
import groovy.sql.Sql

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>GremlinRecipiesGT</code> provides various recipies to handle
  * and modify Gremlin Graphs.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
trait GremlinRecipiesGT {
                   
  /** Give full statistics of {@link Vertex}es and {@link Edge}es in the database.
    * @return The full statistics of {@link Vertex}es and {@link Edge}es in
    *         the database. */
  def String stat() {
    def v = '\nV: ' + g().V().group().by(values('lbl')).by(count()).toSet().toString();
    def e = '\nE: ' + g().E().group().by(values('lbl')).by(count()).toSet().toString();
    return v + e;
    }
    
  /** Get (if exists) or create (if doesn't exist) {@link Vertex}.
    * @param lbl   The {@link Vertex} label.
    * @param name  The name of the {@link Vertex} property to check or set.
    * @param value The value of the {@link Vertex} property to check or set.
    * @return      The found or created {@link Vertex}. */
  def GraphTraversal get_or_create(String lbl,
                                   String name,
                                   String value) {
    return g().V().has('lbl', lbl).
                   has(name, value).
                   fold().
                   coalesce(unfold(), addV(lbl).
                                      property('lbl', lbl).
                                      property(name, value));
    }
          
    
  /** Get (if exists) or create (if doesn't exist) {@link Edge}.
    * @param lbl   The {@link Edge} label.
    * @param name  The name of the {@link Edge} property to check or set.
    * @param value The value of the {@link Edge} property to check or set.
    * @return      The found or created {@link Edge}. */
  def GraphTraversal get_or_create_edge(String lbl,
                                        String name,
                                        String value) {
    return g().V().has('lbl', lbl1).
                   has(name1, value1).
                   as('v').
               V().has('lbl', lbl2).
                   has(name2, value2).
               coalesce(__.inE(edge).where(outV().as('v')), addE(edge).from('v'));
    }
                    
  /** Drop {@link Vertex}es by groups.
    * @param label The label of {@link Vertex}es to drop.
    * @param n     The number of {@link Vertex}es to drop for each commit. */
  def dropV(String label,
            int    n) {
    def m = g().V().has('lbl', label).count().next();
    while (m > 0) {
      println('' + m + ' ' + label + 's to drop');
      g().V().has('lbl', label).limit(n).drop().iterate();
      graph().traversal().tx().commit();
      m -= n;
      }
    }
    
  /** Drop {@link Edge}s by groups.
    * @param label The label of {@link Edge}s to drop.
    * @param n     The number of {@link Edge}s to drop for each commit. */
  def dropE(String label,
            int    n) {
    def m = g().E().has('lbl', label).count().next();
    while (m > 0) {
      println('' + m + ' ' + label + 's to drop');
      g().E().has('lbl', label).limit(n).drop().iterate();
      graph().traversal().tx().commit();
      m -= n;
      }
    }

  /** Calculate deviations of {@link Vertex}es.
    * @param lbl           The label for {@link Vertex}es to evaluate.
    * @param variableNames The names of variables to analyse. 
    * @return              The {Link Map} with results as <tt>variableName - deviation</tt>. */
  def Map standardDeviationV(String       lbl,
                             List<String> variableNames) {
    def sdMap = [:];
    variableNames.stream().
                  each {v ->
                        g().V().has('lbl', lbl).
                                values(v).
                                fold().
                                as(v).
                                mean(local).
                                as('mean').
                                select(v).
                                unfold().
                                math('(_-mean)^2').
                                mean().
                                math('sqrt(_)').
                                map {sd -> sdMap += [(v):sd]};
                        }
    return sdMap;
    }

  /** Calculate deviations of {@link Edge}s.
    * @param lbl           The label for {@link Edge}s to evaluate.
    * @param variableNames The names of variables to analyse. 
    * @return              The {Link Map} with results as <tt>variableName - deviation</tt>. */
  def Map standardDeviationE(String       lbl,
                             List<String> variableNames) {
    def sdMap = [:];
    variableNames.stream().
                  each {v ->
                        g().E().has('lbl', lbl).
                                values(v).
                                fold().
                                as(v).
                                mean(local).
                                as('mean').
                                select(v).
                                unfold().
                                math('(_-mean)^2').
                                mean().
                                math('sqrt(_)').
                                map {sd -> sdMap += [(v):sd]};
                        }
    return sdMap;
    }
   
  /** Create a new {@link Graph} (on the default storage).
    * @param myName The name of the created {@link Graph}.
    *               If <tt>null</tt>, the graph will be only created in memory.
    * @return       The created {@link Graph}. */
  def Graph myGraph(String myName = null) {
    def graph0
    def g0
    if (myName == null) {
      graph0 = JanusGraphFactory.build().
                                 set('storage.backend', 'inmemory').
                                 open();
      }
    else {
      graph0 = JanusGraphFactory.build().
                                 set('storage.backend',     config.getString('storage.backend')).
                                 set('storage.hostname',    config.getString('storage.hostname')).
                                 set('storage.port',        config.getString('storage.port')).
                                 set('storage.hbase.table', myName).
                                 open();
      }
    return graph0;
    }
    
  /** Give data associated with <em>datalink</em> {@link Vertex}.
    * The <em>datalink</em>s can be created like this:
    * <pre>
    * w = g.addV().property('lbl', 'datalink').property('technology', 'Phoenix').property('url', 'jdbc:phoenix:ithdp2101.cern.ch:2181'      ).property('query', "select * from AEI.CANONICAL_0 where project = 'mc16_13TeV'").next()
    * w = g.addV().property('lbl', 'datalink').property('technology', 'Graph'  ).property('url', 'hbase:188.184.87.217:8182:janusgraph'     ).property('query', "g.V().limit(1)").next()
    * w = g.addV().property('lbl', 'datalink').property('technology', 'HBase'  ).property('url', '134.158.74.54:2183:ztf:schema'            ).property('query', "client.setLimit(10); return client.scan(null, null, null, 0, false, false)").next()
    * </pre>
    * @param v The <em>datalink</em> {@link Vertex}.
    * @param q The special (external) database query to be used in place of the standard one. Optiponal.
    * @return The <em>datalink</em> content. */
    def String getDataLink(v, // TBD: type ?
                           String q = null) {
    def url   = v.values('url'  ).next();
    def query;
    if (q != null) {
      query = q;
      }
    else if (v.values('query').hasNext()) {
      query = v.values('query').next();
      }
    else {
      return 'no Query';
      }
    try {
      switch (v.values('technology').next()) {
        case 'HBase':
          def (hostname, port, table, schema) = url.split(':'); // 134.158.74.54:2181:ztf:schema_0.7.0_0.3.8
          def client = new HBaseClient(hostname, port);
          client.connect(table, schema);
          return Eval.me('client', client, query);
          break
        case 'Graph':
          def (backend, hostname, port, table) = url.split(':'); // hbase:188.184.87.217:8182:janusgraph
          def graph = JanusGraphFactory.build().
                                        set('storage.backend',     backend ).
                                        set('storage.hostname',    hostname).
                                        set('storage.port',        port    ).
                                        set('storage.hbase.table', table   ).
                                        open();
          return Eval.me('g', g(), query);
          break
        case 'Phoenix':
          return Sql.newInstance(url, 'org.apache.phoenix.jdbc.PhoenixDriver').
                     rows(query);
          break
        default:
          return 'DataLink ' + v + ' unknown';
          }
        }
      catch (Exception e) {
        return 'DataLink ' + v + ' not found';
        }
      }
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(GremlinRecipiesGT.class);
    
  }