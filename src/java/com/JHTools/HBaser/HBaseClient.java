package com.JHTools.HBaser;

import com.JHTools.Utils.DateTimeManagement;

// HBase
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName ;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Get;
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
import org.apache.hadoop.hbase.filter.RowFilter;  
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.BinaryPrefixComparator;
import org.apache.hadoop.hbase.filter.SubstringComparator;

// Hadoop
import org.apache.hadoop.conf.Configuration;

// Java
import java.util.List;  
import java.util.ArrayList;  
import java.util.Set;
import java.util.HashSet;  
import java.util.TreeSet;  
import java.util.Map;  
import java.util.HashMap;  
import java.util.NavigableMap;
import java.io.PrintWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

// Log4J
import org.apache.log4j.Logger;

/** <code>HBaseClient</code> handles HBase connection. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseClient {
    
 /** Selftest.https://vm-75109.lal.in2p3.fr:8443
   * @throws IOException If anything goes wrong. */
 public static void main(String[] args) throws IOException {
   //String zookeepers = "localhost";
   //String clientPort = "2181";
   String zookeepers = "134.158.74.54";
   String clientPort = "2181";
   HBaseClient client = new HBaseClient(zookeepers, clientPort);
   //HBaseClient client = new HBaseClient("http://localhost:2181");
   client.connect("test_portal_tiny.1");
   Map<String, String> search = new HashMap<>();
   //search.put("key:key", "ZTF19");
   Map<String, Map<String, String>> results = client.scan(null,
                                                          search,
                                                          null,
                                                          null,
                                                          false,
                                                          false);   
   }
   
 /** Create.
   * @param zookeepers The coma-separated list of zookeper ids.
   * @param clientPort The client port. 
   * @throws IOException If anything goes wrong. */
 public HBaseClient(String zookeepers,
                    String clientPort) throws IOException {
   log.info("Opening " + zookeepers + " on port " + clientPort);
   _conf = HBaseConfiguration.create();
   _conf.set("hbase.zookeeper.quorum", zookeepers);
   _conf.set("hbase.zookeeper.property.clientPort", clientPort);
   _connection = ConnectionFactory.createConnection(_conf); 
   }
   
 /** Create.
   * @param url The HBase url.
   * @throws IOException If anything goes wrong. */
 public HBaseClient(String url) throws IOException {
   log.info("Opening " + url);
   String[] x = url.replaceAll("http://", "").split(":");
   _conf = HBaseConfiguration.create();
   _conf.set("hbase.zookeeper.quorum", x[0]);
   _conf.set("hbase.zookeeper.property.clientPort", x[1]);
   _connection = ConnectionFactory.createConnection(_conf); 

   }
   
 /** Create local.
   * @throws IOException If anything goes wrong. */
 public HBaseClient() throws IOException {
   log.info("Opening");
   Configuration conf = HBaseConfiguration.create();
   _connection = ConnectionFactory.createConnection(_conf); 
   }
    
 /** Connect the table.
   * @param tableName  The table name.
   * @return           The assigned id. 
   * @throws IOException If anything goes wrong. */
  public Table connect(String tableName) throws IOException {
    return connect(tableName, "schema", 0);
    }
    
 /** Connect the table.
   * @param tableName  The table name.
   * @param schemaName The name of the {@link Schema} row.
   *                   <tt>null</tt> means to ignore schema,
   *                   empty {@link String} will take the latest one. 
   * @param timeout    The timeout in ms (may be <tt>0</tt>).
   * @return           The assigned id.
   * @throws IOException If anything goes wrong. */
  public Table connect(String tableName,
                       String schemaName,
                       int    timeout) throws IOException {
    // Table setup
    log.info("Connecting to " + tableName);
    _tableName = tableName;
    if (timeout > 0) {
      String tout = String.valueOf(timeout);
      _conf.set("hbase.rpc.timeout",                   tout);
      _conf.set("hbase.client.scanner.timeout.period", tout);
      }
    _table = _connection.getTable(TableName.valueOf(_tableName));
    // Schema search
    Map<String, String> search = new HashMap<>();
    if (schemaName != null) {
      if (schemaName.equals("")) {
        log.info("Using the most recent schema");
        }
      else {
        log.info("Searching for schema " + schemaName);
        search.put("key:key", schemaName);
        }
      Map<String, Map<String, String>> schemas = scan(null,
                                                      search,
                                                      null,
                                                      null,
                                                      false,
                                                      false);
      if (schemas.size() == 0) {
        log.error("No schema found");
        }
      else if (schemas.size() > 1) {
        log.info("" + schemas.size() + " schemas found, choosing the most recent one");
        _schema = new Schema(schemas.entrySet().iterator().next().getValue()); // BUG: wrong
        }
      else {
        _schema = new Schema(schemas.entrySet().iterator().next().getValue());
        }
      }
    return _table;
    }

                    
  /** Get entry or entries from the {@link Catalog}.
    * @param key      The row key. Disables other search terms.
    * @param search   The search terms: {@link Map} of <tt>name-&gt;value</tt>.
    *                 Key can be searched with key:key "pseudo-name".
    *                 All searches are executed as prefix searches.
    * @param filter   The names of required values as array of <tt>family:column</tt>. It can be null.
    * @param interval The time period specified as start,end in the format <tt>HH:mm:ss.SSS dd/MMM/yyyy</tt>.
    * @param ifkey    Whether give also entries keys.
    * @param iftime   Whether give also entries timestamps.
    * @return         The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String              key,
                                               Map<String, String> search,
                                               String[]            filter,
                                               long[]              p,
                                               boolean             ifkey,
                                               boolean             iftime) {
     if (p == null || p.length != 2) {
       p = new long[]{0, 0};
       }
     long start = p[0];
     long stop  = p[1];
     long now   = System.currentTimeMillis(); 
     if (start != 0) {
       start  = now - (long)(start * 1000 * 60);
       }
     if (stop != 0) {
       stop   = now - (long)(stop  * 1000 * 60);
       }
     return scan(key, search, filter, start, stop, ifkey, iftime);
     }
                    
  /** Get entry or entries from the {@link Catalog}.
    * @param key      The row key. Disables other search terms.
    * @param search   The search terms: {@link Map} of <tt>name-&gt;value</tt>.
    *                 Key can be searched with key:key "pseudo-name".
    *                 All searches are executed as prefix searches.
    * @param filter   The names of required values as array of <tt>family:column</tt>. It can be null.
    * @param interval The time period specified as start,end in the format <tt>HH:mm:ss.SSS dd/MMM/yyyy</tt>.
    * @param ifkey    Whether give also entries keys.
    * @param iftime   Whether give also entries timestamps.
    * @return         The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String              key,
                                               Map<String, String> search,
                                               String[]            filter,
                                               String              startS,
                                               String              stopS,
                                               String              format,
                                               boolean             ifkey,
                                               boolean             iftime) {
     long start = 0;
     long stop  = 0;
     if (format == null || !format.trim().equals("")) {
       format = "dd/MM/yyyy HH:mm:ss:SSS";
       }
     DateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm");
     try {
       if (startS != null && !startS.trim().equals("")) {       
         Date startD = formatter.parse(startS);
         Calendar startC = GregorianCalendar.getInstance();
         startC.setTime(startD);
         start = startC.getTimeInMillis();
         }
       }
     catch (ParseException e) {
       }
     try {
       if (stopS != null && !stopS.trim().equals("")) {       
         Date stopD = formatter.parse(stopS);
         Calendar stopC  = GregorianCalendar.getInstance();
         stopC.setTime(stopD);
         stop = stopC.getTimeInMillis(); 
         }
       }
     catch (ParseException e) {
       }
     return scan(key, search, filter, start, stop, ifkey, iftime);
     }
                     
  /** Get entry or entries from the {@link Catalog}.
    * @param key      The row key. Disables other search terms.
    * @param search   The search terms: {@link Map} of <tt>name-&gt;value</tt>.
    *                 Key can be searched with key:key "pseudo-name".
    *                 All searches are executed as prefix searches.
    * @param filter   The names of required values as array of <tt>family:column</tt>. It can be null.
    * @param interval The time period specified as start,end in the format <tt>HH:mm:ss.SSS dd/MMM/yyyy</tt>.
    * @param ifkey    Whether give also entries keys.
    * @param iftime   Whether give also entries timestamps.
    * @return         The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String              key,
                                               Map<String, String> search,
                                               String[]            filter,
                                               long                start,
                                               long                stop,
                                               boolean             ifkey,
                                               boolean             iftime) {
    log.info("Searching for key: " + key + 
             ", search: " + search + 
             ", filter: " + (filter == null ? null : String.join(",", filter)) +
             ", interval: " + start + "-" + stop +
             ", id-time: " + ifkey + "-" + iftime);
    if (filter == null) {
      filter = new String[0];
      }
    if (stop == 0) {
      stop = Long.MAX_VALUE;
      }
    Map<String, Map<String, String>> results = new HashMap<>();
    Map<String, String> result;
    if (key != null && !key.trim().equals("")) {
      Get get = new Get(Bytes.toBytes(key));
      result = new HashMap<>();
      try {
        Result r = table().get(get);
        log.info("" + r.size() + " entries found");
        addResult(r, result, ifkey, iftime);
        results.put(key, result);
        }
      catch (IOException e) {
        log.error("Cannot search", e);
        }
      }
    else {
      Scan scan = new Scan();
      try {
        scan.setTimeRange(start, stop);
        }
      catch (IOException e) {
        log.error("Cannot set time range " + start + " - " + stop);
        }
      String[] fc;
      String family; 
      String column; 
      String value;
      List<Filter> filters = new ArrayList<>();
      for (Map.Entry<String, String> entry : search.entrySet()) {
        fc = entry.getKey().split(":");
        family = fc[0];
        column = fc[1];
        value  = entry.getValue();
        if (family.equals("key") && column.equals("key")) {
          filters.add(new RowFilter(CompareOp.EQUAL, new BinaryPrefixComparator(Bytes.toBytes(value))));
          char[] idc = value.toCharArray();
          char last = idc[idc.length - 1];
          String id1 = value.substring(0, value.length() - 1) + ++last;  
          scan.setStartRow(Bytes.toBytes(value));
          scan.setStopRow( Bytes.toBytes(id1)); // TBD: correct ?
          }
        else {
          filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, new SubstringComparator(value)));
          }
        }
      FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL, filters);  
      scan.setFilter(filterList);
      for (String f : filter) {
        fc = f.split(":");
        family = fc[0];
        column = fc[1];
        scan.addColumn(Bytes.toBytes(family), Bytes.toBytes(column));
        }
      try {
        ResultScanner rs = table().getScanner(scan);
        int i = 0;
        for (Result r : rs) {
          result = new HashMap<>();
          addResult(r, result, ifkey, iftime);
          results.put(Bytes.toString(r.getRow()), result);
          i++;
          }
        log.info("" + i + " entries found");
        }
      catch (IOException e) {
        log.error("Cannot search", e);
        }
      }
    log.info(results.size() + " results found");
    return results;
    }
    
  /** Add {@link Result} into result {@link Map}.
    * @param r       The {@link Result} to add.
    * @param result  The {@link Map} of results <tt>familty:column-&gt;value</tt>.
    * @param ifkey   Whether add also entries keys.
    * @param iftime  Whether add also entries timestamps. */
  private void addResult(Result r,
                         Map<String, String> result,
                         boolean             ifkey,
                         boolean             iftime) {
    String key = Bytes.toString(r.getRow());
    String[] ff;
    String ref;
    if (r != null && r.getRow() != null) {
      if (ifkey) {
        result.put("key:key", key);
        }
      String family;
      String column;
      NavigableMap<byte[], NavigableMap<byte[], byte[]>>	 resultMap = r.getNoVersionMap();
      for (Map.Entry<byte[], NavigableMap<byte[], byte[]>> entry : resultMap.entrySet()) {
        family = Bytes.toString(entry.getKey());
        for (Map.Entry<byte[], byte[]> e : entry.getValue().entrySet()) {
          column = family + ":" + Bytes.toString(e.getKey());
          // searching for schema
          if (key.startsWith("schema")) {
            result.put(column, Bytes.toString(e.getValue()));
            }
          // known schema
          else if (_schema != null && _schema.type(column) != null) {
            // binary
            if (family.equals("b")) {
              ref = "binary:" + key + ":" + Bytes.toString(e.getKey());
              result.put(column, ref);
              _repository.put(ref, _schema.decode2Content(column, e.getValue()).asBytes());
              }
            // not binary
            else {
              result.put(column, _schema.decode(column, e.getValue()));
              }
            }
          // no schema
          else {
            result.put(column, Bytes.toString(e.getValue()));
            }
          }
        }              
      if (iftime) {
        result.put("key:time", DateTimeManagement.time2String(r.rawCells()[0].getTimestamp()));
        }
      }
    }
    
  /** Set the table {@link Schema}.
    * @param schema The {@link Schema} to set. */
  public void setSchema(Schema schema) {
    _schema = schema;
    }
    
  /** Give the table {@link Schema}.
    * @param schema The used {@link Schema}. */
  public Schema schema() {
    return _schema;
    }
    
 /** Give the table {@link BinaryDataRepository}.
    * @param schema The used {@link BinaryDataRepository}. */
  public BinaryDataRepository repository() {
    return _repository;
    }
    
  @Override
  protected void finalize() throws Throwable {
    close();
    }
    
  /** Close and release resources. */
  public void close() {
    log.debug("Closing");
    try {
      _table.close();
      _connection.close();
      }
    catch (IOException e) {
      log.warn("Cannot close Table", e);
      }
    _table = null;
    }               

  /** Give {@link Table}.
    * @return The {@link Table}. */
  public Table table() {
    return _table;
    }

  private Table _table;
  
  private Configuration _conf;
  
  private Connection _connection;
  
  private String _tableName;
  
  private Schema _schema;
    
  private BinaryDataRepository _repository = new BinaryDataRepository();  

  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseClient.class);

  }
