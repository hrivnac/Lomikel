package com.Lomikel.Phoenixer;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.DB.Schema;
import com.Lomikel.DB.Client;
import com.Lomikel.DB.SearchMap;

// Java
import java.util.Map;
import java.util.TreeMap;
import java.util.Base64;
import java.text.SimpleDateFormat;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>PhoenixClient</code> connects to Phoenix.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: implement missing methids from HBaseClient
public class PhoenixClient extends Client<String, PhoenixSchema> {
   
  // Lifecycle -----------------------------------------------------------------
    
  /** Create and do not connect to Phoenix.
    * @throws LomikelException If anything goes wrong. */
  public PhoenixClient() throws LomikelException {
    Init.init("PhoenixClient");
    log.info("Empty opening");
    }
    
  /** Create and connect to Phoenix.
    * @param phoenixUrl The {@link Phoenix} url.
    * @throws LomikelException If anything goes wrong. */
  public PhoenixClient(String phoenixUrl) throws LomikelException {
    Init.init("PhoenixClient");
    _phoenixUrl = phoenixUrl;
    if (phoenixUrl != null) {
      log.info("Opening " + phoenixUrl);
      }
    try {
      Class.forName(JDBC_DRIVER);
      _connection = DriverManager.getConnection(_phoenixUrl);
      }
    catch (ClassNotFoundException | SQLException e) {
      log.error("Cannot open " + _phoenixUrl, e);
      }
    }
	
  @Override
  public String connect(String tableName) throws LomikelException {
     return connect(tableName, "schema");
     }
             
  @Override
  public String connect(String tableName,
                        String schemaName) throws LomikelException {
     return connect(tableName, schemaName, 0);
     }
     
  @Override
  public String connect(String tableName,
                        String schemaName,
                        int    timeout) throws LomikelException {
    log.info("Connecting to " + tableName + ", using " + schemaName);
    setTableName(tableName);
    setSchema(PhoenixSchema.getSchema(schemaName));
    return tableName;
    }
    
  @Override
  public String connect(String tableName,
                        String schemaName,
                        int    timeout,
                        int    retries) throws LomikelException {
    return connect(tableName, schemaName, timeout);
    }
	  
  @Override
  public void close() {
    log.info("Closing");
    try {
      if (_connection != null)
        _connection.close();
        }
    catch (SQLException e) {
      e.printStackTrace();
      }
    }
    
  // Search --------------------------------------------------------------------
           
  @Override
  // BUG: TBD
  public void delete(String key) {
    }
  
  /** 
    * {@inheritDoc}
    *
    * The <em>family</em> part of arguments is omitted. */
  // TBD: implement all arguments
  @Override
  public Map<String, Map<String, String>> scan(String    key,
                                               SearchMap searchMap,
                                               String    filter,
                                               long      start,
                                               long      stop,
                                               boolean   ifkey,
                                               boolean   iftime) {
    String sql = formSqlRequest(key, searchMap, filter, start, stop, ifkey, iftime);
    String answer = query(sql);
    return interpretSqlAnswer(answer);
    }
    
