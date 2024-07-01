package com.Lomikel.HBaser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.DateTimeManagement;
import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.DB.Schema;
import com.Lomikel.DB.Client;
import com.Lomikel.DB.SearchMap;

// HBase
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.TableName ;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptor;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Delete;
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

// Joinery
import joinery.DataFrame;

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
import java.io.IOException;
import java.util.Date;
import java.util.Arrays;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>HBaseClient</code> connects to HBase. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseClient extends Client<Table, HBaseSchema> {
   
  // Lifecycle -----------------------------------------------------------------
  
  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public HBaseClient(String zookeepers,
                     String clientPort) throws LomikelException {
    Init.init();
    setup(zookeepers, clientPort);
    log.info("Opening " + zookeepers + " on port " + clientPort);
    _conf = HBaseConfiguration.create();
    if (zookeepers != null) {
      _conf.set("hbase.zookeeper.quorum", zookeepers);
      _conf.setInt("hbase.client.scanner.timeout.period", 100000);
      }
    if (clientPort != null) {
      _conf.set("hbase.zookeeper.property.clientPort", clientPort);
      }
    try {
      _connection = ConnectionFactory.createConnection(_conf); 
      }
    catch (IOException e) {
      throw new LomikelException("Cannot create from " + _conf, e);
      }
    setSearchOperator("OR");
    }
        
  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public HBaseClient(String zookeepers,
                     int    clientPort) throws LomikelException {
    this(zookeepers, String.valueOf(clientPort));
    }
    
  /** Create and connect to HBase.
    * @param url The HBase url.
    * @throws LomikelException If anything goes wrong. */
  public HBaseClient(String url) throws LomikelException {
    this(url.replaceAll("http://", "").split(":")[0], url.replaceAll("http://", "").split(":")[1]);
    }
        
  @Override
   public Table connect(String tableName) throws LomikelException {
     return connect(tableName, "");
     }
             
  @Override
  public Table connect(String tableName,
                       String schemaName) throws LomikelException {
     return connect(tableName, schemaName, 0);
     }  
     
  @Override
  public Table connect(String tableName,
                       String schemaName,
                       int    timeout) throws LomikelException {
     return connect(tableName, schemaName, timeout, 0);
     }     
     
  @Override
  public Table connect(String tableName,
                       String schemaName,
                       int    timeout,
                       int    retries) throws LomikelException {
    // Table setup
    log.info("Connecting to " + tableName);
    setTableName(tableName);
    if (timeout > 0) {
      log.info("\tglobal timeout = " + timeout);
      _conf.setInt("hbase.rpc.timeout",                   timeout);
      _conf.setInt("hbase.client.scanner.timeout.period", timeout);
      _conf.setInt("hbase.client.operation.timeout",      timeout); 
      _conf.setInt("zookeeper.session.timeout",           timeout);
      _conf.setInt("hbase.client.pause",                  timeout); 
      }
    if (retries > 0) {
      log.info("\tglobal retries = " + retries);
      _conf.setInt("zookeeper.recovery.retry",            retries);
      _conf.setInt("hbase.client.retries.number",         retries);
      }
    try {
      _table = _connection.getTable(TableName.valueOf(tableName()));
      }
    catch (IOException e) {
      throw new LomikelException("Cannot connect to " + _table);
      }
    setLimit(      Integer.MAX_VALUE);
    setSearchLimit(Integer.MAX_VALUE);
    // Schema search
    if (schemaName == null) {
      log.info("Not using schema");
      }
    else {
      Map<String, Map<String, String>> schemas = null;
      try {
        if (schemaName.equals("")) {
          log.info("Using the most recent schema");
          schemas = scan(null,
                         "key:key:schema:prefix",
                         "*",
                         0,
                         false,
                         false);
          }
        else {
          log.info("Searching for schema " + schemaName);
          schemas = scan(schemaName,
                         null,
                         "*",
                         0,
                         false,
                         false);
          }
        }
      catch (Exception e) {
        log.error("Searching for schema " + schemaName + " failed", e);
        }
      if (schemas == null || schemas.size() == 0) {
        log.error("No schema found");
        }
      else if (schemas.size() > 1) {
        log.info("" + schemas.size() + " schemas found, choosing the most recent one");
        Set<Map.Entry<String, Map<String, String>>> schemasSet = schemas.entrySet();
        List<Map.Entry<String, Map<String, String>>> schemasList = new ArrayList<>(schemasSet);
        Map.Entry<String, Map<String, String>> schemaEntry = schemasList.get(schemasList.size() - 1);
        setSchema(new HBaseSchema(schemaEntry.getKey(),
                                  schemaEntry.getValue()));
        }
      else {
        setSchema(new HBaseSchema(schemas.entrySet().iterator().next().getKey(),
                                  schemas.entrySet().iterator().next().getValue()));
        }
      }
    return _table;
    }

  @Override
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
    
  // Search --------------------------------------------------------------------
                    
  /** Create new table.
    * @param tableName The name of new table.
    * @param families  The names of families.
    *                  Can contains the max number of stored version after <tt>:</tt>
    *                  (the default is <tt>1</tt> version, <tt>0</tt> gives <tt>Integer.MAX_VALUE</tt> versions).
    * @throws IOException If anything goes wrong. */
  public void create(String   tableName,
                     String[] families) throws IOException {
    setTableName(tableName);
    Admin admin = _connection.getAdmin();      
    TableDescriptorBuilder builder = TableDescriptorBuilder.newBuilder(TableName.valueOf(tableName()));  
    String familyName;
    int    familyVersions;
    for (String family : families) {
      if (family.contains(":")) {
        familyName     =                 family.split(":")[0];
        familyVersions = Integer.valueOf(family.split(":")[1]);
        if (familyVersions == 0) {
          familyVersions = Integer.MAX_VALUE;
          }
        builder.setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(Bytes.toBytes(familyName))
                                                             .setMaxVersions(familyVersions)
                                                             .build());
        }
      else {
        builder.setColumnFamily(ColumnFamilyDescriptorBuilder.newBuilder(Bytes.toBytes(family)).build());
        }
      }
    admin.createTable(builder.build());
    admin.close();
    log.info("Created table " + tableName() + "(" + String.join(",", families) + ")");
    }
              
  @Override
  public void delete(String key) {
    log.info("Deleting for key: " + key);
    Delete delete;
    for (String k : key.split(",")) {
      delete = new Delete(Bytes.toBytes(k.trim()));
      try {
        table().delete(delete);
        }
      catch (IOException e) {
        log.error("Cannot delete", e);
        }
      }
    }

  @Override
  public Map<String, Map<String, String>> scan(String    key,
                                               SearchMap searchMap,
                                               String    filter,
                                               long      start,
                                               long      stop,
                                               boolean   ifkey,
                                               boolean   iftime) {
    String searchMsg = "";
    if (searchMap != null) {
      searchMsg = searchMap.toString();
      }
    if (searchMsg.length() > 80) {
      searchMsg = searchMsg.substring(0, 80) + "...}";
      }
    log.info("Searching for key: " + key + 
             ", search: " + searchMsg + 
             ", filter: " + filter +
             ", interval: " + start + " ms - " + stop + " ms" +
             ", id/time: " + ifkey + "/" + iftime +
             ", searchLimit/resultLimit: " + searchLimit() + "/" + limit());
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
    String startKey = null;
    String stopKey  = null;
    if (key == null || !key.startsWith("schema")) {
      if (_evaluator != null) {
        filter = mergeColumns(filter, String.join(",", _evaluator.variables()));
        }
      filter = mergeColumns(filter, _alwaysColumns);
      }
    // Get
    if (key != null && !key.trim().equals("")) {
      Get get;
      Result r;
      for (String k : key.split(",")) {
        get = new Get(Bytes.toBytes(k.trim()));
        // Filter
        if (filter != null && !filter.trim().contains("*") && !filter.trim().equals("")) {
          for (String f : filter.split(",")) {
            fc = f.split(":");
            family = fc[0];
            column = fc[1];
            get.addColumn(Bytes.toBytes(family), Bytes.toBytes(column));
            }
          }
        result = new TreeMap<>();
        try {
          r = table().get(get);
          log.debug("" + r.size() + " entries found");
          addResult(r, result, filter, ifkey, iftime);
          results.put(k, result);
          processResults(results);
          }
        catch (IOException e) {
          log.error("Cannot search", e);
          }
        }
      }
    // Scan
    else {
      Scan scan = new Scan();
      // Filter
      if (filter != null && !filter.trim().contains("*") && !filter.trim().equals("")) {
        for (String f : filter.split(",")) {
          if (f.contains(":")) {
            fc = f.split(":");
            family = fc[0];
            column = fc[1];
            scan.addColumn(Bytes.toBytes(family), Bytes.toBytes(column));
            }
          }
        }
      // Time range
      try {
        scan.setTimeRange(start, stop);
        }
      catch (IOException e) {
        log.error("Cannot set time range " + start + " - " + stop);
        }
      // Search
      if (searchMap == null) {
        searchMap = new SearchMap();
        }
      // if (searchMap != null && !searchMap.isEmpty()) {
      if (searchMap != null) {
        List<Filter> filters = new ArrayList<>();
        String firstKey = null;
        String lastKey  = null;
        boolean onlyKeys = !searchMap.isEmpty(); // if empty searchMap => no keys, so not onlyKeys
        SortedSet<String> allKeys = new TreeSet<>();
        for (Map.Entry<String, String> entry : MapUtil.sortByValue(searchMap.map()).entrySet()) {
          fc = entry.getKey().split(":");
          family = fc[0];
          column = fc[1];
          if (family != null && !family.equals("key")) {
            scan.addColumn(Bytes.toBytes(family), Bytes.toBytes(column)); // adding search terms to columns
            }
          comparator = fc.length == 3 ? fc[2] : "default";
          value  = entry.getValue();
          if (family.equals("key") && column.equals("random")) {
            onlyKeys = false;
            filters.add(new RandomRowFilter(Float.parseFloat(value)));
            }
          else if (family.equals("key") && column.equals("startKey")) {
            startKey = value;
            }
          else if (family.equals("key") && column.equals("stopKey")) {
            stopKey = value;
            }
          else if (family.equals("key") && column.equals("key")) {
            for (String v : value.split(",")) {
              allKeys.add(v);
              switch (comparator) {
                case "exact":
                  filters.add(new RowFilter(CompareOp.EQUAL, new BinaryComparator(Bytes.toBytes(v))));
                  break;
                case "substring":
                  filters.add(new RowFilter(CompareOp.EQUAL, new SubstringComparator(v)));
                  onlyKeys = false;
                  break;
                case "regex":
                  filters.add(new RowFilter(CompareOp.EQUAL, new RegexStringComparator(v)));
                  onlyKeys = false;
                  break;
                default: // prefix
                  filters.add(new PrefixFilter(Bytes.toBytes(v)));
                }
              }
            }
          else {
            onlyKeys = false;
            for (String v : value.split(",")) {
              switch (comparator) {
                case "exact":
                  filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, Bytes.toBytes(v)));
                  break;
                case "prefix":
                  filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, new BinaryPrefixComparator(Bytes.toBytes(v))));
                  break;
                case "regex":
                  filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, new RegexStringComparator(v)));
                  break;
                default: // substring
                  filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, new SubstringComparator(v)));
                }
              }
            }
          }
        if (startKey != null || stopKey != null) {
          if (isReversed()) {
            if (stopKey != null) {
              scan.withStartRow(                              Bytes.toBytes(stopKey ) , true);
              }
            if (startKey != null) {
              scan.withStopRow(Bytes.unsignedCopyAndIncrement(Bytes.toBytes(startKey)), true);
              }
            }
          else {
            if (startKey != null) {
              scan.withStartRow(                              Bytes.toBytes(startKey) , true);
              }
            if (stopKey != null) {
              scan.withStopRow(Bytes.unsignedCopyAndIncrement(Bytes.toBytes(stopKey )), true);
              }
            }
          }
        else if (onlyKeys) {
          if (isReversed()) {
            scan.withStartRow(                              Bytes.toBytes(allKeys.last()),   true);
            scan.withStopRow(Bytes.unsignedCopyAndIncrement(Bytes.toBytes(allKeys.first())), true);
            }
          else {
            scan.withStartRow(                              Bytes.toBytes(allKeys.first()), true);
            scan.withStopRow(Bytes.unsignedCopyAndIncrement(Bytes.toBytes(allKeys.last())), true);
            }
          }
        if (_isRange && !onlyKeys) {
          log.warn("Range scan is ignored because incompatible with other arguments");
          _isRange = false;
          }
        FilterList filterList;
        if (_isRange) {
          log.info("Performing range scan");
          RowRange rr = new RowRange(                               Bytes.toBytes(allKeys.first()), true,
                                     Bytes.unsignedCopyAndIncrement(Bytes.toBytes(allKeys.last())), true);
          List<RowRange> lrr = new ArrayList<>();
          lrr.add(rr);
          filterList = new FilterList(_operator, new MultiRowRangeFilter(lrr));
          }
        else  {
          filterList = new FilterList(_operator, filters);  
          }
        scan.setFilter(filterList);
        }
      // Limit
      int limit0 = _limit0;
      if (_evaluator == null && (limit() < _limit0 || _limit0 == 0)) {
        limit0 = limit();
        }
      if (limit0 > 0) {
        scan.setLimit(limit0);
        if (schema() != null) {
          scan.setMaxResultSize(limit0 * schema().size());
          }
        }
      // Reversed
      scan.setReversed(isReversed());
      log.info("scan = " + scan);
      // Results
      try {
        _rs = table().getScanner(scan);
        int i = 0;
        for (Result r : _rs) {
          if (i >= limit()) {
            break;
            }
          result = new TreeMap<>();
          if (addResult(r, result, filter, ifkey, iftime)) {
            results.put(Bytes.toString(r.getRow()), result);
            processResults(results);
            i++;
            }
          }
        }
      catch (IOException e) {
        log.error("Cannot search", e);
        }
      }
    log.debug(results.size() + " results found in " + (System.currentTimeMillis() - time) + "ms");
    return results;
    } 

  @Override 
  // TBD: refactor with scan
  public Map<String, Map<String, Map<Long, String>>> scan3D(String    key,
                                                            SearchMap searchMap,
                                                            String    filter,
                                                            long      start,
                                                            long      stop,
                                                            boolean   ifkey,
                                                            boolean   iftime) {
    String searchMsg = "";
    if (searchMap != null) {
      searchMsg = searchMap.toString();
      }
    if (searchMsg.length() > 80) {
      searchMsg = searchMsg.substring(0, 80) + "...}";
      }
    log.info("Searching multiversion for key: " + key + 
             ", search: " + searchMsg + 
             ", filter: " + filter +
             ", interval: " + start + " ms - " + stop + " ms" +
             ", id/time: " + ifkey + "/" + iftime +
             ", searchLimit/resultLimit: " + searchLimit() + "/" + limit());
    long time = System.currentTimeMillis();
    if (stop == 0) {
      stop = System.currentTimeMillis();
      }
    Map<String, Map<String, Map<Long, String>>> results = new TreeMap<>();
    Map<String, Map<Long, String>> result;
    String[] fc;
    String family; 
    String column;
    String comparator;
    String value;
    String startKey = null;
    String stopKey  = null;
    if (key == null || !key.startsWith("schema")) {
      if (_evaluator != null) {
        filter = mergeColumns(filter, String.join(",", _evaluator.variables()));
        }
      filter = mergeColumns(filter, _alwaysColumns);
      }
    // Get
    if (key != null && !key.trim().equals("")) {
      Get get;
      Result r;
      for (String k : key.split(",")) {
        get = new Get(Bytes.toBytes(k.trim())).readAllVersions();
        // Filter
        if (filter != null && !filter.trim().contains("*") && !filter.trim().equals("")) {
          for (String f : filter.split(",")) {
            fc = f.split(":");
            family = fc[0];
            column = fc[1];
            get.addColumn(Bytes.toBytes(family), Bytes.toBytes(column));
            }
          }
        result = new TreeMap<>();
        try {
          r = table().get(get);
          log.debug("" + r.size() + " entries found");
          addResult3D(r, result, filter, ifkey, iftime);
          results.put(k, result);
          }
        catch (IOException e) {
          log.error("Cannot search", e);
          }
        }
      }
    // Scan
    else {
      Scan scan = new Scan().readAllVersions();
      // Filter
      if (filter != null && !filter.trim().contains("*") && !filter.trim().equals("")) {
        for (String f : filter.split(",")) {
          if (f.contains(":")) {
            fc = f.split(":");
            family = fc[0];
            column = fc[1];
            scan.addColumn(Bytes.toBytes(family), Bytes.toBytes(column));
            }
          }
        }
      // Time range
      try {
        scan.setTimeRange(start, stop);
        }
      catch (IOException e) {
        log.error("Cannot set time range " + start + " - " + stop);
        }
      // Search
      if (searchMap == null) {
        searchMap = new SearchMap();
        }
      // if (searchMap != null && !searchMap.isEmpty()) {
      if (searchMap != null) {
        List<Filter> filters = new ArrayList<>();
        String firstKey = null;
        String lastKey  = null;
        boolean onlyKeys = !searchMap.isEmpty(); // if empty searchMap => no keys, so not onlyKeys
        SortedSet<String> allKeys = new TreeSet<>();
        for (Map.Entry<String, String> entry : MapUtil.sortByValue(searchMap.map()).entrySet()) {
          fc = entry.getKey().split(":");
          family = fc[0];
          column = fc[1];
          if (family != null && !family.equals("key")) {
            scan.addColumn(Bytes.toBytes(family), Bytes.toBytes(column)); // adding search terms to columns
            }
          comparator = fc.length == 3 ? fc[2] : "default";
          value  = entry.getValue();
          if (family.equals("key") && column.equals("random")) {
            onlyKeys = false;
            filters.add(new RandomRowFilter(Float.parseFloat(value)));
            }
          else if (family.equals("key") && column.equals("startKey")) {
            startKey = value;
            }
          else if (family.equals("key") && column.equals("stopKey")) {
            stopKey = value;
            }
          else if (family.equals("key") && column.equals("key")) {
            for (String v : value.split(",")) {
              allKeys.add(v);
              switch (comparator) {
                case "exact":
                  filters.add(new RowFilter(CompareOp.EQUAL, new BinaryComparator(Bytes.toBytes(v))));
                  break;
                case "substring":
                  filters.add(new RowFilter(CompareOp.EQUAL, new SubstringComparator(v)));
                  onlyKeys = false;
                  break;
                case "regex":
                  filters.add(new RowFilter(CompareOp.EQUAL, new RegexStringComparator(v)));
                  onlyKeys = false;
                  break;
                default: // prefix
                  filters.add(new PrefixFilter(Bytes.toBytes(v)));
                }
              }
            }
          else {
            onlyKeys = false;
            for (String v : value.split(",")) {
              switch (comparator) {
                case "exact":
                  filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, Bytes.toBytes(v)));
                  break;
                case "prefix":
                  filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, new BinaryPrefixComparator(Bytes.toBytes(v))));
                  break;
                case "regex":
                  filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, new RegexStringComparator(v)));
                  break;
                default: // substring
                  filters.add(new SingleColumnValueFilter(Bytes.toBytes(family), Bytes.toBytes(column), CompareOp.EQUAL, new SubstringComparator(v)));
                }
              }
            }
          }
        if (startKey != null || stopKey != null) {
          if (isReversed()) {
            if (stopKey != null) {
              scan.withStartRow(                              Bytes.toBytes(stopKey ) , true);
              }
            if (startKey != null) {
              scan.withStopRow(Bytes.unsignedCopyAndIncrement(Bytes.toBytes(startKey)), true);
              }
            }
          else {
            if (startKey != null) {
              scan.withStartRow(                              Bytes.toBytes(startKey) , true);
              }
            if (stopKey != null) {
              scan.withStopRow(Bytes.unsignedCopyAndIncrement(Bytes.toBytes(stopKey )), true);
              }
            }
          }
        else if (onlyKeys) {
          if (isReversed()) {
            scan.withStartRow(                              Bytes.toBytes(allKeys.last()),   true);
            scan.withStopRow(Bytes.unsignedCopyAndIncrement(Bytes.toBytes(allKeys.first())), true);
            }
          else {
            scan.withStartRow(                              Bytes.toBytes(allKeys.first()), true);
            scan.withStopRow(Bytes.unsignedCopyAndIncrement(Bytes.toBytes(allKeys.last())), true);
            }
          }
        if (_isRange && !onlyKeys) {
          log.warn("Range scan is ignored because incompatible with other arguments");
          _isRange = false;
          }
        FilterList filterList;
        if (_isRange) {
          log.info("Performing range scan");
          RowRange rr = new RowRange(                               Bytes.toBytes(allKeys.first()), true,
                                     Bytes.unsignedCopyAndIncrement(Bytes.toBytes(allKeys.last())), true);
          List<RowRange> lrr = new ArrayList<>();
          lrr.add(rr);
          filterList = new FilterList(_operator, new MultiRowRangeFilter(lrr));
          }
        else  {
          filterList = new FilterList(_operator, filters);  
          }
        scan.setFilter(filterList);
        }
      // Limit
      int limit0 = _limit0;
      if (_evaluator == null && (limit() < _limit0 || _limit0 == 0)) {
        limit0 = limit();
        }
      if (limit0 > 0) {
        scan.setLimit(limit0);
        if (schema() != null) {
          scan.setMaxResultSize(limit0 * schema().size());
          }
        }
      // Reversed
      scan.setReversed(isReversed());
      log.info("scan = " + scan);
      // Results
      try {
        _rs = table().getScanner(scan);
        int i = 0;
        for (Result r : _rs) {
          if (i >= limit()) {
            break;
            }
          result = new TreeMap<>();
          if (addResult3D(r, result, filter, ifkey, iftime)) {
            results.put(Bytes.toString(r.getRow()), result);
            i++;
            }
          }
        }
      catch (IOException e) {
        log.error("Cannot search", e);
        }
      }
    log.debug(results.size() + " results found in " + (System.currentTimeMillis() - time) + "ms");
    return results;
    }    
    
  /** Add {@link Result} into result {@link Map}.
    * To be used with single-version results.
    * @param r       The {@link Result} to add.
    * @param result  The {@link Map} of results <tt>familty:column-&gt;value</tt>.
    * @param filter  The comma-separated list of names of required values as <tt>family:column</tt>.
    *                It can be <tt>null</tt>.
    * @param ifkey   Whether add also entries keys (as <tt>key:key</tt>).
    * @param iftime  Whether add also entries timestamps (as <tt>key:time</tt>).
    * @return        Whether the result has been added. */
  protected boolean addResult(Result              r,
                              Map<String, String> result,
                              String              filter,
                              boolean             ifkey,
                              boolean             iftime) {
    if (r == null) {
      return false;
      }
    String key = Bytes.toString(r.getRow());
    // when schema is already loaded => ignore schema rows
    if (key.startsWith("schema") && schema() != null) {
      return false;
      }
    // evaluate non-schema rows
    if (!key.startsWith("schema") &&_evaluator != null && !evaluateResult(r)) {
      return false;
      }
    String[] ff;
    String ref;
    if (r.getRow() != null) {
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
          if (filter == null || filter.contains("*") || filter.contains(column)) {
            // searching for schema
            if (key.startsWith("schema")) {
              result.put(column, Bytes.toString(e.getValue()));
              }
            // known schema
            else if (schema() != null && schema().type(column) != null) {
              // binary
              if (family.equals("b")) {
                ref = "binary:" + key + ":" + Bytes.toString(e.getKey());
                result.put(column, ref);
                _repository.put(ref, schema().decode2Content(column, e.getValue()).asBytes());
                }
              // not binary
              else {
                result.put(column, schema().decode(column, e.getValue()));
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
        if (dateFormat() == null) {
          result.put("key:time", String.valueOf(r.rawCells()[0].getTimestamp()));
          }
        else {
          result.put("key:time", DateTimeManagement.time2String(r.rawCells()[0].getTimestamp()));
          }
        }
      }
    return true;
    }

  /** Add {@link Result} into result {@link Map}.
    * To be used with multiversion results.
    * @param r       The {@link Result} to add.
    * @param result  The {@link Map} of results <tt>familty:column-&gt;value</tt>.
    * @param filter  The comma-separated list of names of required values as <tt>family:column</tt>.
    *                It can be <tt>null</tt>.
    * @param ifkey   Whether add also entries keys (as <tt>key:key</tt>).
    * @param iftime  Whether add also entries timestamps (as <tt>key:time</tt>).
    * @return        Whether the result has been added. */
  // TBD: refactor with the other addResult
  protected boolean addResult3D(Result                         r,
                                Map<String, Map<Long, String>> result,
                                String                         filter,
                                boolean                        ifkey,
                                boolean                        iftime) {
    if (r == null) {
      return false;
      }
    String key = Bytes.toString(r.getRow());
    // when schema is already loaded => ignore schema rows
    if (key.startsWith("schema") && schema() != null) {
      return false;
      }
    // evaluate non-schema rows
    if (!key.startsWith("schema") &&_evaluator != null && !evaluateResult(r)) {
      return false;
      }
    String[] ff;
    String ref;
    Map<Long, String> t;
    Set<Long> allTS = new TreeSet<>();
    if (r.getRow() != null) {
      String family;
      String column;
      NavigableMap<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>>	resultMap = r.getMap();
      for (Map.Entry<byte[], NavigableMap<byte[], NavigableMap<Long, byte[]>>> entry : resultMap.entrySet()) {
        family = Bytes.toString(entry.getKey());
        for (Map.Entry<byte[], NavigableMap<Long, byte[]>> e : entry.getValue().entrySet()) {
          column = family + ":" + Bytes.toString(e.getKey());
          if (filter == null || filter.contains("*") || filter.contains(column)) {
            // searching for schema
            if (key.startsWith("schema")) {
              log.error("3D cell cannot be used for schema");
              }
            // known schema
            else if (schema() != null && schema().type(column) != null) {
              // binary
              if (family.equals("b")) {
                log.error("3D cell cannot be used for binary data");
                }
              // not binary
              else {
                t = new TreeMap<>();
                for (Map.Entry<Long, byte[]> ee : e.getValue().entrySet()) {
                  t.put(ee.getKey(), schema().decode(column, ee.getValue()));
                  if (ifkey || iftime) {
                    allTS.add(ee.getKey());
                    }
                  }
                result.put(column, t);
                }
              }
            // no schema
            else {
              t = new TreeMap<>();
              for (Map.Entry<Long, byte[]> ee : e.getValue().entrySet()) {
                t.put(ee.getKey(), Bytes.toString(ee.getValue()));
                if (ifkey || iftime) {
                  allTS.add(ee.getKey());
                  }
                }
              result.put(column, t);
              }
            }
          }
        }
      if (ifkey) {
        t = new TreeMap<>();
        for (Long ts : allTS) {
          t.put(ts, key);
          }
        result.put("key:key", t);
        }
      if (iftime) {
        t = new TreeMap<>();
        for (Long ts : allTS) {
          if (dateFormat() == null) {
            t.put(ts, String.valueOf(ts));
            }
          else {
            t.put(ts, DateTimeManagement.time2String(ts));
            }
          }
        result.put("key:time", t);   
        }
      }
    return true;
    }
    
  /** Process results after each insertion of new result.
    * May be imlemented in subclasses or setup via {@link #setProcessor}.
    * @param results The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  protected void processResults(Map<String, Map<String, String>> results) {
    if (_processor != null) {
      _processor.processResults(results);
      }
    }
   
  /** Set {@link HBaseProcessor}.
    * @param resultsProcessor The {@link HBaseProcessor} to set. */
  public void setProcessor(HBaseProcessor processor) {
    _processor = processor;
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
        value = schema().decode(column, e.getValue());
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
    
  /** Give n-dim scatter plot from multi-versioned columns,
    * associated by their timestamps.
    * @param rowkey   The rowkey to get the scatter for.
    * @param columns  The columns to get.
    *                 If <tt>null</tt> or <tt>*</tt>, all available columns will be delivered.
    * @param ifkey    Whether add also entries keys (as <tt>key:key</tt>).
    * @param iftime   Whether add also entries timestamps (as <tt>key:time</tt>).
    * @param skipNull Whether remove rows and columns all all <tt>null</tt> entries.
    * @return         The resulting {@link DataFrame}. */
  public DataFrame<Object> search3D(String  rowkey,
                                    String  columns,
                                    boolean ifkey,
                                    boolean iftime,
                                    boolean skipAllNull) {
    return search3D(rowkey, null, columns, ifkey, iftime, skipAllNull);
    }
  
  /** Give n-dim scatter plot from multi-versioned columns,
    * associated by their timestamps.
    * @param rowkey   The rowkey to get the scatter for.
    * @param xColumn  The column to get as x-axis (index).
    *                 if <tt>null</tt>, the automatic index will be created.
    * @param columns  The columns to get as y-axis.
    *                 If <tt>null</tt> or <tt>*</tt>, all available columns will be delivered.
    *                 Values without correspondence in <tt>xColumn</tt> will be ignored.
    * @param ifkey    Whether add also entries keys (as <tt>key:key</tt>).
    * @param iftime   Whether add also entries timestamps (as <tt>key:time</tt>).
    * @param skipNull Whether remove rows and columns all all <tt>null</tt> entries.
    * @return         The resulting x -&gt; [y] {@link DataFrame}. */
  public DataFrame<Object> search3D(String  rowkey,
                                    String  xColumn,
                                    String  columns,
                                    boolean ifkey,
                                    boolean iftime,
                                    boolean skipAllNull) {
    log.info("Searching curves " + xColumn + " * " + columns + " for " + rowkey);
    if (columns == null || columns.equals("*")) {
      columns = "";
      boolean first = true;
      for (String column : schema().columnNames()) {
        if (first) {
          first = false;
          }
        else {
          columns += ",";
          }
        columns += column;
        }
      }
    Map<String, Map<String, Map<Long, String>>> results = scan3D(rowkey,
                                                                 "",
                                                                 ((xColumn != null) ? (xColumn + ",") : "") + columns,
                                                                 0,
                                                                 0,
                                                                 ifkey,
                                                                 iftime);
    List<Object> list = new ArrayList<>();
    String columnsAll = columns + (ifkey ? ",key:key" : "") + (iftime ? ",key:time" : "");
    DataFrame df = new DataFrame(columnsAll.split(","));
    boolean hasVal;
    if (results != null && !results.isEmpty() && results.containsKey(rowkey)) {
      Map<String, Map<Long, String>> result = results.get(rowkey);
      // xColumn as index
      if (xColumn == null) {
        Set<Long> timestamps = new TreeSet<>();
        for (Map.Entry<String, Map<Long, String>> item : result.entrySet()) {
          for (Map.Entry<Long, String> value : item.getValue().entrySet()) {
            timestamps.add(value.getKey());
            }
          }
        Map<Long, String> columnsVal;
        for (Long timestamp : timestamps) {
          list.clear();
          hasVal = false;
          for (String column : columns.split(",")) {
            if (result.containsKey(column)) {
              columnsVal = result.get(column);
              if (columnsVal.containsKey(timestamp)) {
                list.add(schema().castColumn(column, columnsVal.get(timestamp)));
                hasVal = true;
                }
              else {
                list.add(null);
                }
              }
            }
          if (ifkey) {
            list.add(result.get("key:key").get(timestamp));
            }
          if (iftime) {
            list.add(result.get("key:time").get(timestamp));
            }
          if (!skipAllNull || hasVal) {
            df.append(list);
            }                    
          }
        }
      // automatic index
      else {
        Map<Long, String> xs = result.get(xColumn);
        Map<Long, String> ys;
        for (Map.Entry<Long, String> x : xs.entrySet()) {
          list.clear();
          hasVal = false;
          for (String column : columns.split(",")) {
            ys = result.get(column);
            if (ys != null && ys.containsKey(x.getKey())) {
              list.add(schema().castColumn(column, ys.get(x.getKey())));           
              hasVal = true;
              }
            else {
              list.add(null);
              }
            }
          if (ifkey) {
            list.add(result.get("key:key").get(x.getKey()));
            }
          if (iftime) {
            list.add(result.get("key:time").get(x.getKey()));
            }
          if (!skipAllNull || hasVal) {
            df.append(schema().castColumn(xColumn, x.getValue()), list);
            }
          }
        }
      }
    else {
      log.warn("Nothing found");
      }
    if (skipAllNull) {
      for (String column : columns.split(",")) {
        if (df.col(column).stream().allMatch(e -> e == null)) {
          df = df.drop(column);
          }
        }
      }
    return df;
   }
   
  /** Give the timeline for the column.
    * @param columnName The name of the column.
    * @param search   The search terms as <tt>family:column:value,...</tt>.
    *                 Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                 {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                 among <tt>exact,prefix,substring,regex</tt>.
    *                 The default for key is <tt>prefix</tt>,
    *                 the default for columns is <tt>substring</tt>.
    *                 It can be <tt>null</tt>.
    *                 All searches are executed as prefix searches.    
    * @return         The {@link Set} of {@link Pair}s of timestamp-value. */
  public Set<Pair<String, String>> timeline(String columnName,
                                            String search) {
    log.info("Getting timeline of " + columnName + " with " + search);
    Set<Pair<String, String>> tl = new TreeSet<>();
    Map<String, Map<String, String>> results = scan(null, search, columnName, 0, false, true);
    Pair<String, String> p;
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      if (!entry.getKey().startsWith("schema")) { 
        p = Pair.of(entry.getValue().get("key:time"),
                    entry.getValue().get(columnName));
        tl.add(p);
        }
      }
    return tl;
    }
    
  /** Give all recent values of the column.
    * Results are ordered by the row key, so evetual limits on results
    * number will be apllied to them and not to the time.
    * @param columnName     The name of the column.
    * @param substringValue The column value substring to search for.
    * @param minutes        How far into the past it should search (in minutes). 
    * @param getValues      Whether to get column values or row keys.
    * @return               The {@link Set} of different values of that column. */
  public Set<String> latests(String  columnName,
                             String  prefixValue,
                             long    minutes,
                             boolean getValues) {
    log.info("Getting " + columnName + " of rows prefixed by " + prefixValue + " from last " + minutes + " minutes");
    Set<String> l = new TreeSet<>();
    String search = "";
    if (prefixValue != null) {
      search += columnName + ":" + prefixValue + ":prefix";
      }
    Map<String, Map<String, String>> results = scan(null, search, columnName, minutes, false, false);
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
      _formula   = null;
      _evaluator = null;
      log.info("Unsetting evaluation formula");
      return;
      }
    log.info("Setting evaluation formula '" + formula + "'");
    if (schema() == null) {
      log.error("Evaluation can be set only for known Schema");
      return;
      }
    try {
      _evaluator = new HBaseEvaluator(schema());
      _evaluator.setVariables(formula);
      _formula   = formula;
      }
    catch (LomikelException e) {
      log.error("Evaluator cannot be set", e);
      }
    }
      
  /** Add a row into table.
    * @param key    The row key.
    * @param values The column values as family:column:value.
    *               Use schema if defined.
    * @throws IOException If anything goes wrong. */
  public void put(String   key,
                  String[] values) throws IOException {
    Put put = new Put(Bytes.toBytes(key));
    String value;
    String[] w;
    for (String v : values) {
      w = v.split(":");
      value = w[2];
      if (schema() != null) {
        put.addColumn(Bytes.toBytes(w[0]), Bytes.toBytes(w[1]), schema().encode(w[0] +  ":" +  w[1], value).bytes());
        }
      else {
        put.addColumn(Bytes.toBytes(w[0]), Bytes.toBytes(w[1]), Bytes.toBytes(value));
        }
      }
    table().put(put);
    }
    
  /** Add rows into table in a batch.
    * @param rows   The {@link Map} rowkey -&gt; (family:column -&gt; value).
    *               Use schema if defined.
    * @throws IOException If anything goes wrong. */
  public void put(Map<String, Map<String, String>> rows) throws IOException {
    List<Put> puts = new ArrayList<Put>();
    String key;
    Put put;
    String[] w;
    String value;
    for (Map.Entry<String, Map<String, String>> row : rows.entrySet()) {
      key = row.getKey();
      put = new Put(Bytes.toBytes(key));
      for (Map.Entry<String, String> cell : row.getValue().entrySet()) {
        w = cell.getKey().split(":");
        value  = cell.getValue();
        if (schema() != null) {
          put.addColumn(Bytes.toBytes(w[0]), Bytes.toBytes(w[1]), schema().encode(w[0] +  ":" +  w[1], value).bytes());
          }
        else {
          put.addColumn(Bytes.toBytes(w[0]), Bytes.toBytes(w[1]), Bytes.toBytes(value));
          }
        }
      puts.add(put);  
      }
    table().put(puts);
    }
  
  // Aux -----------------------------------------------------------------------
        
  /** Set the limit for the number of searched results (before eventual evaluation).
    * @param limit The limit for the number of searched esults. */
  public void setSearchLimit(int limit) {
    log.info("Setting search limit " + limit);
    _limit0 = limit;
    }
    
  /** Give the limit for the number of searched results (before eventual evaluation).
    * @retrun The limit for the number of searched esults. */
  public int searchLimit() {
    return _limit0;
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
    * @param dateFormat The result timestamp format.
    *                  The default is the native HBase format (ms). */
  public void setDateFormat(String dateFormat) {
    log.info("Setting Date format " + dateFormat);
    _dateFormat = dateFormat;
    }
    
 /** Give the table {@link BinaryDataRepository}.
    * @return The used {@link BinaryDataRepository}. */
  public BinaryDataRepository repository() {
    return _repository;
    }
    
  /** Set the table {@link BinaryDataRepository}.
    * @param repository The used {@link BinaryDataRepository}. */
  public void setRepository(BinaryDataRepository repository) {
    _repository = repository;
    }
       
  /** Increment <tt>byte[]</tt>.
    * @param value The origibal value.
    * @return      The incremented value. */
  //public static byte[] incrementBytes(final byte[] value) {
  //  byte[] newValue = Arrays.copyOf(value, value.length);
  //  for (int i = 0; i < newValue.length; i++) {
  //    int val = newValue[newValue.length - i - 1] & 0x0ff;
  //    int total = val + 1;
  //    boolean carry = false;
  //    if (total > 255) {
  //      carry = true;
  //      total %= 256;
  //      }
  //    newValue[newValue.length - i - 1] = (byte) total;
  //    if (!carry) {
  //      return newValue;
  //      }
  //    }
  //  return newValue;
  //  }

  /** Give {@link Table}.
    * @return The {@link Table}. */
  public Table table() {
    return _table;
    }

  /** Set connection parameters.
   * @param zookeepers The zookeepers to use. 
   * @param clientPort The client port to use. */
  protected void setup(String zookeepers,
                       String clientPort) {
    _zookeepers = zookeepers;
    _clientPort = clientPort;
    }
    
  /** Give the used zookeepers.
    * @return The used zookeepers. */
  protected String zookeepers() {
    return _zookeepers;
    }
    
  /** Give the used client port.
    * @return The used client port. */
  protected String clientPort() {
    return _clientPort;
    }
    
  /** Specify if the search should return all results between stricy results.
    * @param isRange If the search should be considered as an inclusive range-search. */
  public void setRangeScan(boolean isRange) {
    _isRange = isRange;
    }
    
  /** Tell if the search should return all results between stricy results.
    * @return If the search should be considered as an inclusive range-search. */
  public boolean isRangeScan() {
    return _isRange;
    }

  /** Set columns to show in any case, regardless further filters.
    * @param columns The comma-separated list of columns to show in any case, regardless further filters.
    */
  public void setAlwaysColumns(String columns) {
    _alwaysColumns = columns;
    log.info("Setting always columns " + columns );
    }
  
  /** Add columns to show in any case, regardless further filters.
    * @param columns The comma-separated list of columns to show in any case, regardless further filters. */
  public void addAlwaysColumns(String columns) {
    _alwaysColumns = mergeColumns(_alwaysColumns, columns);
    log.info("Adding always columns " + columns + " => " + _alwaysColumns);
    }
  
  /** Merge two comma-separated list of columns.
    * <tt>null</tt> or <tt>*</tt> in either input gives <tt>null</tt> (i.e. all)
    * on output.
    * @param columns1 The first comma-separated list of columns.
    * @param columns2 The second comma-separated list of columns.
    * @return         The result comma-separated list of columns. */
  // TBD: check for releated columns, wrong ,....
  private String mergeColumns(String columns1,
                              String columns2) {
    String columns = null;
    columns = ((columns1 == null) ? "" : columns1)
              + "," +
              ((columns2 == null) ? "" : columns2);
    columns = columns.replaceAll(",,", ",").replaceAll("^,", "").replaceAll(",$", "");
    log.info("Merging " + columns1 + " + " + columns2 + " => " + columns);
    return columns;
    }
    
  /** Give the actual {@link ResultScanner}.
    * @return The actual {@link ResultScanner}
    *         after <tt>limit</tt> results already been read. */
  public ResultScanner resultScanner() {
    return _rs;
    }
    
  /** Give attached {@link HBaseEvaluator}.
    * @return The attached {@link HBaseEvaluator}. */
  public HBaseEvaluator evaluator() {
    return _evaluator;
    }
   
  /** Give the date format string.
    * @return the date format string or <tt>null</tt>. */
  public String dateFormat() {
    return _dateFormat;
    }
 
  /** Give the ${link Configuration}.
    * @return The ${link Configuration}. */
  public Configuration conf() {
    return _conf;
    }
    
  private Table _table;
  
  private Configuration _conf;
  
  private Connection _connection;
  
  private ResultScanner _rs;              
  
  private String _zookeepers;
  
  private String _clientPort;
   
  private boolean _isRange = false;
  
  private String _alwaysColumns = "";
  
  private int _limit0 = 0;
 
  private String _dateFormat = null;
  
  private FilterList.Operator _operator = FilterList.Operator.MUST_PASS_ALL;
    
  private BinaryDataRepository _repository = new BinaryDataRepository();  

  private HBaseEvaluator _evaluator;
  
  private HBaseProcessor _processor;
  
  private String _formula;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(HBaseClient.class);

  }
