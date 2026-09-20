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
      }
    finally {
      graph.close()
      }
    println 'JanuserGroovyRegressionTest: OK'
    }

  private static final class TestRecipes implements FinkGremlinRecipiesGT {
    def source
    def g() { source }
    def graph() { source.graph }
    def commit() { source.tx().commit() }
    }
  }
