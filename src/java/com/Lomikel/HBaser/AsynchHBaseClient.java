package com.Lomikel.HBaser;

import com.Lomikel.Utils.LomikelException;

// HBase
import org.apache.hadoop.hbase.client.Scan;

// HealPix
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// Java
import java.util.List;  
import java.util.ArrayList;  
import java.util.Map;  
import java.util.concurrent.ConcurrentLinkedQueue;

// Log4J
import org.apache.log4j.Logger;

/** <code>AsynchHBaseClient</code> provides {@link HBaseClient} scan asynchronously.
  * Only some methods are available for asynchronous processing.
  * Only one asynchronous scanning {@link Thread} is allowed.
  * Results are available as {@link List}s instead of {@link Map}s. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class AsynchHBaseClient implements Runnable, HBaseProcessor {
  
  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public AsynchHBaseClient(HBaseClient hbc) throws LomikelException {
    _hbc = hbc;
    _hbc.setProcessor(this);
    }
            
  @Override
  public void run() {
    try {
      while (true) {
        if (_doscan) {
          log.info("Starting asynchronous scan");
          _scanning = true;
          _hbc.scan(_scanKey,
                    _scanSearch,
                    _scanFilter,
                    _scanStart,
                    _scanStop,
                    _scanIfkey,
                    _scanIftime);
          _doscan   = false;
          _scanning = false;
          }
        Thread.sleep(_loopWait);
        }
      }
    catch (Exception e) {
      log.info("Stopped");
      }
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
  public void startScan(String  key,
                        String  search,
                        String  filter,
                        long    start,
                        long    stop,
                        boolean ifkey,
                        boolean iftime) {
    if (_doscan == true || _scanning == true) {
      log.error("Already scanning, new request ignored");
      }
    else {
      _scanKey    = key;
      _scanSearch = search;
      _scanFilter = filter;
      _scanStart  = start;
      _scanStop   = stop;
      _scanIfkey  = ifkey;
      _scanIftime = iftime;
      _doscan     = true;
      log.info("Scheduling asynchronous scan");
      }
    _thread = new Thread(this);
    _thread.start();
    }
  
  /** Add results into {@link ConcurrentLinkedQueue}
    * and clean the {@link Map}. */  
  @Override
  public void processResults(Map<String, Map<String, String>> results) {
    boolean isSchema = false; // BUG: in other subclasses of HBaseClient ?
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      if (entry.getKey().startsWith("schema")) {
        isSchema = true;
        break;
        }
      _queue.add(entry.getValue());
      }
    if (isSchema) {
      isSchema = false;
      }
    else {
      results.clear();
      }
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
    * @return           The {@link List} of {@link Map}s of results as <tt>{family:column-&gt;value}</tt>. */
  public List<Map<String, String>> restrictedScan(String  key,
                                                  String  search,
                                                  String  filter,
                                                  long    start,
                                                  long    stop,
                                                  boolean ifkey,
                                                  boolean iftime,
                                                  int     timelimit) {
    log.info("Starting scan restricted to " + timelimit + "s");
    startScan(key,
              search,
              filter,
              start,
              stop,
              ifkey,
              iftime);
    long endtime = timelimit + System.currentTimeMillis() / 1000; 
    try {
      while (size() == 0 || scanning()) {
        log.info("" + size() + " results received, " + (endtime - (System.currentTimeMillis() / 1000)) + "s to end");
        if (System.currentTimeMillis() / 1000 >= endtime) {
          log.warn("Scanning ended due to time limit");
          break;
          }
        Thread.sleep(_loopWait);
        }
      }
   catch (InterruptedException e) {
     log.warn("Scanning interrupted");
     }
   stop();
   log.info(size() + " results accumulated");
   return poll(size());
   }
    
  /** Give next result (if available).
    * @return The available result. */
  public Map<String, String> poll() {
    return _queue.poll();
    }
    
  /** Give next several results (when available).
    * @param n The number of requested results.
    * @return The available results. */
  public List<Map<String, String>> poll(int n) {
    List<Map<String, String>> results = new ArrayList<>();
    if (n > size()) {
      n = size();
      log.warn("Only " + size() + " results available");
      }
    for (int i = 0; i < n; i++) {
      results.add(_queue.poll());
      }
    return results;
    }
    
  /** The actual size of the queue.
    * @return The number of available results. */
  public int size() {
    return _queue.size();
    }
    
  /** Stop the {@link Thread}. */ 
  public void stop() {
    _thread.interrupt();
    }
    
  /** Tell, whether it is actually scanning.
    * @return Whether it is actually scanning. */
  public boolean scanning() {
    return _scanning;
    }
    
  /** Set the time parallel thread waits between actions.
    * @param t The time parallel thread waits between actions.
    *          The default is <tt>1s</tt>. */
  public void setLoopWait(int t) {
    _loopWait = t;
    }
    
  private HBaseClient _hbc;
        
  private ConcurrentLinkedQueue<Map<String, String>> _queue = new ConcurrentLinkedQueue<>();
  
  private Thread _thread;
  
  private String  _scanKey;
  private String  _scanSearch;
  private String  _scanFilter;
  private long    _scanStart;
  private long    _scanStop;
  private boolean _scanIfkey; 
  private boolean _scanIftime;
    
  private boolean _doscan   = false;  
  private boolean _scanning = false;
  
  private int     _loopWait = 1000; // 1s = 1000ms
  
  /** Logging . */
  private static Logger log = Logger.getLogger(AsynchHBaseClient.class);

  }
