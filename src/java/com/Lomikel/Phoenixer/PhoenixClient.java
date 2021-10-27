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
import java.text.SimpleDateFormat;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

// Log4J
import org.apache.log4j.Logger;

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
    Init.init();
    log.info("Empty opening");
    }
    
  /** Create and connect to Phoenix.
    * @param phoenixUrl The {@link Phoenix} url.
    * @throws LomikelException If anything goes wrong. */
  public PhoenixClient(String phoenixUrl) throws LomikelException {
    Init.init();
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
    long time = System.currentTimeMillis();
    if (stop == 0) {
      stop = System.currentTimeMillis();
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
    searchMap.rmNullValues();
    String where = "";
    boolean first = true;
    for (Map.Entry<String, String> entry : MapUtil.sortByValue(searchMap.map()).entrySet()) {
      if (first) {
        first = false;
        }
      else {
        where += " and ";
        }
      if (entry.getValue() != null) {
        if (schema().type(entry.getKey()).equals("String")) {        
          where += entry.getKey() + " = '" + entry.getValue() + "'";
          }
        else {
          where += entry.getKey() + " = " + entry.getValue();
          }
        }
      }
    if (filter == null || filter.trim().equals("")) {
      filter = "*";
      }
    String sql = "select " + filter + " from " + tableName();
    if (!where.equals("")) {
      sql += " where " + where;
      }
    if (limit() != 0) {
      sql += " limit " + limit();
      }
    return sql;
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
            result.put(keyvalue[0], keyvalue[1]);
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
          result += md.getColumnName(i + 1).toLowerCase() + "=";
          //result += rs.getString(i + 1);
          switch (md.getColumnTypeName(i + 1)) {
            case "BOOLEAN": 
              result += rs.getBoolean(i + 1);
              break;
            case "BINARY": 
              result += "***";
              //result += rs.getString(i + 1);
              //result += new String(rs.getBytes(i + 1), "UTF-16LE");
              break;
            case "BINARY ARRAY": 
              result += rs.getString(i + 1);
              break;
            case "INTEGER": 
              result += rs.getInt(i + 1);
              break;
            case "FLOAT": 
              result += rs.getFloat(i + 1);
              break;
            case "BIGINT": 
              result += rs.getLong(i + 1);
              break;
            case "SMALLINT": 
              result += rs.getShort(i + 1);
              break;
            case "SMALLINT ARRAY": 
              result += rs.getString(i + 1);
              break;
            case "VARCHAR": 
              result += rs.getString(i + 1);
              break;
            case "TIMESTAMP": 
              result += rs.getDate(i + 1);
              break;
            default:
	            log.error("Cannot get result " + md.getColumnName(i + 1).toLowerCase() + "  of type " + md.getColumnTypeName(i + 1));
              }              
           }
        }     
      rs.close();
      st.close();
      }
    catch (SQLException se) {
      se.printStackTrace();
      }
    catch (Exception e) {
      e.printStackTrace();
      }
    finally {
      try {
        if (st != null) {
          st.close();
          }
        }
      catch (SQLException se2) {
        } 
      }
    log.info("Result: " + result);
    return result;
    } 
  
  // Aux -----------------------------------------------------------------------
    
  /** Give {@link Class} representing a {@link Vertex} of a label.
    * @param lbl The {@link Vertex} label (i.e. <em>lbl</em>) value of
    *            the {@link Vertex} to be represented.
    * @return    The representation of requested {@link Vertex}. */
  public static void registerVertexType(String lbl,
                                        Class  representant) {
    log.info(lbl + "  will be represented by " + representant);
    _representations.put(lbl, representant);
    }  
    
  @Override
  public Class representation(String lbl) {
    return _representations.get(lbl);
    }
    
  @Override
  public Map<String, Class> representations() {
    return _representations;
    }
 
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
  
  private static Map<String, Class> _representations = new TreeMap<>();
  
  /** Logging . */
  private static Logger log = Logger.getLogger(PhoenixClient.class);
    
  }
