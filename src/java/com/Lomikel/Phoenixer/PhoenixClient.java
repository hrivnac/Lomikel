package com.Lomikel.Phoenixer;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Coding;
import com.Lomikel.Utils.LomikelException;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.sql.Date;
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
public class PhoenixClient {
   
  // Lifecycle -----------------------------------------------------------------
    
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
	
  /** Connect to the table. Using the latest schema starting with <tt>schema</tt>.
    * @param tableName  The table name.
    * @return           The assigned id. 
    * @throws LomikelException If anything goes wrong. */
   public String connect(String tableName) throws LomikelException {
     return connect(tableName, "schema");
     }
             
  /** Connect to the table.
    * @param tableName  The table name.
    * @param schemaName The name of the {@link Schema} row.
    *                   <tt>null</tt> means to ignore schema,
    *                   empty {@link String} will take the latest one. 
    * @return           The assigned id.
    * @throws LomikelException If anything goes wrong. */
   public String connect(String tableName,
                         String schemaName) throws LomikelException {
     return connect(tableName, schemaName, 0);
     }
     
  /** Connect to the table.
    * @param tableName  The table name.
    * @param schemaName The name of the {@link Schema} row.
    *                   <tt>null</tt> means to ignore schema,
    *                   empty {@link String} will take the latest one. 
    * @param timeout    The timeout in ms (may be <tt>0</tt>).
    * @return           The assigned id.
    * @throws LomikelException If anything goes wrong. */
  public String connect(String tableName,
                        String schemaName,
                        int    timeout) throws LomikelException {
     log.info("Connecting to " + tableName);
    _tableName  = tableName;
    _schema     = Schema.getSchema(schemaName);
    return tableName;
    }
   
  @Override
  protected void finalize() throws Throwable {
    close();
    }
	  
  /** Close and release resources. */
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
        
  /** Assemble Phoenix search sql clause.
    * @param prototype The {@link Element} to be matched by the sql clause.
    * @return          The Phoenix sql clause. */
  protected String phoenixSearch(Element prototype) {
    String where = "";
    boolean first = true;
    for (Map.Entry<String, Object> entry : prototype.content().entrySet()) {
      if (first) {
        first = false;
        }
      else {
        where += " and ";
        }
      if (prototype.schema().get(entry.getKey()).equals("String") ||
          prototype.schema().get(entry.getKey()).equals("Data"  )) {        
        where += entry.getKey() + " = '" + entry.getValue() + "'";
        }
      else {
        where += entry.getKey() + " = " + entry.getValue();
        }
      }
    if (where.equals("")) {
      return "select * from " + prototype.phoenixTable();
      }
    return "select * from " + prototype.phoenixTable() + " where " + where;
    }
    
  /** Convert {@link Vertex} to {@link Element} prototype. 
    * @param vertex The {@link Vertex} to be converted.
    * @return       The {@link Element} prototype. 
    * @throws LomikelException If {@link Element} cannot be created. */
  protected Element prototype(Vertex vertex) throws LomikelException {
    Element prototype = _ef.element(vertex.label());
    String key = (String)(vertex.properties("phoenixkey").next().value());
    prototype.setKey(Coding.spltKey(key));
    return prototype;
    }
    
  /** Convert Phoenix results to {@link Element}s.
    * @param results   The Phoenix results
    *                  as <tt>name=value;...\n...</tt>.
    * @param prototype The {@link Element} prototype to be used.
    * @return          The {@link List} of resulting {@link Element}s. */
  protected List<Element> parsePhoenixResults(String  results,
                                              Element prototype) {
    List<Element> elements = new ArrayList<>();
    String key;
    String value;
    String[] keyvalue;
    for (String line : results.split("\n")) {
      prototype = prototype.emptyClone();
      for (String result : line.split("#")) {
        keyvalue = result.split("=");
        key   = keyvalue[0];
        value = keyvalue[1];
        if (value != null && !value.trim().equals("null") && !value.trim().equals("")) {
          try {
            //log.info(prototype + ": " + key + " => " + value);
            switch (prototype.schema().get(key)) {
             case "Integer":
               prototype.set(key, Integer.valueOf(value));
               break;
             case "Long": 
               prototype.set(key, Long.valueOf(value));
               break;
             case "String": 
               prototype.set(key, value);
               break;
             case "Date": 
               prototype.set(key, PHOENIX_DATE_FORMAT.parse(value));
               break;
             default:
               log.error("Cannot process " + key + " = " + value);
               }
             }
          catch (IllegalArgumentException e) {
            log.error("Cannot process " + key + " = " + value, e);
           }
          catch (ParseException e) {
            log.error("Cannot parse " + key + " = " + value, e);
           }
          }
        }
      elements.add(prototype);
      }
    return elements; 
    }
    
  /** Process the <em>Phoenix</em> SQL and give answer.
    * @param sql The SQL query.
    * @return    The query result as <tt>name=value;...#...</tt>.*/
  public String query(String sql) {
    log.info(sql);
    Statement st = null;
    int runNumber;
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
          switch (md.getColumnTypeName(i + 1)) {
            case "INTEGER": 
              result += rs.getInt(i + 1);
              break;
            case "BIGINT": 
              result += rs.getLong(i + 1);
              break;
            case "VARCHAR": 
              result += rs.getString(i + 1);
              break;
             case "TIMESTAMP": 
              result += rs.getDate(i + 1);
              break;
           default:
              log.error("Cannot get result " + i + "  of type " + sql);
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
        if (st != null)
          st.close();
          }
      catch (SQLException se2) {
        } 
      }
    log.info(result);
    return result;
    } 
    
  private String _tableName;
  
  private Schema _schema;
    
  private Connection _connection;  
  
  private ElementFactory _ef;
  
	private String _phoenixUrl;
    
	private static String DEFAULT_PHOENIX_URL = "jdbc:phoenix:localhost:2181";
  
  //private static SimpleDateFormat PHOENIX_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  private static SimpleDateFormat PHOENIX_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    
  static final String JDBC_DRIVER = "org.apache.phoenix.jdbc.PhoenixDriver";
  
  /** Logging . */
  private static Logger log = Logger.getLogger(PhoenixClient.class);
    
  }
