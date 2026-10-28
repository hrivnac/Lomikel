package com.Lomikel.HBaserNew;

import com.Lomikel.Utils.LomikelException;

// HBase
import org.apache.hadoop.hbase.client.Scan;

// HealPix
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// org.json
import org.json.JSONObject;

// Java
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>AsynchHBaseClient</code> provides {@link HBaseClient} scanning asynchronously.
  * Only some methods are available for asynchronous processing.
  * Only one asynchronous scanning {@link Thread} is allowed.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class AsynchHBaseClient extends    HBaseClient
                               implements Runnable {
  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port.
    * @throws LomikelException If anything goes wrong. */
  public AsynchHBaseClient(String zookeepers,
                           String clientPort) throws LomikelException {
    super(zookeepers, clientPort);
    }

  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port.
    * @throws LomikelException If anything goes wrong. */
  public AsynchHBaseClient(String zookeepers,
                           int    clientPort) throws LomikelException {
    super(zookeepers, clientPort);
    }

  /** Create and connect to HBase.
    * @param url The HBase url.
    * @throws LomikelException If anything goes wrong. */
  public AsynchHBaseClient(String url) throws LomikelException {
    super(url);
    }

  @Override
  // BUG: prevent other calls during scan(...)
  public void run() {
    try {
      while (true) {
        if (_doscan) {
          log.info("Starting asynchronous scan");
          log.debug("Scan args: key={}, search={}, filter={}, start={}, stop={}, ifkey={}, iftime={}",
                    _scanKey, _scanSearch, _scanFilter, _scanStart, _scanStop, _scanIfkey, _scanIftime);
          _scanning = true;
          try {
            setProcessor(_processor);
            scan(_scanKey,
                 _scanSearch,
                 _scanFilter,
                 _scanStart,
                 _scanStop,
                 _scanIfkey,
                 _scanIftime);
            }
          finally {
            setProcessor(null);
            }
          _doscan   = false;
          _scanning = false;
          }
        Thread.sleep(_loopWait);
        }
      }
    catch (InterruptedException e) {
      if (_doscan || _scanning) {
        _scanFailure = e;
        }
      _doscan = false;
      _scanning = false;
      log.info("Asynchronous scan interrupted");
      }
    catch (Exception e) {
      _scanFailure = e;
      _doscan = false;
      _scanning = false;
      log.error("Asynchronous scan failed", e);
      }
    }

  @Override
  protected void handleScanIOException(IOException failure) {
    throw new UncheckedIOException("Asynchronous HBase scan failed", failure);
    }

  /** Start scan assynchronously.
    * @param key     The row key. Disables other search terms.
    *                It can be <tt>null</tt>.
    * @param search  The search terms as <tt>family:column:value,...</tt>.
    *                Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                among <tt>exact,prefix,substring,regex</tt>.
    *                The default for key is <tt>prefix</tt>,
    *                the default for columns is <tt>substring</tt>.
    *                The randomiser can be added with <tt>random:random:chance</tt>.
    *                It can be <tt>null</tt>.
    *                All searches are executed as prefix searches.
    * @param filter  The names of required values as <tt>family:column,...</tt>.
    *                <tt>*</tt> = all.
    * @param start   The time period start timestamp in <tt>ms</tt>.
    *                <tt>0</tt> means since the beginning.
    * @param stop    The time period stop timestamp in <tt>ms</tt>.
    *                <tt>0</tt> means till now.
    * @param ifkey   Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime  Whether give also entries timestamps (as <tt>key:time</tt>). */
  public synchronized void startScan(String  key,
                        String  search,
                        String  filter,
                        long    start,
                        long    stop,
                        boolean ifkey,
                        boolean iftime) {
    tryStartScan(key, search, filter, start, stop, ifkey, iftime, false);
    }

  /** Return the admitted scan's generation, or -1 when another scan owns the worker. */
  private synchronized long tryStartScan(String key, String search, String filter,
                                          long start, long stop, boolean ifkey, boolean iftime,
                                          boolean requireEmptyQueue) {
    if (_stopping && _thread != null && _thread.isAlive()) {
      log.warn("Previous scan is still stopping; new request ignored");
      return -1;
      }
    _stopping = false;
    if (_doscan == true || _scanning == true) {
      log.error("Already scanning, new request ignored");
      return -1;
      }
    if (requireEmptyQueue && !_queue.isEmpty()) {
      log.warn("Timed scan rejected: previous rows have not been polled");
      return -1;
      }
    if (_processor != null) _processor.reset();
    _scanKey    = key;
    _scanSearch = search;
    _scanFilter = filter;
    _scanStart  = start;
    _scanStop   = stop;
    _scanIfkey  = ifkey;
    _scanIftime = iftime;
    _scanFailure = null;
    _doscan     = true;
    _scanGeneration++;
    log.info("Scheduling asynchronous scan");
    if (_thread == null || !_thread.isAlive()) {
      _thread = new Thread(this, "HBaserNew-async-scan");
      _thread.setDaemon(true);
      _thread.start();
      }
    return _scanGeneration;
    }

  /** Start scan assynchronously. Present results as a <em>JSON</em> string.
    * @param key     The row key. Disables other search terms.
    *                It can be <tt>null</tt>.
    * @param search  The search terms as <tt>family:column:value,...</tt>.
    *                Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                among <tt>exact,prefix,substring,regex</tt>.
    *                The default for key is <tt>prefix</tt>,
    *                the default for columns is <tt>substring</tt>.
    *                The randomiser can be added with <tt>random:random:chance</tt>.
    *                It can be <tt>null</tt>.
    *                All searches are executed as prefix searches.
    * @param filter  The names of required values as <tt>family:column,...</tt>.
    *                <tt>*</tt> = all.
    * @param start   The time period start timestamp in <tt>ms</tt>.
    *                <tt>0</tt> means since the beginning.
    * @param stop    The time period stop timestamp in <tt>ms</tt>.
    *                <tt>0</tt> means till now.
    * @param ifkey   Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime  Whether give also entries timestamps (as <tt>key:time</tt>). */
   public String scan2json(String  key,
                           String  search,
                           String  filter,
                           long    start,
                           long    stop,
                           boolean ifkey,
                           boolean iftime,
                           int     timelimit) {
    Map<String, Map<String, String>> r = scan(key,
                                              search,
                                              filter,
                                              start,
                                              stop,
                                              ifkey,
                                              iftime,
                                              timelimit);
    return new JSONObject(r).toString();
    }

  /** Scan with timelimit.
    * @param key        The row key. Disables other search terms.
    *                   It can be <tt>null</tt>.
    * @param search     The search terms as <tt>family:column:value,...</tt>.
    *                   Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                   <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                   {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                   among <tt>exact,prefix,substring,regex</tt>.
    *                   The default for key is <tt>prefix</tt>,
    *                   the default for columns is <tt>substring</tt>.
    *                   The randomiser can be added with <tt>random:random:chance</tt>.
    *                   It can be <tt>null</tt>.
    *                   All searches are executed as prefix searches.
    * @param filter     The names of required values as <tt>family:column,...</tt>.
    *                   <tt>*</tt> = all.
    * @param start      The time period start timestamp in <tt>ms</tt>.
    *                   <tt>0</tt> means since the beginning.
    * @param stop       The time period stop timestamp in <tt>ms</tt>.
    *                   <tt>0</tt> means till now.
    * @param ifkey      Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime     Whether give also entries timestamps (as <tt>key:time</tt>).
    * @param timelimit  The scanning timelimit [s] after which the processus will be interrupted and
    *                   only so far acquired results will be returned.
    * @return           The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String  key,
                                               String  search,
                                               String  filter,
                                               long    start,
                                               long    stop,
                                               boolean ifkey,
                                               boolean iftime,
                                               int     timelimit) {
    log.info("Starting scan restricted to " + timelimit + "s");
    // Only the admitted request may stop its worker. A rejected timed request
    // must not interrupt a concurrent scan owned by another caller.
    long generation = tryStartScan(key, search, filter, start, stop, ifkey, iftime, true);
    if (generation < 0) {
      throw new IllegalStateException("Timed scan requires an idle client with no unconsumed rows");
      }
    long endtime = timelimit + System.currentTimeMillis() / 1000;
    try {
      try {
        // wait for first result or stopping
        while (size() == 0 && _doscan) {
        //log.info("" + size() + " results received, " + (endtime - (System.currentTimeMillis() / 1000)) + "s to end");
        if (System.currentTimeMillis() / 1000 >= endtime) {
          log.warn("Scanning ended due to time limit");
          break;
          }
        Thread.sleep(_loopWait / 10);
          }
        // continue scanning till endtime or completion
        while (scanning()) {
        //log.info("" + size() + " results received, " + (endtime - (System.currentTimeMillis() / 1000)) + "s to end");
        if (System.currentTimeMillis() / 1000 >= endtime) {
          log.warn("Scanning ended due to time limit");
          break;
          }
        Thread.sleep(_loopWait);
          }
        }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Scanning interrupted");
        }
      Exception failure = scanFailure();
      if (failure != null) throw new IllegalStateException("Asynchronous scan failed", failure);
      log.info(size() + " results accumulated");
      return poll(size());
      }
    finally {
      synchronized (this) {
        if (_scanGeneration == generation) stop(true);
        }
      }
   }

  /** Give next result (if available).
    * @return The available result. */
  public Map<String, Map<String, String>> poll() {
    return poll(1);
    }

  /** Give next several results (when available).
    * @param n The number of requested results.
    * @return The available results. */
  public Map<String, Map<String, String>> poll(int n) {
    Map.Entry<String, Map<String, String>> result;
    Map<String, Map<String, String>> results = new TreeMap<>();
    String key;
    if (n > size()) {
      n = size();
      log.warn("Only " + size() + " results available");
      }
    for (int i = 0; i < n; i++) {
      result = _queue.poll();
      if (result == null) break;
      key = result.getKey();
      results.put(key, result.getValue());
      }
    return results;
    }

  /** The actual size of the queue.
    * @return The number of available results. */
  public int size() {
    return _queue.size();
    }

  /** Tell, whether it is actually scanning.
    * @return Whether it is actually scanning. */
  public boolean scanning() {
    return _scanning;
    }

  /** Whether a scheduled scan is pending, even before its worker starts. */
  public boolean scanPending() {
    return _doscan || _scanning;
    }

  /** Failure of the last asynchronous scan, or null if none was recorded. */
  public Exception scanFailure() {
    return _scanFailure;
    }

  /** Set maximum size of the queue.
    * Queue accumlation will stop till its size goes bellow this limit.
    * @param maxsize The maximum size of the queue. Default is <tt>1000</tt>. */
  public void setMaxQueueSize(int maxsize) {
    _processor.setMaxQueueSize(maxsize);
    }

  /** Set the time parallel thread waits between actions.
    * @param t The time parallel thread waits between actions (in <tt>ms</tt>).
    *          The default is <tt>1000ms</tt>. */
  public void setLoopWait(int t) {
    _loopWait = t;
    }

  /** Stop the scanning and remove all remaining results. */
  public void stop() {
    stop(false);
    }

  /** Stop the scanning.
    * @param keep Whether to keep already accumulated results,
    *             or to remove them. */
  public synchronized void stop(boolean keep) {
    log.info("Stopping scan");
    if (_processor != null) _processor.cancel(keep);
    _doscan = false;
    if (_thread != null) {
      _stopping = _thread.isAlive();
      _thread.interrupt();
      }
    // The worker clears _scanning on exit; keep it set while it may still run.
    if (!keep) {
      log.info("\tand removing remaining results");
      }
    }

  protected ConcurrentLinkedQueue<Map.Entry<String, Map<String, String>>> _queue = new ConcurrentLinkedQueue<>();

  protected AsynchHBaseProcessor _processor = new AsynchHBaseProcessor(_queue);

  private Thread _thread;
  private long _scanGeneration;
  private volatile boolean _stopping;

  protected String  _scanKey;
  protected String  _scanSearch;
  protected String  _scanFilter;
  protected long    _scanStart;
  protected long    _scanStop;
  protected boolean _scanIfkey;
  protected boolean _scanIftime;

  protected volatile boolean _doscan   = false;
  protected volatile boolean _scanning = false;
  protected volatile Exception _scanFailure;

  protected int     _loopWait = 1000; // 1s = 1000ms

  /** Logging . */
  private static Logger log = LogManager.getLogger(AsynchHBaseClient.class);

  }
