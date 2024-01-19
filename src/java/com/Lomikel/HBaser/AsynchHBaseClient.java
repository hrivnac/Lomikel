package com.Lomikel.HBaser;

import com.Lomikel.Utils.LomikelException;

// HBase
import org.apache.hadoop.hbase.client.Scan;

// HealPix
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// org.json      
import org.json.JSONObject;
import org.json.JSONArray;

// Java
import java.util.Collection;  
import java.util.List;  
import java.util.ArrayList;  
import java.util.Map;  
import java.util.TreeMap;  
import java.util.concurrent.ConcurrentLinkedQueue;

// Log4J
import org.apache.log4j.Logger;

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
          _scanning = true;
          setProcessor(new AsynchHBaseProcessor(_queue));
          scan(_scanKey,
               _scanSearch,
               _scanFilter,
               _scanStart,
               _scanStop,
               _scanIfkey,
               _scanIftime);
          setProcessor(null);
          _doscan   = false;
          _scanning = false;
          }
        Thread.sleep(_loopWait);
        }
      }
    catch (Exception e) {
      _doscan   = false;
      _scanning = false;
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
      _scanIfkey  = true;
      _scanIftime = iftime;
      _doscan     = true;
      log.info("Scheduling asynchronous scan");
      }
    _thread = new Thread(this);
    _thread.start();
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
    // run() will start scanning
    startScan(key,
              search,
              filter,
              start,
              stop,
              ifkey,
              iftime);
    long endtime = timelimit + System.currentTimeMillis() / 1000; 
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
     log.warn("Scanning interrupted");
     }
   _thread.interrupt();
   log.info(size() + " results accumulated");
   return poll(size());
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
    Map<String, String> result;
    Map<String, Map<String, String>> results = new TreeMap<>();
    String key;
    if (n > size()) {
      n = size();
      log.warn("Only " + size() + " results available");
      }
    for (int i = 0; i < n; i++) {
      result = _queue.poll();
      key = result.get("key:key");
      results.put(key, result);
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
    
  /** Set the time parallel thread waits between actions.
    * @param t The time parallel thread waits between actions.
    *          The default is <tt>1s</tt>. */
  public void setLoopWait(int t) {
    _loopWait = t;
    }
        
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
