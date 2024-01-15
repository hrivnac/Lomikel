package com.Lomikel.HBaser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.DateTimeManagement;
import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.DB.Schema;
import com.Lomikel.DB.Client;
import com.Lomikel.DB.SearchMap;
import com.Lomikel.ElasticSearcher.ESClient;

// HBase
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName ;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.filter.Filter;  
import org.apache.hadoop.hbase.filter.FilterList;  
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;  
import org.apache.hadoop.hbase.filter.RandomRowFilter;  
import org.apache.hadoop.hbase.filter.RowFilter;  
import org.apache.hadoop.hbase.filter.PrefixFilter;  
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.BinaryPrefixComparator;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.RegexStringComparator;
import org.apache.hadoop.hbase.filter.SubstringComparator;
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter;
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter.RowRange;

// Hadoop
import org.apache.hadoop.conf.Configuration;

// HealPix
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// Java
import java.util.List;  
import java.util.ArrayList;  
import java.util.Set;
import java.util.SortedSet;  
import java.util.TreeSet;  
import java.util.Map;  
import java.util.HashMap;  
import java.util.TreeMap;  
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.IOException;
import java.util.Date;
import java.util.Arrays;

// Log4J
import org.apache.log4j.Logger;

/** <code>AsynchHBaseClient</code> provides {@link HBaseClient} scan asynchronously.
  * Only some methods are available for asynchronous processing.
  * Only one asynchronous scanning {@link Thread} is allowed.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class AsynchHBaseClient extends HBaseClient 
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
  public void run() {
    try {
      while (true) {
        if (_doscan) {
          log.info("Starting asynchronous scan");
          _scanning = true;
          scan(_scanKey,
               _scanSearch,
               _scanFilter,
               _scanStart,
               _scanStop,
               _scanIfkey,
               _scanIftime);
          _doscan   = false;
          _scanning = false;
          }
        Thread.sleep(1000);
        }
      }
    catch (InterruptedException e) {
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
  protected void processResults(Map<String, Map<String, String>> results) {
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
    
  public boolean scanning() {
    return _scanning;
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
  
  /** Logging . */
  private static Logger log = Logger.getLogger(AsynchHBaseClient.class);

  }
