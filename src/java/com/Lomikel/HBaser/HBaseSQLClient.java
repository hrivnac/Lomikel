package com.Lomikel.HBaser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.DateTimeManagement;
import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.ByteArray;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.DB.Schema;
import com.Lomikel.DB.Client;
import com.Lomikel.DB.SearchMap;

// HBase
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName ;
import org.apache.hadoop.hbase.client.Admin;
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

// SQL
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

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
import org.apache.log4j.Logger;

/** <code>HBaseSQLClient</code> adds SQL-search possibility and SQL Phoenix upsert
  * possibility to {@link HBaseClient}. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseSQLClient extends HBaseClient {
  
  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public HBaseSQLClient(String zookeepers,
                        String clientPort) throws LomikelException {
    super(zookeepers, clientPort);
    }
        
  /** Create and connect to HBase.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public HBaseSQLClient(String zookeepers,
                        int    clientPort) throws LomikelException {
    super(zookeepers, clientPort);
    }
    
  /** Create and connect to HBase.
    * @param url The HBase url.
    * @throws LomikelException If anything goes wrong. */
  public HBaseSQLClient(String url) throws LomikelException {
    super(url);
    }
         
  /** Get row(s) using SQL query.
    * The Phoenix SQL View should be already created using {@link #sqlTableCreationCommand}. 
    * @param sql     The (Phoenix) SQL query.
    *                The <code>ROWKEY</code> should be included in the <code>SELECT<code>
    *                part (explicitely or implicitely).
    *                The small and mixed case variables should be closed in quotation marks. 
    * @param ifkey   Whether add also entries keys (as <tt>key:key</tt>).
    * @return        The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  // TBD: handle binary columns
  // TBD: handle iftime
  // TBD: handle WHERE
  public Map<String, Map<String, String>> scan(String    sql,
                                               boolean   ifkey) {
    Map<String, Map<String, String>> results = new TreeMap<>();
    Map<String, String> result;
    Statement stmt;
    try {
      stmt = conn().createStatement();
      }
    catch (SQLException e) {
      log.error("Cannot create statement", e);
      return null;
      }
    try {
      ResultSet rs = stmt.executeQuery(sql);
      ResultSetMetaData rsmd = rs.getMetaData();
      int n = rsmd.getColumnCount();
      while (rs.next()) {
        result = new TreeMap<>();
        if (addResult(rs, rsmd, result, ifkey)) {
          results.put(rs.getString("rowkey"), result);
          }
        }
      }
    catch (SQLException e ) {
      log.error("Cannot execute: " + sql, e);
      } 
    return results;
    }
    
  /** Add {@link Result} into result {@link Map}.
    * @param rs      The {@link ResultSet} to add (the current row).
    * @param rsmd    The {@link ResultSetMetaData}.
    * @param result  The {@link Map} of results <tt>familty:column-&gt;value</tt>.
    * @param ifkey   Whether add also entries keys (as <tt>key:key</tt>).
    * @return        Whether the result has been added. */
  private boolean addResult(ResultSet           rs,
                            ResultSetMetaData   rsmd,
                            Map<String, String> result,
                            boolean             ifkey) throws SQLException {
    String columnName;
    int n = rsmd.getColumnCount();
    boolean isSchema = false;
    if (rs.getString("ROWKEY").startsWith("schema")) {
      isSchema = true;
      }
    for (int i = 1; i <= n; i++) {
      columnName = rsmd.getColumnName(i);
      if (columnName.equals("ROWKEY")) {
        if (ifkey) {
          result.put("key:key", rs.getString(i));
          }
        }
      else if (!isSchema && _simpleSchema != null && _simpleSchema.type(columnName) != null) {
        result.put(rsmd.getColumnName(i), _simpleSchema.decode(columnName, rs.getBytes(i)));
        }
      else {
        result.put(columnName, rs.getString(i));
        }
      }
    return true;
    }
    
  /** Give SQL view creation command for this HBase table.
    * It creates the Phoenix SQL view of the the current HBase table.
    * @return The SQL view creation command for this HBase table. */
  public String sqlViewCreationCommand() {
    HBaseSchema hs = (HBaseSchema)schema();
    return hs.toSQLView(table().getName().getNameAsString());
    }
   
  /** Give SQL table creation command for this HBase table.
    * It creates the SQL tabel with the same properties are the current HBase table.
    * Using the default table name.
    * @return The SQL table creation command for this HBase table. */
  public String sqlTableCreationCommand() {
    return sqlTableCreationCommand(null);
    }
    
  /** Give SQL table creation command for this HBase table.
    * It creates the SQL tabel with the same properties are the current HBase table.
    * @param sqlTableTame The SQL table name. Empty or <tt>null</tt> will use the default name.
    * @return The SQL table creation command for this HBase table. */
  public String sqlTableCreationCommand(String sqlTableName) {
    String stn = tableName();
    if (sqlTableName != null && !sqlTableName.trim().equals("")) {
      stn = sqlTableName;
      }
    return schema().toSQL(stn);
    }

  /** Replicate row(s) into Phoenix SQL table.
    * @param key          The row key. Disables other search terms.
    *                     It can be <tt>null</tt>.
    * @param search       The search terms as <tt>family:column:value,...</tt>.
    *                     Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                     <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                     {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                     among <tt>exact,prefix,substring,regex</tt>.
    *                     The default for key is <tt>prefix</tt>,
    *                     the default for columns is <tt>substring</tt>.
    *                     The randomiser can be added with <tt>random:random:chance</tt>.
    *                     It can be <tt>null</tt>.
    *                     All searches are executed as prefix searches.    
    * @param filter       The names of required values as <tt>family:column,...</tt>.
    *                     <tt>*</tt> = all.
    * @param start        The time period start timestamp in <tt>ms</tt>.
    *                     <tt>0</tt> means since the beginning.
    * @param stop         The time period stop timestamp in <tt>ms</tt>.
    *                     <tt>0</tt> means till now.
    * @param ifkey        Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime       Whether give also entries timestamps (as <tt>key:time</tt>).
    * @param sqlTableName The SQL table name. Empty or <tt>null</tt> will use the default name.
    * @return             The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan2SQL(String    key,
                                                   String    search,
                                                   String    filter,
                                                   long      start,
                                                   long      stop,
                                                   boolean   ifkey,
                                                   boolean   iftime,
                                                   String    sqlTableName) {
    log.info("Upserting results into SQL Phoenix table " + _sqlTableName + ", not returning results");
    _sqlTableName = sqlTableName;
    Map<String, Map<String, String>> results = scan(key, search, filter, start, stop, ifkey, iftime);
    _sqlTableName = null;
    return results;
    }
    
  /** Depending on <tt>_upsert</tt>, upsert results into Phoenix SQL table and clean the {@link Map}. */  
  @Override
  protected void processResults(Map<String, Map<String, String>> results) {
    if (_sqlTableName != null) {
      results2SQL(results, _sqlTableName);
      results.clear();
      }
    }

  /** Results presented as an SQL command.
    * The command can be used for SQL tables created with {@link Schema#toSQL} method.
    * Using the default table name.
    * @param results The {@link Map} of results.
    * @return        The result as a {@link List}. */
  public void results2SQL(Map<String, Map<String, String>> results) {
    results2SQL(results, null);
    }
    
  /** Results presented as an SQL command.
    * The command can be used for SQL tables created with {@link Schema#toSQL} method.
    * @param results      The {@link Map} of results.
    * @param sqlTableName The SQL table name. Empty or <tt>null</tt> will use the default name. */
  public void results2SQL(Map<String, Map<String, String>> results,
                          String                           sqlTableName) {
    String stn = tableName();
    if (sqlTableName != null && !sqlTableName.trim().equals("")) {
      stn = sqlTableName;
      }
    log.info("Upserting into " + stn);
    String sql;
    List<String> names  = new ArrayList<>();
    List<String> values = new ArrayList<>();
    short n = 0;
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      sql = "UPSERT INTO " + stn;
      names.clear();
      values.clear();
      names.add("ROWKEY");
      values.add("'" + entry.getKey() + "'");
      for (Map.Entry<String, String> cell : entry.getValue().entrySet()) {
        if (cell.getKey().equals("key:key")) {
          // already added
          }
        else if (cell.getKey().equals("key:time")) {
          names.add("ROWTIME");
          values.add("'" + cell.getValue() + "'");
          }
        else if (!schema().contains(cell.getKey())) {
          log.warn("The column " + cell.getKey() + " is not covered by " + schema().name() + ", so ignored.");
          // column not in schema
          }
        else if (cell.getValue().split(":")[0].equals("binary")) {
          names.add(cell.getKey().split(":")[1]);
          values.add("'" + repository().get64(cell.getValue()) + "'");
          }
        else {
          names.add(cell.getKey().split(":")[1]);
          if (schema().isNumber(cell.getKey())) {
            values.add(cell.getValue());
            }
          else {
            values.add("'" + cell.getValue() + "'");
            }
          }
        }
      sql += " (" + String.join(",", names) + ") VALUES(" + String.join(",", values) + ")";
      upsert(sql);
      n++;
      }
    try {
      conn().commit();
      log.info("" + n + " upserts commited");
      }
    catch (SQLException e) {
      log.error("Cannot commit", e);
      }
    }

  /** Upsert into connected SQL Phoenix database.
    * @param sql The SQL <tt>upsert</tt> command. */
  private void upsert(String sql) {
    Statement stmt;
    try {
      stmt = conn().createStatement();
      }
    catch (SQLException e) {
      log.error("Cannot create statement", e);
      return;
      }
    try {
      stmt.executeUpdate(sql);
      }
    catch (SQLException e ) {
      log.error("Cannot execute: " + sql, e);
      } 
    }    
    
  /** Give JDBC {@link Connection} to Phoenix database.
    * @return The JDBC {@link Connection} to Phoenix database. */
  private Connection conn() {
    if (_conn == null) {
      log.info("Opening " + zookeepers() + " on port " + clientPort() + " via Phoenix");
      try {
        Class.forName("org.apache.phoenix.jdbc.PhoenixDriver");
        //Class.forName("org.apache.phoenix.queryserver.client.Driver");
        }
      catch (ClassNotFoundException e) {
        log.error("Cannot find Phoenix JDBC driver", e);
        return null;
        }
      try {
        _conn = DriverManager.getConnection("jdbc:phoenix:" + zookeepers() + ":" + clientPort()); 
        //_conn = DriverManager.getConnection("jdbc:phoenix:thin:url=http://" + zookeepers() + ":" + clientPort() + ";serializa‌​tion=PROTOBUF"); 
        }
      catch (SQLException e) {
        log.error("Cannot open connection", e);
        return null;
        }
      if (schema() instanceof HBaseSchema && schema().size() > 0) {
        _simpleSchema = (HBaseSchema)schema().simpleSchema();
        }
      }
    return _conn;
    }
    
  @Override
  public void close() {
    log.debug("Closing");
    try {
      _conn.close();
      }
    catch (SQLException e) {
      log.warn("Cannot close JDBC", e);
      }
    _conn = null;
    super.close();
    }
    
  private HBaseSchema _simpleSchema; 
    
  private Connection _conn = null;  
  
  private String _sqlTableName = null;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseSQLClient.class);

  }
