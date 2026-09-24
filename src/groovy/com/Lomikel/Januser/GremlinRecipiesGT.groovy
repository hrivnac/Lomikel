package com.Lomikel.Januser;

import com.Lomikel.HBaser.HBaseClient

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
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
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.addE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inE;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.outV;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV;
import static org.apache.tinkerpop.gremlin.process.traversal.P.within;
import static org.apache.tinkerpop.gremlin.process.traversal.Scope.local;

// JanusGraph
import org.janusgraph.core.JanusGraphFactory;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** Adds generic Groovy traversal helpers to a {@link GremlinRecipies} host.
  *
  * <p>The trait does not own a traversal source. Calls to {@code g()} and
  * {@code commit()} resolve against the implementing recipe. Traversal-building
  * helpers can operate on a remote source. {@link #getDataLink(Object, String)}
  * closes the temporary resources it creates. The lower-level
  * {@code createHBaseDataLinkClient} and {@code openDataLinkGraph} factories,
  * and {@link #myGraph(String)}, instead return caller-owned resources.</p>
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
          
    
  /** Get (if it exists) or create an {@link Edge} between two vertices.
    * @param lbl1   The outgoing vertex label mirrored in {@code lbl}.
    * @param name1  The outgoing vertex identity-property name.
    * @param value1 The outgoing vertex identity-property value.
    * @param lbl2   The incoming vertex label mirrored in {@code lbl}.
    * @param name2  The incoming vertex identity-property name.
    * @param value2 The incoming vertex identity-property value.
    * @param edge   The native edge label, also copied to {@code lbl}.
    * @return The found or created {@link Edge} traversal. */
  def GraphTraversal get_or_create_edge(String lbl1,
                                        String name1,
                                        String value1,
                                        String lbl2,
                                        String name2,
                                        String value2,
                                        String edge) {
    return g().V().has('lbl', lbl1).
                   has(name1, value1).
                   as('fromVertex').
               V().has('lbl', lbl2).
                   has(name2, value2).
               coalesce(inE(edge).where(outV().as('fromVertex')),
                        addE(edge).from('fromVertex').property('lbl', edge));
    }

  /** Obsolete incomplete signature retained only for source compatibility.
    * @deprecated Use {@link #get_or_create_edge(String, String, String,
    * String, String, String, String)} with both endpoints and the edge label. */
  @Deprecated
  def GraphTraversal get_or_create_edge(String lbl,
                                        String name,
                                        String value) {
    throw new UnsupportedOperationException('Both edge endpoints and the edge label are required');
    }
                    
  /** Drop {@link Vertex}es by groups.
    * @param label    The label of {@link Vertex}es to drop.
    * @param n        The number of {@link Vertex}es to drop for each commit.
    * @param attName  The name of an attribute to check. Optional. 
    * @param attValue The value of an attribute to check. Optional. */
  def dropV(String label,
            int    n,
            String attName  = null,
            String attValue = null) {
    if (n <= 0) {
      throw new IllegalArgumentException('Batch size must be positive')
      }
    while (true) {
      def batch;
      if (attName == null) {
        batch = g().V().has('lbl', label)
                   .limit(n)
                   .id()
                   .toList();
        }
      else {
        batch = g().V().has('lbl', label)
                   .has(attName, attValue)
                   .limit(n)
                   .id()
                   .toList();
        }
      if (batch.isEmpty()) {
        break;
        }
      println('' + batch.size() + ' ' + label + 's to drop');
      g().V(batch.toArray()).drop().iterate();
      commit();
      }
    }
    
  /** Drop {@link Edge}s by groups.
    * @param label    The label of {@link Edge}s to drop.
    * @param n        The number of {@link Edge}s to drop for each commit.
    * @param attName  The name of an attribute to check. Optional. 
    * @param attValue The value of an attribute to check. Optional. */
  def dropE(String label,
            int    n,
            String attName  = null,
            String attValue = null) {
    if (n <= 0) {
      throw new IllegalArgumentException('Batch size must be positive')
      }
    while (true) {
      def batch;
      if (attName == null) {
        batch = g().E().has('lbl', label)
                   .limit(n)
                   .id()
                   .toList();
        }
      else {
        batch = g().E().has('lbl', label)
                   .has(attName, attValue)
                   .limit(n)
                   .id()
                   .toList();
        }
      if (batch.isEmpty()) {
        break;
        }
      println('' + batch.size() + ' ' + label + 's to drop');
      g().E(batch.toArray()).drop().iterate();
      commit();
      }
    }

  /** Calculate deviations of {@link Vertex}es.
    * @param lbl           The label for {@link Vertex}es to evaluate.
    * @param variableNames The names of variables to analyse. 
    * @return              The {@link Map} with results as
    *                      {@code variableName - deviation}. */
  def Map standardDeviationV(String       lbl,
                             List<String> variableNames) {
    def sdMap = [:];
    variableNames.stream().
                  each {v ->
                        def x = g().V().has('lbl', lbl).
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
                                        next();
                        sdMap[v] = x;
                        }
    return sdMap;
    }

  /** Calculate deviations of {@link Edge}s.
    * @param lbl           The label for {@link Edge}s to evaluate.
    * @param variableNames The names of variables to analyse. 
    * @return              The {@link Map} with results as
    *                      {@code variableName - deviation}. */
  def Map standardDeviationE(String       lbl,
                             List<String> variableNames) {
    def sdMap = [:];
    variableNames.stream().
                  each {v ->
                        def x = g().E().has('lbl', lbl).
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
                                        next();
                        sdMap[v] = x;
                        }
    return sdMap;
    }
   
  /** Create a new embedded {@link Graph} independent of the recipe source.
    * @param myName The name of the created {@link Graph}.
    *               If {@code null}, the graph is created in memory; otherwise
    *               the recipe instance must expose a {@code config} property
    *               with {@code getString} values for the storage backend,
    *               hostname, and port.
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
    
  /** Execute one of the legacy HBase DataLink forms generated by Lomikel.
    * Arbitrary Groovy is intentionally rejected. */
  def executeHBaseDataLinkQuery(client,
                                String query) {
    def scan = query =~ /^\s*return\s+client\.scan\('([^'\\]*)',\s*null,\s*'\*',\s*0,\s*true,\s*true\)\s*;?\s*$/
    if (scan.matches()) {
      return client.scan(scan.group(1), null, '*', 0, 0, true, true)
      }

    def cutout = query =~ /^\s*x\s*=\s*client\.scan\('([^'\\]*)',\s*null,\s*'([^'\\]*)',\s*0,\s*false,\s*false\)\.get\('([^'\\]*)'\)\.get\('([^'\\]*)'\)\s*;\s*y\s*=\s*client\.repository\(\)\.get\(x\)\s*;\s*java\.util\.Base64\.getEncoder\(\)\.encodeToString\(y\)\s*;?\s*$/
    if (cutout.matches()) {
      def key = cutout.group(1)
      def column = cutout.group(2)
      if (key != cutout.group(3) || column != cutout.group(4)) {
        throw new IllegalArgumentException('Inconsistent HBase DataLink parameters')
        }
      def rows = client.scan(key, null, column, 0, 0, false, false)
      def reference = rows?.get(key)?.get(column)
      def bytes = client.repository().get(reference)
      return java.util.Base64.getEncoder().encodeToString(bytes)
      }

    throw new IllegalArgumentException('Unsupported HBase DataLink query')
    }

  /** Execute the only supported legacy Graph DataLink form.
    * Arbitrary Groovy is intentionally rejected. */
  def executeGraphDataLinkQuery(GraphTraversalSource source,
                                String               query) {
    def limitQuery = query =~ /^\s*g\.V\(\)\.limit\((\d+)\)\s*;?\s*$/
    if (!limitQuery.matches()) {
      throw new IllegalArgumentException('Unsupported Graph DataLink query')
      }
    long limit = Long.parseLong(limitQuery.group(1))
    return source.V().limit(limit)
    }

  /** Create an HBase DataLink client. Isolated for lifecycle testing. */
  def createHBaseDataLinkClient(String hostname,
                                String port) {
    return new HBaseClient(hostname, port)
    }

  /** Open the graph selected by a Graph DataLink URL. */
  def openDataLinkGraph(String backend,
                        String hostname,
                        String port,
                        String table) {
    return JanusGraphFactory.build().
                             set('storage.backend',     backend ).
                             set('storage.hostname',    hostname).
                             set('storage.port',        port    ).
                             set('storage.hbase.table', table   ).
                             open()
    }

  /** Give data associated with <em>datalink</em> {@link Vertex}.
    * The <em>datalink</em>s can be created like this:
    * <pre>
    * w = g.addV().property('lbl', 'datalink').property('technology', 'Graph').property('url', 'hbase:storage-host:2181:janusgraph').property('query', "g.V().limit(1)").next()
    * w = g.addV().property('lbl', 'datalink').property('technology', 'HBase').property('url', 'hbase-host:2181:table:schema').property('query', "return client.scan('object_1', null, '*', 0, true, true)").next()
    * </pre>
    * @param v The <em>datalink</em> {@link Vertex}.
    * @param q The optional external-database query used instead of the stored one.
    * @return The <em>datalink</em> content. */
    // Kept dynamically typed for source compatibility with existing Groovy callers.
    def String getDataLink(v,
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
          def (hostname, port, table, schema) = url.split(':');
          def client
          try {
            client = createHBaseDataLinkClient(hostname, port)
            client.connect(table, schema)
            return executeHBaseDataLinkQuery(client, query)
            }
          finally {
            if (client != null) {
              try {
                client.close()
                }
              catch (Exception closeFailure) {
                log.warn('Cannot close HBase DataLink client', closeFailure)
                }
              }
            }
        case 'Graph':
          def (backend, hostname, port, table) = url.split(':');
          def targetGraph
          def targetSource
          try {
            targetGraph = openDataLinkGraph(backend, hostname, port, table)
            targetSource = targetGraph.traversal()
            return executeGraphDataLinkQuery(targetSource, query).toList()
            }
          finally {
            Exception closeFailure = null
            if (targetSource != null) {
              try {
                targetSource.close()
                }
              catch (Exception e) {
                closeFailure = e
                }
              }
            if (targetGraph != null) {
              try {
                targetGraph.close()
                }
              catch (Exception e) {
                if (closeFailure == null) {
                  closeFailure = e
                  }
                else {
                  closeFailure.addSuppressed(e)
                  }
                }
              }
            if (closeFailure != null) {
              throw closeFailure
              }
            }
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
