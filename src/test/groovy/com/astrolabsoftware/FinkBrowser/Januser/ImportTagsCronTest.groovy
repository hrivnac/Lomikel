package com.astrolabsoftware.FinkBrowser.Januser

/** Exercise the importer scan/commit body with offline fake clients and graph. */
final class ImportTagsCronTest {
  static void main(String[] args) {
    def source = new File('src/work/CC/importTags.groovy').text
    def body = source.substring(source.indexOf('client.startScan('))
    assert body.contains('client.startScan(')
    def good = run(body, false)
    assert good.error == null : good.error
    assert good.events.contains('add:NewTag') : "scheduled scan was skipped: ${good.events}"
    assert good.events.contains('commit')
    assert good.events.contains('close')

    def failed = run(body, true)
    assert failed.error != null : 'scanner failure must propagate to the cron wrapper'
    assert !failed.events.contains('commit') : 'do not report failed import as committed'
    assert failed.events.contains('close') : 'close client even on scan failure'
    println 'ImportTagsCronTest: OK'
  }

  private static Map run(String body, boolean fail) {
    def events = Collections.synchronizedList([])
    def pending = new java.util.concurrent.atomic.AtomicBoolean(false)
    def failure = new java.util.concurrent.atomic.AtomicReference<Throwable>()
    def queue = new java.util.concurrent.ConcurrentLinkedQueue<Map>()
    def client = new Expando()
    client.startScan = { Object... ignored ->
      pending.set(true)
      Thread.start {
        Thread.sleep(20)
        if (fail) failure.set(new IllegalStateException('injected scan failure'))
        else queue.add(['key:key': '61000_object1'])
        pending.set(false)
      }
    }
    client.scanning = { false } // the old check races before the worker starts
    client.scanPending = { pending.get() }
    client.scanFailure = { failure.get() }
    client.size = { queue.size() }
    client.poll = { def row = queue.poll(); [(row['key:key']): row] }
    client.stop = { events << 'stop' }
    client.close = { events << 'close' }
    def traversal = new Expando()
    traversal.addV = { label -> events << "add:${label}"; traversal }
    traversal.property = { name, value -> traversal }
    traversal.iterate = { -> }
    def gr = new Expando(g: { traversal }, commit: { events << 'commit' })
    def timer = new Expando(report: { text -> false })
    def script = new GroovyShell(new Binding(client: client, gr: gr, timer: timer,
      cls: 'tag', delay: 1, now: System.currentTimeMillis())).parse(body)
    def error = new java.util.concurrent.atomic.AtomicReference<Throwable>()
    Thread worker = new Thread({
      try { script.run() }
      catch (Throwable t) { error.set(t) }
    } as Runnable)
    worker.daemon = true
    worker.start()
    worker.join(1000)
    assert !worker.alive : 'importer did not terminate after scan completion'
    [events: events.collect { it.toString() }, error: error.get()]
  }
}
