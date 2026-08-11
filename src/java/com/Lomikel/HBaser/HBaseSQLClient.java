package com.Lomikel.HBaser;

import com.Lomikel.Utils.LomikelException;

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
import java.util.Map;  
import java.util.TreeMap;  

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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
       
  /** Give SQL table creation command for this HBase table.
    * It creates the SQL tabel with the same properties are the current HBase table.
    * Using the default table name.
    * @return The SQL table creation command for this HBase table. */
  public String sqlTableCreationCommand() {
    return schema().toSQL(tableName() + "_" + schema().name().replaceAll("\\.", "__"));
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
  private static Logger log = LogManager.getLogger(HBaseSQLClient.class);

  }