  // BUG: call the other scan, filling just one version into map
  @Override
  public Map<String, Map<String, Map<Long, String>>> scan3D(String    key,
                                                            SearchMap searchMap,
                                                            String    filter,
                                                            long      start,
                                                            long      stop,
                                                            boolean   ifkey,
                                                            boolean   iftime) {
    return null;
    }
 
                   
  /** Formulate SQL request.
    * It has the same arguments as {@link #scan(String, SearchMap, String, long, long, boolean, boolean)}.
    * @return The request formed as an SQL string. */
   public String formSqlRequest(String    key,
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
             ", id/time: " + ifkey + "/" + iftime);
    if (filter != null && filter.contains("*")) {
      iftime = true;
      }
    if (key != null) {
      searchMap.clear();
      String[] keyParts = key.split("#");
      // TBD: check size of keyParts and Schema.rowkeynames()
      for (int i = 0; i < keyParts.length; i++) {
        if (!keyParts[i].trim().equals("")) {
          searchMap.put(schema().rowkeyNames()[i], keyParts[i]);
          }
        }
      }
    if (searchMap != null) {
      searchMap.rmNullValues();
      }
    StringBuffer whereB = new StringBuffer("");
    boolean first = true;
    for (Map.Entry<String, String> entry : MapUtil.sortByValue(searchMap.map()).entrySet()) {
      if (first) {
        first = false;
        }
      else {
        whereB.append(" and ");
        }
      if (entry.getValue() != null) {
        if (schema().type(entry.getKey()).equals("String")) {        
          whereB.append(entry.getKey())
                .append(" = '")
                .append(entry.getValue())
                .append("'");
          }
        else {
          whereB.append(entry.getKey())
                .append(" = ")
                .append(entry.getValue());
          }
        }
      }
    if (filter == null || filter.trim().equals("")) {
      filter = "*";
      }
    StringBuffer sqlB = new StringBuffer("select " + filter + " from " + tableName());
    if (!whereB.toString().equals("")) {
      sqlB.append(" where ")
          .append(whereB);
      }
    if (limit() != 0) {
      sqlB.append(" limit ")
          .append(limit());
      }
    return sqlB.toString();
    }
    
  /** Interpret the SQL answer.
    * @param answer The string answer from {@link #query(String}.
    * @return The parsed result in the same form as {@link #scan(String, SearchMap, String, long, long, boolean, boolean)}. */
  public Map<String, Map<String, String>> interpretSqlAnswer(String answer) {
    Map<String, Map<String, String>> results = new TreeMap<>();
    Map<String, String> result;    
    String[] keyvalue;
    String key;
    String[] kv;
    for (String line : answer.split("\n")) {
      result = new TreeMap<>();
      for (String r : line.split("#")) {
        if (!r.trim().equals("")) {
          keyvalue = r.split("=");
          if (keyvalue.length > 1) {
            result.put(rename(keyvalue[0]), keyvalue[1]);
            }
          }
        }
      kv = new String[schema().rowkeyNames().length];
      for (int i = 0; i < schema().rowkeyNames().length; i++) {
        kv[i] = result.get(schema().rowkeyNames()[i]);
        }
      results.put(String.join("#", kv), result);
      }
    return results;
    }
    
  /** Process the <em>Phoenix</em> SQL and give answer.
    * @param sql The SQL query.
    * @return    The query result as <tt>name=value;...#...</tt>.*/
  public String query(String sql) {
    log.info("Query: " + sql);
    Statement st = null;
    String result = "";
    String r;
    String cName;
    boolean firstRes = true;
    boolean firstVal;
    try {
      st = _connection.createStatement();
      ResultSet rs = st.executeQuery(sql);
      ResultSetMetaData md = rs.getMetaData();
      while (rs.next()) {
        if (!firstRes) {
          result += "\n";
          }
        else {
          firstRes = false;
          }
        firstVal = true;
        for (int i = 0; i < md.getColumnCount(); i++) {
          if (!firstVal) {
            result += "#";
            }
          else {
            firstVal = false;
            }
          r = "";
          cName = md.getColumnName(i + 1).toLowerCase();
          //r += rs.getString(i + 1);
          switch (md.getColumnTypeName(i + 1)) {
            case "BOOLEAN": 
              r += rs.getBoolean(i + 1);
              break;
            case "BINARY": 
              //r += "***";
              //r = rs.getString(i + 1);
              //r = new String(rs.getBytes(i + 1), "UTF-16LE");
              r = Base64.getEncoder().encodeToString(rs.getBytes(i + 1));
              break;
            case "BINARY ARRAY": 
              r += rs.getString(i + 1);
              break;
            case "INTEGER": 
              r += rs.getInt(i + 1);
              break;
            case "FLOAT": 
              r += rs.getFloat(i + 1);
              break;
            case "BIGINT": 
              r += rs.getLong(i + 1);
              break;
            case "SMALLINT": 
              r += rs.getShort(i + 1);
              break;
            case "SMALLINT ARRAY": 
              r += rs.getString(i + 1);
              break;
            case "VARCHAR": 
              r += rs.getString(i + 1);
              break;
            case "TIMESTAMP": 
              r += rs.getDate(i + 1);
              break;
            default:
	            log.error("Cannot get result " + cName + "  of type " + md.getColumnTypeName(i + 1));
              }  
           if (!rs.wasNull()) {
             result += cName + "=" + r;
             }
           }
        }     
      rs.close();
      st.close();
      }
    catch (SQLException se) {
      log.error("Query " + sql + " has failed");
      log.debug("Query " + sql + " has failed", se);
      }
    catch (Exception e) {
      log.error("Query " + sql + " has failed");
      log.debug("Query " + sql + " has failed", e);
      }
    finally {
      try {
        if (st != null) {
          st.close();
          }
        }
      catch (SQLException se2) {
        log.error("Query " + sql + " has failed");
        log.debug("Query " + sql + " has failed", se2);
        } 
      }
    if (result.trim().equals("")) {
      result = null;
      }
    log.info("Result: " + result);
    return result;
    } 
  
  // Aux -----------------------------------------------------------------------
 
  /** Give {@link Connection}.
    * @return The {@link Connection}. */
  public Connection connection() {
    return _connection;
    }
    
  private Connection _connection;  
  
	private String _phoenixUrl;
  
  //private static SimpleDateFormat PHOENIX_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  private static SimpleDateFormat PHOENIX_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    
  static final String JDBC_DRIVER = "org.apache.phoenix.jdbc.PhoenixDriver";
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(PhoenixClient.class);
    
  }
