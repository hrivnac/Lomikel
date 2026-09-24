package com.astrolabsoftware.FinkBrowser.Januser

/** Offline control-flow test: only the remote setup is replaced with fakes. */
final class FillOColCronTest {
  static void main(String[] args) {
    def source = new File('src/work/IJCLab/fillOCol.groovy').text
    def marker = '// The scan can start asynchronously'
    assert source.contains(marker)
    // Run the real scan/classification/completion body, without connecting to HBase/JanusGraph.
    def body = source.substring(source.indexOf(marker)).
      replace('Classifier classifier', 'Object classifier').
      replace('NotifierURL.notifyExecution(', 'notifier.notifyExecution(').
      replace('Info.release()', "'release'")
    assert body.contains('notifier.notifyExecution(')

    def empty = run(body, [], null)
    assert empty.events == ['start', 'stop', 'close', 'correlate', 'notify'] :
      "empty, completed scan must terminate and report success: ${empty.events}"

    def failedScan = run(body, [], null, 'failed')
    assert failedScan.error != null : 'worker failure must fail the job'
    assert !failedScan.events.contains('correlate')
    assert !failedScan.events.contains('notify')
    def neverStarted = run(body, [], null, 'pending')
    assert neverStarted.error != null : 'startup timeout must fail the job'
    assert !neverStarted.events.contains('correlate')
    assert !neverStarted.events.contains('notify')

    def good = run(body, ['first', 'second'], null)
    assert good.events == ['start', 'classify:first', 'report', 'classify:second',
                           'report', 'stop', 'close', 'correlate', 'notify'] : good.events

    def failed = run(body, ['first', 'bad', 'third'], 'bad')
    assert failed.error != null : 'classification failure must fail the job'
    assert failed.events.contains('classify:first') : "already registered objects remain committed: ${failed.events}, ${failed.error}"
    assert !failed.events.contains('classify:third') : 'do not mutate later objects after failure'
    assert !failed.events.contains('correlate') : 'do not generate correlations on incomplete input'
    assert !failed.events.contains('notify') : 'do not report a successful completion'
    println 'FillOColCronTest: OK'
  }

  private static Map run(String body, List<String> ids, String failId,
                         String scanOutcome = 'success') {
    def events = Collections.synchronizedList([])
    def queue = new ArrayDeque(ids.collect { ['i:objectId': it] })
    def client = new Expando()
    client.scanning = { false }
    client.scanPending = { scanOutcome == 'pending' }
    client.scanFailure = { scanOutcome == 'failed' ? new IllegalStateException('scan failed') : null }
    client.size = { queue.size() }
    client.stop = { events << 'stop' }
    client.close = { events << 'close' }
    client.poll = { def row = queue.removeFirst(); [(row['i:objectId']): row] }
    def gr = new Expando()
    gr.classifySource = { classifier, id ->
      events << "classify:${id}"
      if (id == failId) throw new IllegalStateException('injected classification failure')
    }
    gr.generateCorrelations = { classifiers -> events << 'correlate' }
    def timer = new Expando(start: { events << 'start' },
                            report: { events << 'report' }, info: { value -> value })
    def log = new Expando(error: { message, error -> events << 'error' })
    def notifier = new Expando(notifyExecution: { a, b, c, d -> events << 'notify' })
    def shell = new GroovyShell(new Binding(client: client, gr: gr, timer: timer,
      classifiers: ['FINK'], log: log, notifier: notifier, delay: 1, startupWaitMillis: 10))
    def script = shell.parse(body)
    script.metaClass.println = { Object ignored -> } // quiet on the defective waiting loop
    def error = new java.util.concurrent.atomic.AtomicReference<Throwable>()
    Thread worker = new Thread({
      try { script.run() }
      catch (Throwable t) { error.set(t) }
    } as Runnable)
    worker.daemon = true
    worker.start()
    worker.join(1000)
    assert !worker.alive : 'completed empty scan must not wait forever'
    return [events: events.collect { it.toString() }, error: error.get()]
  }
}
