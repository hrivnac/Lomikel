package com.astrolabsoftware.FinkBrowser.Januser

import org.janusgraph.core.JanusGraphFactory

final class JanuserGroovyRegressionTest {

  static void main(String[] args) {
    def graph = JanusGraphFactory.build().set('storage.backend', 'inmemory').open()
    try {
      def source = graph.traversal()
      source.addV('object').property('lbl', 'object').property('importDate', 'test-date').iterate()
      source.tx().commit()
      def recipes = new TestRecipes(source: source)
      def failure = new java.util.concurrent.atomic.AtomicReference<Throwable>()
      Thread worker = new Thread({
        try {
          recipes.drop_by_date('test-date', 1, 0)
          }
        catch (Throwable t) {
          failure.set(t)
          }
        } as Runnable)
      worker.daemon = true
      worker.start()
      worker.join(2000)
      assert !worker.alive : 'drop_by_date must terminate after deleting the final batch'
      if (failure.get() != null) {
        throw failure.get()
        }
      assert source.V().has('importDate', 'test-date').count().next() == 0L

      source.addV('left').property('lbl', 'left').property('id', 'L').iterate()
      source.addV('right').property('lbl', 'right').property('id', 'R').iterate()
      recipes.get_or_create_edge('left', 'id', 'L', 'right', 'id', 'R', 'links').iterate()
      recipes.get_or_create_edge('left', 'id', 'L', 'right', 'id', 'R', 'links').iterate()
      assert source.E().hasLabel('links').has('lbl', 'links').count().next() == 1L :
             'get_or_create_edge must create one edge and reuse it'
      assert source.V().has('lbl', 'left').has('id', 'L').out('links').
                    has('lbl', 'right').has('id', 'R').count().next() == 1L :
             'get_or_create_edge must preserve the requested direction and endpoints'

      def marker = 'januser.datalink.regression'
      System.clearProperty(marker)
      try {
        recipes.executeHBaseDataLinkQuery(new FakeHBaseClient(),
          "System.setProperty('${marker}', 'executed')")
        assert false : 'arbitrary DataLink scripts must be rejected'
        }
      catch (IllegalArgumentException expected) {
        assert System.getProperty(marker) == null : 'rejected DataLink scripts must not execute'
        }

      def hbase = new FakeHBaseClient()
      def candidate = recipes.executeHBaseDataLinkQuery(hbase,
        "return client.scan('object_1', null, '*', 0, true, true)")
      assert candidate == [object_1: ['i:value': 'ok']]
      assert hbase.lastScan == ['object_1', null, '*', 0L, 0L, true, true]

      def cutout = recipes.executeHBaseDataLinkQuery(hbase,
        "x=client.scan('object_1', null, 'b:cutoutScience_stampData', 0, false, false).get('object_1').get('b:cutoutScience_stampData');y=client.repository().get(x);java.util.Base64.getEncoder().encodeToString(y)")
      assert cutout == 'Zml0cw==' : 'supported cutout DataLinks must retain Base64 behavior'

      try {
        recipes.executeGraphDataLinkQuery(source,
          "java.lang.System.setProperty('${marker}', 'executed')")
        assert false : 'arbitrary graph DataLink scripts must be rejected'
        }
      catch (IllegalArgumentException expected) {
        assert System.getProperty(marker) == null : 'rejected graph DataLink scripts must not execute'
        }

      def hbaseLink = source.addV('datalink').property('lbl', 'datalink').
                            property('technology', 'HBase').property('url', 'host:1:table:schema').
                            property('query', "return client.scan('object_1', null, '*', 0, true, true)").next()
      recipes.hbaseClient = new FakeHBaseClient()
      assert recipes.getDataLink(hbaseLink).contains('object_1')
      assert recipes.hbaseClient.connected : 'HBase DataLink must connect its selected client'
      assert recipes.hbaseClient.closed : 'HBase DataLink must close its client after success'

      def rejectedLink = source.addV('datalink').property('lbl', 'datalink').
                             property('technology', 'HBase').property('url', 'host:1:table:schema').
                             property('query', "System.setProperty('${marker}', 'executed')").next()
      recipes.hbaseClient = new FakeHBaseClient()
      recipes.getDataLink(rejectedLink)
      assert recipes.hbaseClient.closed : 'HBase DataLink must close after query rejection'
      assert System.getProperty(marker) == null

      def failingClient = new FakeHBaseClient(failConnect: true)
      recipes.hbaseClient = failingClient
      recipes.getDataLink(hbaseLink)
      assert failingClient.closed : 'HBase DataLink must close after connect failure'

      def target = new FakeTargetGraph()
      target.source.addV('target').property(org.apache.tinkerpop.gremlin.structure.T.id, 'target-id').iterate()
      recipes.targetGraph = target
      def graphLink = source.addV('datalink').property('lbl', 'datalink').
                           property('technology', 'Graph').property('url', 'inmemory:host:1:table').
                           property('query', 'g.V().limit(1)').next()
      def graphResult = recipes.getDataLink(graphLink)
      assert graphResult.contains('target-id') : 'Graph DataLink must query the URL-selected graph'
      assert target.source.closed : 'Graph DataLink must close its traversal source'
      assert target.closed : 'Graph DataLink must close its target graph'

      def rejectedTarget = new FakeTargetGraph()
      recipes.targetGraph = rejectedTarget
      def rejectedGraphLink = source.addV('datalink').property('lbl', 'datalink').
                                   property('technology', 'Graph').property('url', 'inmemory:host:1:table').
                                   property('query', "System.setProperty('${marker}', 'executed')").next()
      recipes.getDataLink(rejectedGraphLink)
      assert rejectedTarget.source.closed : 'Graph DataLink must close traversal after rejection'
      assert rejectedTarget.closed : 'Graph DataLink must close graph after rejection'
      assert System.getProperty(marker) == null
      }
    finally {
      graph.close()
      }
    println 'JanuserGroovyRegressionTest: OK'
    }

  private static final class TestRecipes implements FinkGremlinRecipiesGT {
    def source
    def hbaseClient
    def targetGraph
    def g() { source }
    def graph() { source.graph }
    def commit() { source.tx().commit() }
    def createHBaseDataLinkClient(String hostname, String port) { hbaseClient }
    def openDataLinkGraph(String backend, String hostname, String port, String table) { targetGraph }
    }

  private static final class FakeHBaseClient {
    def lastScan
    boolean connected
    boolean closed
    boolean failConnect

    def connect(String table, String schema) {
      if (failConnect) throw new IllegalStateException('intentional connect failure')
      connected = true
      }

    void close() { closed = true }

    def scan(String key, String search, String filter, long start, long stop,
             boolean ifkey, boolean iftime) {
      lastScan = [key, search, filter, start, stop, ifkey, iftime]
      if (filter.startsWith('b:cutout')) {
        return [(key): [(filter): 'blob-id']]
        }
      return [(key): ['i:value': 'ok']]
      }

    def repository() {
      return new FakeRepository()
      }
    }

  private static final class FakeRepository {
    byte[] get(String id) {
      return id == 'blob-id' ? 'fits'.bytes : null
      }
    }

  private static final class FakeTargetGraph {
    def graph = org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph.open()
    def source = new RecordingTraversalSource(graph)
    boolean closed
    def traversal() { source }
    void close() {
      closed = true
      graph.close()
      }
    }

  private static final class RecordingTraversalSource extends org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource {
    boolean closed
    RecordingTraversalSource(org.apache.tinkerpop.gremlin.structure.Graph graph) { super(graph) }
    @Override
    void close() {
      closed = true
      super.close()
      }
    }
  }
