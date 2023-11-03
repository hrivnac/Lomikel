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

/** <code>HBaseSQLClient</code> adds SQL-search possibility to {@link HBaseClient}. 
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
    * @param sql     The (Phoenix) SQL query.
    * @param ifkey   Whether add also entries keys (as <tt>key:key</tt>).
    * @return        The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  // TBD: handle binary columns
  // TBD: handle iftime
  public Map<String, Map<String, String>> scan(String    sql,
                                               boolean   ifkey) {
    Map<String, Map<String, String>> results = new TreeMap<>();
    Map<String, String> result;
    if (_conn == null) {
      log.info("Opening " + zookeepers() + " on port " + clientPort() + " via Phoenix");
      try {
        Class.forName("org.apache.phoenix.jdbc.PhoenixDriver");
        //Class.forName("org.apache.phoenix.queryserver.client.Driver");
        }
      catch (ClassNotFoundException e) {
        log.error("Cannot find Phoenix JDBC driver", e);
        return results;
        }
      try {
        _conn = DriverManager.getConnection("jdbc:phoenix:" + zookeepers() + ":" + clientPort()); 
        //_conn = DriverManager.getConnection("jdbc:phoenix:thin:url=http://" + zookeepers() + ":" + clientPort() + ";serializa‌​tion=PROTOBUF"); 
        }
      catch (SQLException e) {
        log.error("Cannot open connection", e);
        return results;
        }
      if (schema() instanceof HBaseSchema && schema().size() > 0) {
        _simpleSchema = (HBaseSchema)schema().simpleSchema();
        }
      }
    Statement stmt;
    try {
      stmt = _conn.createStatement();
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
  
  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseSQLClient.class);

  }
