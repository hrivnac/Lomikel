package com.astrolabsoftware.FinkBrowser.Januser

import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import java.sql.Timestamp

/** Offline regression for classifier scope in the actual processTags body. */
final class ProcessTagsCronTest {
  static void main(String[] args) {
    def source = new File('src/work/CC/processTags.groovy').text
    def imports = source.substring(source.indexOf('// TinkerPop'), source.indexOf('// SQL'))
    def body = source.substring(source.indexOf('getOrCreateObject ='))
                     .split('// ----------------------------------------------------------\n//  mark processed NewTag vertices', 2)[0]
    [false, true].each { reverse -> run(imports + body, reverse) }
    println 'ProcessTagsCronTest: OK'
  }

  private static void run(String source, boolean reverse) {
    def graph = TinkerGraph.open()
    def g = graph.traversal()
    def object = g.addV('object').property('lbl', 'object')
                  .property('objectId', 'object1').next()
    def fink = ocol(g, 'A', 'LSST', 'FINK')
    def tag = ocol(g, 'A', 'ANY', 'TAG')
    def tagOnly = ocol(g, 'B', 'ANY', 'TAG')
    def edges = [
      { member(fink, object, [1.0d]) },
      { member(tag, object, [10.0d]) },
      { member(tagOnly, object, [20.0d]) }
    ]
    if (reverse) edges = edges.reverse()
    edges.each { it() }
    def timer = new Expando(start: { -> }, report: { -> false })
    def gr = new Expando(commit: { -> })
    def binding = new Binding(g: g, gr: gr, timer: timer,
      grouped: [object1: [A: [tagIds: [], mjds: [2.0d]],
                         C: [tagIds: [], mjds: [3.0d]]]],
      defaultSurvey: 'LSST', defaultClassifier: 'FINK', defaultFlavor: '',
      jobImportDate: new Timestamp(0))
    new GroovyShell(binding).evaluate(source)
    assert instances(g, fink, object) == [1.0d, 2.0d] :
      'FINK membership must merge only FINK observations'
    assert instances(g, tag, object) == [10.0d] :
      'TAG membership must not be modified'
    assert instances(g, tagOnly, object) == [20.0d] :
      'other TAG classes must not be copied into FINK'
    assert !g.V().has('lbl', 'OCol').has('cls', 'B')
              .has('classifier', 'FINK').hasNext() :
      'unrelated TAG-only class must not produce a FINK membership'
    def newFink = g.V().has('lbl', 'OCol').has('cls', 'C')
                   .has('survey', 'LSST').has('classifier', 'FINK').next()
    assert instances(g, newFink, object) == [3.0d]
    double finkWeight = g.V(fink.id(), newFink.id()).outE('deepcontains')
      .values('weight').toList().sum(0.0d) as double
    assert Math.abs(finkWeight - 1.0d) < 1e-9d
    assert g.V(tag).outE('deepcontains').values('weight').next() == 1.0d
    for (vertex in [object, fink, tag, tagOnly, newFink]) {
      assert vertex.label() == vertex.property('lbl').value()
    }
    assert g.E().has('lbl', 'deepcontains').toList()
            .every { it.label() == it.property('lbl').value() }
    graph.close()
  }

  private static def ocol(g, String cls, String survey, String classifier) {
    g.addV('OCol').property('lbl', 'OCol').property('cls', cls)
      .property('survey', survey).property('classifier', classifier)
      .property('flavor', '').next()
  }

  private static void member(ocol, object, List<Double> mjds) {
    ocol.addEdge('deepcontains', object, 'lbl', 'deepcontains',
      'instances', mjds, 'weights', mjds.collect { 1.0d }, 'weight', 1.0d)
  }

  private static List<Double> instances(g, ocol, object) {
    g.V(ocol).outE('deepcontains').where(
      org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV().hasId(object.id()))
      .values('instances').next().collect { it as Double }
  }
}
