package com.astrolabsoftware.FinkBrowser.Januser

import org.janusgraph.core.JanusGraphFactory
import org.janusgraph.core.attribute.Geoshape
import org.apache.tinkerpop.gremlin.process.traversal.Traversal
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.AndStep
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.OrStep
import org.apache.tinkerpop.gremlin.process.traversal.step.filter.TraversalFilterStep
import org.apache.tinkerpop.gremlin.process.traversal.step.map.ProjectStep
import org.apache.tinkerpop.gremlin.process.traversal.strategy.AbstractTraversalStrategy
import org.apache.tinkerpop.gremlin.process.traversal.strategy.verification.VerificationException
import org.apache.tinkerpop.gremlin.process.traversal.TraversalStrategy.VerificationStrategy
import org.apache.tinkerpop.gremlin.structure.T
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph

final class JanuserGroovyRegressionTest {

  static void main(String[] args) {
    def graph = JanusGraphFactory.build().set('storage.backend', 'inmemory').open()
    try {
      def source = graph.traversal()
      source.addV('object').property('lbl', 'object').property('importDate', 'test-date').iterate()
      source.tx().commit()
      def recipes = new TestRecipes(source: source)
      source.addV('alert').property('marker', 'geo-inside-1').
             property('direction', Geoshape.point(0.0d, 0.0d)).property('jd', 5.0d).iterate()
      source.addV('alert').property('marker', 'geo-inside-2').
             property('direction', Geoshape.point(0.1d, 0.1d)).property('jd', 6.0d).iterate()
      source.addV('alert').property('marker', 'geo-date-boundary').
             property('direction', Geoshape.point(0.0d, 0.0d)).property('jd', 10.0d).iterate()
      source.addV('alert').property('marker', 'geo-outside').
             property('direction', Geoshape.point(10.0d, 10.0d)).property('jd', 5.0d).iterate()
      source.tx().commit()
      recipes.gCount = 0
      assert recipes.geosearch(180.0, 0.0, 1.0, 0.0, 10.0, 10).
                     values('marker').toSet() == ['geo-inside-1', 'geo-inside-2'] as Set
      assert recipes.gCount == 1 : 'geosearch must execute through one graph traversal source'
      assert recipes.geosearch(180.0, 0.0, 1.0, 0.0, 10.0, 1).count().next() == 1
      assert recipes.geosearch(180.0, 0.0, 1.0, 0.0, 10.0, 0).count().next() == 0
      assert (recipes.classifierWithFlavor(null) as List) == [null, '']
      assert (recipes.classifierWithFlavor('classifier') as List) == ['classifier', '']
      assert (recipes.classifierWithFlavor('classifier=') as List) == ['classifier', '']
      assert (recipes.classifierWithFlavor('classifier=flavor') as List) == ['classifier', 'flavor']
      for (String malformed : ['', '=flavor', 'a=b=c']) {
        try {
          recipes.classifierWithFlavor(malformed)
          assert false : "malformed classifier must be rejected: ${malformed}"
          }
        catch (IllegalArgumentException expected) {
          // Expected validation failure.
          }
        }
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

      5.times { source.addV('batchVertex').property('lbl', 'batchVertex').iterate() }
      source.tx().commit()
      recipes.commitCount = 0
      def invalidBatchFailure = new java.util.concurrent.atomic.AtomicReference<Throwable>()
      Thread invalidBatch = new Thread({
        try {
          recipes.dropV('batchVertex', 0)
          }
        catch (Throwable t) {
          invalidBatchFailure.set(t)
          }
        } as Runnable)
      invalidBatch.daemon = true
      invalidBatch.start()
      invalidBatch.join(2000)
      assert !invalidBatch.alive : 'zero-sized dropV batches must be rejected without looping'
      assert invalidBatchFailure.get() instanceof IllegalArgumentException
      assert source.V().has('lbl', 'batchVertex').count().next() == 5L :
             'invalid dropV batches must not mutate the graph'
      recipes.dropV('batchVertex', 2)
      assert source.V().has('lbl', 'batchVertex').count().next() == 0L
      assert recipes.commitCount == 3 :
             'dropV must commit each actual batch through the recipe abstraction'

      6.times {
        def edgeFrom = source.addV('batchEndpoint').next()
        def edgeTo = source.addV('batchEndpoint').next()
        edgeFrom.addEdge('batchEdge', edgeTo, 'lbl', 'batchEdge')
        }
      source.tx().commit()
      recipes.commitCount = 0
      try {
        recipes.dropE('batchEdge', -1)
        assert false : 'negative dropE batches must be rejected'
        }
      catch (IllegalArgumentException expected) {
        assert source.E().has('lbl', 'batchEdge').count().next() == 6L :
               'invalid dropE batches must not mutate the graph'
        }
      recipes.dropE('batchEdge', 4)
      assert source.E().has('lbl', 'batchEdge').count().next() == 0L
      assert recipes.commitCount == 2 :
             'dropE must commit each actual batch through the recipe abstraction'

      def membershipSource = source.addV('object').property('lbl', 'object').
                                    property('objectId', 'membership-source').next()
      def membershipCandidate = source.addV('object').property('lbl', 'object').
                                       property('objectId', 'membership-candidate').next()
      def membershipClass = source.addV('OCol').property('lbl', 'OCol').
                                  property('survey', 'ZTF').property('classifier', 'TAG').
                                  property('flavor', '').property('cls', 'forged').next()
      membershipClass.addEdge('audit', membershipSource, 'lbl', 'deepcontains', 'weight', 1.0d)
      membershipClass.addEdge('audit', membershipCandidate, 'lbl', 'deepcontains', 'weight', 1.0d)
      assert recipes.classification('membership-source', 'TAG').isEmpty() :
             'classification must ignore non-deepcontains edges with forged lbl properties'
      assert recipes.objectNeighborhood([:], 'membership-source', 'TAG', null, null).isEmpty() :
             'neighborhoods must ignore non-deepcontains membership edges'

      def classifiedObject = source.addV('object').property('lbl', 'object').
                                    property('objectId', 'classified-source').next()
      def selectedOne = source.addV('OCol').property('lbl', 'OCol').
                              property('classifier', 'TAG').property('flavor', 'f').property('cls', 'one').next()
      def selectedTwo = source.addV('OCol').property('lbl', 'OCol').
                              property('classifier', 'TAG').property('flavor', 'f').property('cls', 'two').next()
      def otherClassifier = source.addV('OCol').property('lbl', 'OCol').
                                  property('classifier', 'OTHER').property('flavor', '').property('cls', 'other').next()
      def otherFlavor = source.addV('OCol').property('lbl', 'OCol').
                              property('classifier', 'TAG').property('flavor', 'other').property('cls', 'flavor').next()
      def missingClass = source.addV('OCol').property('lbl', 'OCol').
                               property('classifier', 'TAG').property('flavor', 'f').next()
      selectedOne.addEdge('deepcontains', classifiedObject, 'lbl', 'deepcontains', 'weight', 1.0d)
      selectedTwo.addEdge('deepcontains', classifiedObject, 'lbl', 'deepcontains', 'weight', 2.0d)
      otherClassifier.addEdge('deepcontains', classifiedObject, 'lbl', 'deepcontains', 'weight', 100.0d)
      otherFlavor.addEdge('deepcontains', classifiedObject, 'lbl', 'deepcontains', 'weight', 50.0d)
      missingClass.addEdge('deepcontains', classifiedObject, 'lbl', 'deepcontains', 'weight', 9.0d)
      selectedOne.addEdge('audit', classifiedObject, 'lbl', 'deepcontains', 'weight', 200.0d)
      source.tx().commit()
      recipes.source = source.withStrategies(new RequireClassificationFilterBeforeProjectionStrategy())
      def filteredClassification = recipes.classification('classified-source', 'TAG=f')
      assert filteredClassification as Set == [
        [weight: 1.0d, classifier: 'TAG', flavor: 'f', class: 'one'],
        [weight: 2.0d, classifier: 'TAG', flavor: 'f', class: 'two'],
        [weight: 9.0d, classifier: 'TAG', flavor: 'f']
        ] as Set : 'classification must preserve selected productive values, including a partial malformed row'
      recipes.source = source
      assert recipes.classification('classified-source') as Set == [
        [weight: 1.0d, classifier: 'TAG',   flavor: 'f',     class: 'one'],
        [weight: 2.0d, classifier: 'TAG',   flavor: 'f',     class: 'two'],
        [weight: 100.0d, classifier: 'OTHER', flavor: '',    class: 'other'],
        [weight: 50.0d, classifier: 'TAG',  flavor: 'other', class: 'flavor'],
        [weight: 9.0d, classifier: 'TAG',   flavor: 'f']
        ] as Set : 'unfiltered classification must retain every productive membership row'

      def reclassObject = source.addV('object').property('lbl', 'object').
                                property('objectId', 'reclass-object').next()
      def sourceClass = source.addV('OCol').property('lbl', 'OCol').
                              property('survey', 'ZTF').property('classifier', 'SRC').
                              property('flavor', '').property('cls', 'source').next()
      def destinationClass = source.addV('OCol').property('lbl', 'OCol').
                                   property('survey', 'ZTF').property('classifier', 'DST').
                                   property('flavor', '').property('cls', 'destination').next()
      sourceClass.addEdge('deepcontains', reclassObject,
                          'lbl', 'deepcontains', 'weight', 1.0d)
      destinationClass.addEdge('audit', sourceClass,
                               'lbl', 'overlaps', 'intersection', 1.0d)
      assert recipes.reclassification('reclass-object', 'SRC', 'DST', 10, false).isEmpty() :
             'reclassification must ignore non-overlaps edges with forged lbl properties'
      assert recipes.overlaps().isEmpty() :
             'overlap listings must ignore non-overlaps edges with forged lbl properties'

      def overlapA = source.addV('OCol').property('lbl', 'OCol').
                            property('classifier', 'TAG').property('flavor', 'f').property('cls', 'A').next()
      def overlapB = source.addV('OCol').property('lbl', 'OCol').
                            property('classifier', 'TAG').property('flavor', 'f').property('cls', 'B').next()
      def overlapC = source.addV('OCol').property('lbl', 'Special').
                            property('classifier', 'TAG').property('flavor', 'f').property('cls', 'C').next()
      def overlapOtherA = source.addV('OCol').property('lbl', 'OCol').
                                 property('classifier', 'OTHER').property('flavor', '').property('cls', 'X').next()
      def overlapOtherB = source.addV('OCol').property('lbl', 'OCol').
                                 property('classifier', 'OTHER').property('flavor', '').property('cls', 'Y').next()
      overlapA.addEdge('overlaps', overlapB, 'lbl', 'overlaps', 'intersection', 1.0d)
      overlapA.addEdge('overlaps', overlapC, 'lbl', 'overlaps', 'intersection', 5.0d)
      overlapA.addEdge('overlaps', overlapB, 'lbl', 'overlaps', 'intersection', 7.0d)
      overlapOtherA.addEdge('overlaps', overlapOtherB, 'lbl', 'overlaps', 'intersection', 100.0d)
      overlapA.addEdge('overlaps', overlapOtherA, 'lbl', 'overlaps', 'intersection', 90.0d)
      overlapA.addEdge('audit', overlapB, 'lbl', 'overlaps', 'intersection', 200.0d)
      source.tx().commit()
      recipes.source = source.withStrategies(new RequireOverlapFilterBeforeProjectionStrategy())
      def filteredOverlaps = recipes.overlaps([classifier: 'TAG=f'])
      assert filteredOverlaps == [
        'OCol:TAG:f:B * OCol:TAG:f:A': 7.0d,
        'Special:TAG:f:C * OCol:TAG:f:A': 5.0d
        ] : 'overlaps must preserve duplicate-key overwrite and descending-value semantics'
      assert filteredOverlaps.keySet().toList() == [
        'OCol:TAG:f:B * OCol:TAG:f:A',
        'Special:TAG:f:C * OCol:TAG:f:A'
        ] : 'overlaps must preserve final map order'
      assert recipes.overlaps([lbl: 'Special']) == [
        'Special:TAG:f:C * OCol:TAG:f:A': 5.0d
        ] : 'overlaps must preserve endpoint-OR label filtering'
      overlapA.addEdge('overlaps', overlapC, 'lbl', 'overlaps')
      source.tx().commit()
      def malformedKey = 'Special:TAG:f:C * OCol:TAG:f:A'
      def malformedByLabel = recipes.overlaps([lbl: 'Special'])
      assert malformedByLabel.containsKey(malformedKey) && malformedByLabel[malformedKey] == null :
             'overlaps must preserve ordered duplicate overwrite by a missing intersection'
      def malformedCombined = recipes.overlaps([lbl: 'Special', classifier: 'TAG=f'])
      assert malformedCombined.containsKey(malformedKey) && malformedCombined[malformedKey] == null :
             'combined overlap filters must preserve malformed duplicate overwrite semantics'
      recipes.source = source

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
    assertClassificationMultiPropertyEquivalence()
    println 'JanuserGroovyRegressionTest: OK'
    }

  private static void assertClassificationMultiPropertyEquivalence() {
    def graph = TinkerGraph.open()
    def source = graph.traversal()
    try {
      def object = source.addV('object').property('lbl', 'object').
                         property('objectId', 'multi-property-object').next()
      def firstMismatch = graph.addVertex(T.label, 'OCol', 'lbl', 'OCol', 'cls', 'excluded')
      firstMismatch.property(VertexProperty.Cardinality.list, 'classifier', 'OTHER')
      firstMismatch.property(VertexProperty.Cardinality.list, 'classifier', 'TAG')
      firstMismatch.property(VertexProperty.Cardinality.list, 'flavor', 'f')
      firstMismatch.addEdge('deepcontains', object, 'lbl', 'deepcontains', 'weight', 1.0d)
      def firstMatch = graph.addVertex(T.label, 'OCol', 'lbl', 'OCol', 'cls', 'included')
      firstMatch.property(VertexProperty.Cardinality.list, 'classifier', 'TAG')
      firstMatch.property(VertexProperty.Cardinality.list, 'classifier', 'OTHER')
      firstMatch.property(VertexProperty.Cardinality.list, 'flavor', 'f')
      firstMatch.addEdge('deepcontains', object, 'lbl', 'deepcontains', 'weight', 2.0d)
      def recipes = new TestRecipes(source: source)
      def baseline = []
      source.V().has('lbl', 'object').has('objectId', 'multi-property-object').inE('deepcontains').
             project('weight', 'classifier', 'flavor', 'class').
             by(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.values('weight')).
             by(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.outV().values('classifier')).
             by(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.outV().values('flavor')).
             by(org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.outV().values('cls')).
             each { row ->
               if (row.classifier == 'TAG' && row.flavor == 'f') baseline += row
               }
      assert recipes.classification('multi-property-object', 'TAG=f') == baseline :
             'filtering must use the same first multi-property values that projection exposes'
      }
    finally {
      source.close()
      graph.close()
      }
    }

  private static final class TestRecipes implements FinkGremlinRecipiesGT {
    def source
    def hbaseClient
    def targetGraph
    int commitCount
    int gCount
    def g() {
      gCount++
      source
      }
    def getGeoshape() { Geoshape }
    def inside(def lower, def upper) {
      org.apache.tinkerpop.gremlin.process.traversal.P.inside(lower, upper)
      }
    def graph() { source.graph }
    def commit() {
      commitCount++
      source.tx().commit()
      }
    def createHBaseDataLinkClient(String hostname, String port) { hbaseClient }
    def openDataLinkGraph(String backend, String hostname, String port, String table) { targetGraph }
    }

  private static final class RequireOverlapFilterBeforeProjectionStrategy
      extends AbstractTraversalStrategy<VerificationStrategy>
      implements VerificationStrategy {

    @Override
    void apply(Traversal.Admin<?, ?> traversal) {
      int project = traversal.steps.findIndexOf { it instanceof ProjectStep }
      if (project >= 0 && !traversal.steps.subList(0, project).
                                  any { it instanceof AndStep || it instanceof OrStep }) {
        throw new VerificationException('overlap endpoint filters must precede projection', traversal)
        }
      }
    }

  private static final class RequireClassificationFilterBeforeProjectionStrategy
      extends AbstractTraversalStrategy<VerificationStrategy>
      implements VerificationStrategy {

    @Override
    void apply(Traversal.Admin<?, ?> traversal) {
      int project = traversal.steps.findIndexOf { step ->
        step instanceof ProjectStep &&
        step.projectKeys == ['weight', 'classifier', 'flavor', 'class']
        }
      if (project >= 0 && !traversal.steps.subList(0, project).
                                  any { it instanceof TraversalFilterStep }) {
        throw new VerificationException('classification endpoint filter must precede projection', traversal)
        }
      }
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
