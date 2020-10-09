package com.Lomikel.HBaser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.DateTimeManagement;
import com.Lomikel.Utils.LomikelException;

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
import org.apache.hadoop.hbase.filter.PrefixFilter;  
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.BinaryPrefixComparator;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.RegexStringComparator;
import org.apache.hadoop.hbase.filter.SubstringComparator;

// Hadoop
import org.apache.hadoop.conf.Configuration;

// Java
import java.util.List;  
import java.util.ArrayList;  
import java.util.Set;
import java.util.TreeSet;  
import java.util.Map;  
import java.util.HashMap;  
import java.util.TreeMap;  
import java.util.NavigableMap;
import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;

// Log4J
import org.apache.log4j.Logger;

/** <code>HBaseClient</code> handles connectionto HBase table. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseClient {
       
 /** Create.
   * @param zookeepers The coma-separated list of zookeper ids.
   * @param clientPort The client port. 
   * @throws IOException If anything goes wrong. */
 public HBaseClient(String zookeepers,
                    int    clientPort) throws IOException {
   this(zookeepers, String.valueOf(clientPort));
   }
   
 /** Create.
   * @param zookeepers The coma-separated list of zookeper ids.
   * @param clientPort The client port. 
   * @throws IOException If anything goes wrong. */
 public HBaseClient(String zookeepers,
                    String clientPort) throws IOException {
   Init.init();
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
   _conf.set("hbase.zookeeper.property.clientPorf t", x[1]);
   _connection = ConnectionFactory.createConnection(_conf); 

   }
   
 /** Create on <em>localhost</em>.
   * @throws IOException If anything goes wrong. */
 public HBaseClient() throws IOException {
   log.info("Opening");
   Configuration conf = HBaseConfiguration.create();
   _connection = ConnectionFactory.createConnection(_conf); 
   }
   
 /** Connect to the table.
   * @param tableName  The table name.
   * @return           The assigned id. 
   * @throws IOException If anything goes wrong. */
  // TBD: which schema is used ?
  public Table connect(String tableName) throws IOException {
    return connect(tableName, "schema");
    }
            
 /** Connect to the table.
   * @param tableName  The table name.
   * @param schemaName The name of the {@link Schema} row.
   *                   <tt>null</tt> means to ignore schema,
   *                   empty {@link String} will take the latest one. 
   * @return           The assigned id.
   * @throws IOException If anything goes wrong. */
  public Table connect(String tableName,
                       String schemaName) throws IOException {
    return connect(tableName, schemaName, 0);
    }
    
 /** Connect to the table.
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
    if (schemaName != null) {
      if (schemaName.equals("")) {
        log.info("Using the most recent schema");
        }
      else {
        log.info("Searching for schema " + schemaName);
        }
      Map<String, Map<String, String>> schemas = scan(schemaName,
                                                      null,
                                                      null,
                                                      0,
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
                    
                      
  /** Get row(s).
    * @param key      The row key. Disables other search terms.
    *                 It can be <tt>null</tt>.
    * @param search   The search terms as <tt>family:column:value,...</tt>.
    *                 Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                 {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                 among <tt>exact,prefix,substring,regex</tt>.
    *                 The default for key is <tt>prefix</tt>,
    *                 the default for columns is <tt>substring</tt>.
    *                 It can be <tt>null</tt>.
    *                 All searches are executed as prefix searches.    
    * @param filter   The names of required values as <tt>family:column,...</tt>.
    *                 It can be <tt>null</tt>.
    * @param delay   The time period start, in minutes back since dow.
    *                 <tt>0</tt> means no time restriction.
    * @param ifkey    Whether give also entries keys.
    * @param iftime   Whether give also entries timestamps.
    * @return         The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String  key,
                                               String  search,
                                               String  filter,
                                               long    delay,
                                               boolean ifkey,
                                               boolean iftime) {
    log.debug("Searching for key: " + key + 
              ", search: " + search + 
              ", filter: " + filter +
              ", delay: "  + delay + " min" + 
              ", id/time: " + ifkey + "/" + iftime);
    long now = System.currentTimeMillis();
    long start = (delay == 0L) ? 0L :  now - delay * 1000L * 60L;
    long stop = now;
    return scan(key, search, filter, start, stop, ifkey, iftime);
    }
                   
  /** Get row(s).
    * @param key      The row key. Disables other search terms.
    *                 It can be <tt>null</tt>.
    * @param search   The search terms as <tt>family:column:value,...</tt>.
    *                 Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                 {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                 among <tt>exact,prefix,substring,regex</tt>.
    *                 The default for key is <tt>prefix</tt>,
    *                 the default for columns is <tt>substring</tt>.
    *                 It can be <tt>null</tt>.
    *                 All searches are executed as prefix searches.    
    * @param filter   The names of required values as <tt>family:column,...</tt>.
    *                 It can be <tt>null</tt>.
    *                 It is ignored when searching using formula.
    * @param start    The time period start timestamp in <tt>ms</tt>.
    *                 <tt>0</tt> means since the beginning.
    * @param stop     The time period stop timestamp in <tt>ms</tt>.
    *                 <tt>0</tt> means till now.
    * @param ifkey    Whether give also entries keys.
    * @param iftime   Whether give also entries timestamps.
    * @return         The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String  key,
                                               String  search,
                                               String  filter,
                                               long    start,
                                               long    stop,
                                               boolean ifkey,
                                               boolean iftime) {
    log.info("Searching for key: " + key + 
             ", search: " + search + 
             ", filter: " + filter +
             ", interval: " + start + " ms - " + stop + " ms" +
             ", id/time: " + ifkey + "/" + iftime);
    Map<String, String> searchM = new TreeMap<>();
    String[] ss;
    if (search != null && !search.trim().equals("")) {
      for (String s : search.trim().split(",")) {
        ss = s.trim().split(":");
        if (ss.length == 4) {
          searchM.put(ss[0] + ":" + ss[1] + ":" + ss[2], ss[3]);
          }
        else {
          searchM.put(ss[0] + ":" + ss[1], ss[2]);
          }
        }
      }
    return scan(key, searchM, filter, start, stop, ifkey, iftime);
    }
                   
  /** Get row(s).
    * @param key       The row key. Disables other search terms.
    *                  It can be <tt>null</tt>.
    * @param searchMap The {@link Map} of search terms as <tt>family:column-value</tt>.
    *                  Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                  {@link Comparator} can be chosen as <tt>family:column-value:comparator</tt>
    *                  among <tt>exact,prefix,substring,regex</tt>.
    *                  The default for key is <tt>prefix</tt>,
    *                  the default for columns is <tt>substring</tt>.
    *                  It can be <tt>null</tt>.
    *                  All searches are executed as prefix searches.    
    * @param filter    The names of required values as <tt>family:column,...</tt>.
    *                  It can be <tt>null</tt>.
    *                  It is ignored when searching using formula.
    * @param start     The time period start timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means since the beginning.
    * @param stop      The time period stop timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means till now.
    * @param ifkey     Whether give also entries keys.
    * @param iftime    Whether give also entries timestamps.
    * @return          The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String              key,
                                               Map<String, String> searchMap,
                                               String              filter,
                                               long                start,
                                               long                stop,
                                               boolean             ifkey,
                                               boolean             iftime) {
    log.info("Searching for key: " + key + 
             ", search: " + searchMap + 
             ", filter: " + filter +
             ", interval: " + start + " ms - " + stop + " ms" +
             ", id/time: " + ifkey + "/" + iftime);
    long time = System.currentTimeMillis();
    if (stop == 0) {
      stop = System.currentTimeMillis();
      }
    Map<String, Map<String, String>> results = new TreeMap<>();
    Map<String, String> result;
    String[] fc;
    String family; 
    String column;
    String comparator;
    String value;
    String[] filterA = null;
    if (_formula == null && filter != null && !filter.trim().equals("")) {
      filterA = filter.trim().split(",");
      }         
    // Get
    if (key != null && !key.trim().equals("")) {
      Get get;
      Result r;
      for (String k : key.split(",")) {
        get = new Get(Bytes.toBytes(k.trim()));
        // Filter
        if (filterA != null && filterA.length > 0) {
          for (String f : filterA) {
            fc = f.split(":");
            family = fc[0];
            column = fc[1];
            get.addColumn(Bytes.toBytes(family), Bytes.toBytes(column));
            }
          }
        result = new TreeMap<>();
        try {
          r = table().get(get);
          log.info("" + r.size() + " entries found");
          addResult(r, result, filterA, ifkey, iftime);
          results.put(k, result);
          }
        catch (IOException e) {
          log.error("Cannot search", e);
          }
        }
      }
    // Scan
    else {
      Scan scan = new Scan();
      // Time range
      try {
        scan.setTimeRange(start, stop);
        }
      catch (IOException e) {
        log.error("Cannot set time range " + start + " - " + stop);
        }
      // Search
      if (searchMap != null && !searchMap.isEmpty()) {
        List<Filter> filters = new ArrayList<>();
        for (Map.Entry<String, String> entry : searchMap.entrySet()) {
          fc = entry.getKey().split(":");
          family = fc[0];
          column = fc[1];
          comparator = fc.length == 3 ? fc[2] : "default";
          value  = entry.getValue();
          if (family.equals("key") && column.equals("key")) {
            String[] keyArray = value.split(",");
            Arrays.sort(keyArray);
            String firstKey = null;
            String lastKey  = null;
            if (keyArray.length > 1) {
              setSearchOperator("OR");
              }
            for (String k : keyArray) {
              switch (comparator) {
                case "exact":
                  filters.add(new RowFilter(CompareOp.EQUAL, new BinaryComparator(Bytes.toBytes(k))));
                  break;
                case "substring":
                  filters.add(new RowFilter(CompareOp.EQUAL, new SubstringComparator(k)));
                  break;
                case "regex":
                  filters.add(new RowFilter(CompareOp.EQUAL, new RegexStringComparator(k)));
                  break;
                default: // prefix
                  filters.add(new PrefixFilter(Bytes.toBytes(k)));
                }
              if (firstKey == null) {
                firstKey = k;
                }
              lastKey = k;
              }
            if (!comparator.equals("substring") && !comparator.equals("regex")) {
              scan.withStartRow(Bytes.toBytes(firstKey),               true);
              scan.withStopRow(incrementBytes(Bytes.toBytes(lastKey)), true);
              }
            }
          else {
            switch (comparator) {
              case "exact":
                filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, Bytes.toBytes(value)));
                break;
              case "prefix":
                filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, new BinaryPrefixComparator(Bytes.toBytes(value))));
                break;
              case "regex":
                filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, new RegexStringComparator(value)));
                break;
              default: // substring
                filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, new SubstringComparator(value)));
              }
             }
          }
        FilterList filterList = new FilterList(_operator, filters);  
        scan.setFilter(filterList);
        }
      // Filter
      if (filterA != null && filterA.length > 0) {
        for (String f : filterA) {
          fc = f.split(":");
          family = fc[0];
          column = fc[1];
          scan.addColumn(Bytes.toBytes(family), Bytes.toBytes(column));
          }
        }
      // Limit
      int limit0 = _limit0;
      if (_evaluator == null && (_limit < _limit0 || _limit0 == 0)) {
        limit0 = _limit;
        }
      if (limit0 > 0) {
        scan.setLimit(limit0);
        if (_schema != null) {
          scan.setMaxResultSize(limit0 * _schema.size());
          }
        }
      // Results
      try {
        ResultScanner rs = table().getScanner(scan);
        int i = 0;
        for (Result r : rs) {
          result = new TreeMap<>();
          if (addResult(r, result, filterA, ifkey, iftime)) {
            results.put(Bytes.toString(r.getRow()), result);
            if (++i == _limit) {
              break;
              }
            }
          }
        log.info("" + i + " entries found");
        }
      catch (IOException e) {
        log.error("Cannot search", e);
        }
      }
    log.info(results.size() + " results found in " + (System.currentTimeMillis() - time) + "ms");
    return results;
    }
    
  /** Add {@link Result} into result {@link Map}.
    * @param r       The {@link Result} to add.
    * @param result  The {@link Map} of results <tt>familty:column-&gt;value</tt>.
    * @param filter  The names of required values as array of <tt>family:column</tt>.
    *                It can be <tt>null</tt>.
    * @param ifkey   Whether add also entries keys.
    * @param iftime  Whether add also entries timestamps.
    * @return        Whether the result has been added. */
  private boolean addResult(Result              r,
                            Map<String, String> result,
                            String[]            filter,
                            boolean             ifkey,
                            boolean             iftime) {
    String key = Bytes.toString(r.getRow());
    if (!key.startsWith("schema") &&_evaluator != null && !evaluateResult(r)) {
      return false;
      }
    String[] ff;
    String ref;
    if (r != null && r.getRow() != null) {
      if (ifkey) {
        result.put("key:key", key);
        }
      if (filter == null || filter.length > 0) {
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
        }
      if (iftime) {
        if (_dateFormat == null) {
          result.put("key:time", String.valueOf(r.rawCells()[0].getTimestamp()));
          }
        else {
          result.put("key:time", DateTimeManagement.time2String(r.rawCells()[0].getTimestamp()));
          }
        }
      }
    return true;
    }
    
  /** Evaluate {@link Result} using registered formula.
    * @param r The {@link Result} to be evaluated. */
  private boolean evaluateResult(Result r) {
    Map<String, String> values = new HashMap<>();
    Map<byte[], NavigableMap<byte[], byte[]>>	resultMap = r.getNoVersionMap();
    String family;
    String column;
    String value;
    for (Map.Entry<byte[], NavigableMap<byte[], byte[]>> entry : resultMap.entrySet()) {
      family = Bytes.toString(entry.getKey());
      for (Map.Entry<byte[], byte[]> e : entry.getValue().entrySet()) {
        column = family + ":" + Bytes.toString(e.getKey());
        value = _schema.decode(column, e.getValue());
        values.put(column, value);
        }
      }
    try {
      return _evaluator.evalBoolean(values, _formula);
      }
    catch (LomikelException e) {
      log.error("Cannot evaluate " + _formula + " taking false", e);
      return false;
      }
    }
    
  /** Give the timeline for the column.
    * @param columnName The name of the column.
    * @return           The {@link Map} value-timestamp. */
  public Map<String, Long> timeline(String columnName) {
    Map<String, Long> tl = new TreeMap<>();
    Map<String, Map<String, String>> results = scan(null, null, columnName, 0, false, true);
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      if (!entry.getKey().startsWith("schema")) { 
        tl.put(entry.getValue().get(columnName), Long.parseLong(entry.getValue().get("key:time")));
        }
      }
    return tl;
    }
    
  /** Give all recent values of the column.
    * @param columnName     The name of the column.
    * @param substringValue The column value substring to search for.
    * @param minutes        How far into the past it should search. 
    * @param getValues      Whether to get column values or row keys.
    * @return               The {@link Set} of different values of that column. */
  public Set<String> latests(String columnName,
                             String substringValue,
                             long minutes,
                             boolean getValues) {
    Set<String> l = new TreeSet<>();
    String search = "";
    if (substringValue != null) {
      search += columnName + ":" + substringValue;
      }
    Map<String, Map<String, String>> results = scan(null, search, null, minutes, false, false);
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      if (!entry.getKey().startsWith("schema")) {
        l.add(getValues ? entry.getValue().get(columnName) : entry.getKey());
        }
      }
    return l;
    }

  /** Set formula to be used to filter rows.
    * <pre>
    * // a simple (Java) formula using HBase table columns and giving boolean value
    * client.setEvaluation("dec < 10");
    * // an external function, should also specify a list of columns internally used by their function 
    * client.setEvaluation("isWithinGeoLimits(80, 85, -4.0, 0.0)", "ra,dec");
    * // standard scan is executed after an evaluation function set
    * client.scan(...);
    * </pre>
    * @param formula   The formula to be used to filter rows.
    *                  The variables (column names) should appear without family names.
    *                  <tt>null</tt> or empty unsets the evaluation formula.
    * @param variables The variables used by the formula if implemented as an external function. */
  // TBD: document how to create external fuctions
  public void setEvaluation(String formula,
                            String variables) {
    setEvaluation(formula);
    if (_formula != null) {
      _evaluator.setVariables(variables);
      }
    }
        
  /** Set formula to be used to filter rows.
    * @param formula The formula to be used to filter rows.
    *                The variables (column names) should appear without family names.
    *                <tt>null</tt> or empty unsets the evaluation formula. */
  public void setEvaluation(String formula) {
    if (formula == null || formula.trim().equals("")) {
      _formula =  null;
      _evaluator = null;
      log.info("Unsetting evaluation formula");
      return;
      }
    log.info("Setting evaluation formula '" + formula + "'");
    if (_schema == null) {
      log.error("Evaluation can be set only for known Schema");
      return;
      }
    try {
      _evaluator = new Evaluator(_schema);
      _evaluator.setVariables(formula);
      _formula   = formula;
      }
    catch (LomikelException e) {
      log.error("Evaluator cannot be set", e);
      }
    }
      
  /** Set the table {@link Schema}.
    * @param schema The {@link Schema} to set. */
  public void setSchema(Schema schema) {
    _schema = schema;
    }
    
  /** Set the limit for the number of results.
    * @param limit The limit for the number of results. */
  public void setLimit(int limit) {
    log.info("Setting limit " + limit);
    _limit = limit;
    }
    
  /** Set the limit for the number of searched results (before eventual evaluation).
    * @param limit The limit for the number of searched esults. */
  public void setSearchLimit(int limit) {
    log.info("Setting search limit " + limit);
    _limit0 = limit;
    }
    
  /** Set the AND/OR operator for prefix column search.
    * @param operator OR when contains OR, AND otherwise. */
  public void setSearchOperator(String operator) {
    log.info("Setting search operator to " + operator);
    if (operator.contains("OR")) {
       _operator = FilterList.Operator.MUST_PASS_ONE;
       }
    else {
      _operator = FilterList.Operator.MUST_PASS_ALL;
      }
    }
    
  /** Set result timestamp format.
    * @param format The result timestamp format.
    *               The default is the native HBase format (ms). */
  public void setDateFormat(String dateFormat) {
    log.info("Setting Date format " + dateFormat);
    _dateFormat = dateFormat;
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
    
  /** Increment <tt>byte[]</tt>.
    * @param value The origibal value.
    * @return      The incremented value. */
  public static byte[] incrementBytes(final byte[] value) {
    byte[] newValue = Arrays.copyOf(value, value.length);
    for (int i = 0; i < newValue.length; i++) {
      int val = newValue[newValue.length - i - 1] & 0x0ff;
      int total = val + 1;
      boolean carry = false;
      if (total > 255) {
        carry = true;
        total %= 256;
        }
      newValue[newValue.length - i - 1] = (byte) total;
      if (!carry) {
        return newValue;
        }
      }
    return newValue;
    }
    
  /** Results presented as readable {@link String}.
    * @param results The {@link Map} of results.
    * @return        The result is a readable form. */
  public static String results2String(Map<String, Map<String, String>> results) {
    String report = "";
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      report += entry.getKey() + " = " + entry.getValue() + "\n";
      }
    return report;
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
  
  private int _limit0 = 0;
   
  private int _limit = 0;
 
  private String _dateFormat = null;
  
  private FilterList.Operator _operator = FilterList.Operator.MUST_PASS_ALL;
    
  private BinaryDataRepository _repository = new BinaryDataRepository();  

  private Evaluator _evaluator;
  
  private String _formula;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseClient.class);

  }
