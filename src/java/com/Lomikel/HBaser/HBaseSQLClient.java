package com.Lomikel.HBaser;

import com.Lomikel.Utils.LomikelException;
import com.Lomikel.DB.Schema;

// HBase
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;

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
import java.util.Map;  
import java.util.TreeMap;  

// Log4J
import org.apache.log4j.Logger;

/** <code>HBaseSQLClient</code> adds SQL-search possibility and SQL Phoenix upsert
  * possibility to {@link HBaseClient}. 
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: reuse Phoenixer
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
    return hs.toSQLView(tableName());
    }
   
  /** Give SQL table creation command for this HBase table.
    * It creates the SQL tabel with the same properties are the current HBase table.
    * Using the default table name.
    * @return The SQL table creation command for this HBase table. */
  public String sqlTableCreationCommand() {
    return schema().toSQL(tableName() + "_" + schema().name().replaceAll("\\.", "__"));
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
        
  /** Give JDBC {@link Connection} to Phoenix database.
    * @return The JDBC {@link Connection} to Phoenix database. */
  public Connection conn() {
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
        _simpleSchema        = (HBaseSchema)schema().simpleSchema();
        }
      setProcessor(new HBaseSQLClientProcessor(this));      
      }
    return _conn;
    }
    
  @Override
  public void close() {
    log.debug("Closing");
    try {
      if (_conn != null) {
        _conn.close();
        }
      }
    catch (SQLException e) {
      log.warn("Cannot close JDBC", e);
      }
    _conn = null;
    super.close();
    }
    
  /** Give simple {@link HBaseSchema}.
    * @return The simple {@link HBaseSchema}. */
  public HBaseSchema simpleSchema() {
    return _simpleSchema;
    }
    
  /** Give SQL table name.
    * @return The SQL table name. */
  public String sqlTableName() {
    return _sqlTableName;
    }
    
  private HBaseSchema _simpleSchema; 
    
  private Connection _conn = null;  
  
  private String _sqlTableName = null;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseSQLClient.class);

  }
