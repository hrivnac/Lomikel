package com.Lomikel.Phoenixer;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Coding;
import com.Lomikel.Utils.MapUtil;
import com.Lomikel.Utils.LomikelException;

// Tinker Pop
import org.apache.tinkerpop.gremlin.structure.Vertex;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
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
  
  /** Get row(s).
    * @param key     The row key. Disables other search terms.
    *                It can be <tt>null</tt>.
    * @param search  The search terms as <tt>family:column:value,...</tt>.
    *                Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                among <tt>exact,prefix,substring,regex</tt>.
    *                The default for key is <tt>prefix</tt>,
    *                the default for columns is <tt>substring</tt>.
    *                It can be <tt>null</tt>.
    *                All searches are executed as prefix searches.    
    * @param filter  The names of required values as <tt>family:column,...</tt>.
    *                <tt>*</tt> = all.
    * @param delay   The time period start, in minutes back since dow.
    *                <tt>0</tt> means no time restriction.
    * @param ifkey   Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime  Whether give also entries timestamps (as <tt>key:time</tt>).
    * @return        The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String  key,
                                               String  search,
                                               String  filter,
                                               long    delay,
                                               boolean ifkey,
                                               boolean iftime) {
    String searchMsg = search;
    if (searchMsg != null && searchMsg.length() > 80) {
      searchMsg = searchMsg.substring(0, 80) + "...";
      }
    log.debug("Searching for key: " + key + 
              ", search: " + searchMsg + 
              ", filter: " + filter +
              ", delay: "  + delay + " min" + 
              ", id/time: " + ifkey + "/" + iftime);
    long now = System.currentTimeMillis();
    long start = (delay == 0L) ? 0L :  now - delay * 1000L * 60L;
    long stop = now;
    return scan(key, search, filter, start, stop, ifkey, iftime);
    }
                   
  /** Get row(s).
    * @param key     The row key. Disables other search terms.
    *                It can be <tt>null</tt>.
    * @param search  The search terms as <tt>family:column:value,...</tt>.
    *                Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                among <tt>exact,prefix,substring,regex</tt>.
    *                The default for key is <tt>prefix</tt>,
    *                the default for columns is <tt>substring</tt>.
    *                It can be <tt>null</tt>.
    *                All searches are executed as prefix searches.    
    * @param filter  The names of required values as <tt>family:column,...</tt>.
    *                <tt>*</tt> = all.
    * @param start   The time period start timestamp in <tt>ms</tt>.
    *                <tt>0</tt> means since the beginning.
    * @param stop    The time period stop timestamp in <tt>ms</tt>.
    *                <tt>0</tt> means till now.
    * @param ifkey   Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime  Whether give also entries timestamps (as <tt>key:time</tt>).
    * @return        The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String  key,
                                               String  search,
                                               String  filter,
                                               long    start,
                                               long    stop,
                                               boolean ifkey,
                                               boolean iftime) {
    String searchMsg = search;
    if (search != null && search.length() > 80) {
      searchMsg = search.substring(0, 80) + "...";
      }
    log.info("Searching for key: " + key + 
             ", search: " + searchMsg + 
             ", filter: " + filter +
             ", interval: " + start + " ms - " + stop + " ms" +
             ", id/time: " + ifkey + "/" + iftime);
    Map<String, String> searchM = new TreeMap<>();
    if (search != null && !search.trim().equals("")) {
      String[] ss;
      String k;
      String v;
      for (String s : search.trim().split(",")) {
        ss = s.trim().split(":");
        if (ss.length == 4) {
          k = ss[0] + ":" + ss[1] + ":" + ss[3];
          }
        else {
          k = ss[0] + ":" + ss[1];
          }
        v = ss[2];
        if (searchM.containsKey(k)) {
          v = searchM.get(k) + "," + v;
          }
        searchM.put(k, v);
        }
      }
    return scan(key, searchM, filter, start, stop, ifkey, iftime);
    }
    
  /** Get row(s).
    * @param key       The row key. Disables other search terms.
    *                  It can be <tt>null</tt>.
    * @param searchMap The {@link Map} of search terms as <tt>family:column-value,value,...</tt>.
    *                  Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                  <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                  {@link Comparator} can be chosen as <tt>family:column:comparator-value</tt>
    *                  among <tt>exact,prefix,substring,regex</tt>.
    *                  The default for key is <tt>prefix</tt>,
    *                  the default for columns is <tt>substring</tt>.
    *                  It can be <tt>null</tt>.
    *                  All searches are executed as prefix searches.    
    * @param filter    The names of required values as <tt>family:column,...</tt>.
    *                  <tt>*</tt> = all.
    * @param start     The time period start timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means since the beginning.
    * @param stop      The time period stop timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means till now.
    * @param ifkey     Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime    Whether give also entries timestamps (as <tt>key:time</tt>).
    * @return          The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> scan(String              key,
                                               Map<String, String> searchMap,
                                               String              filter,
                                               long                start,
                                               long                stop,
                                               boolean             ifkey,
                                               boolean             iftime) {
    String sql = formSqlRequest(key, searchMap, filter, start, stop, ifkey, iftime);
    String answer = query(sql);
    return interpretSqlAnswer(answer);
    }
                   
  /** Formulate SQL request.
    * @param key       The row key. Disables other search terms.
    *                  It can be <tt>null</tt>.
    * @param searchMap The {@link Map} of search terms as <tt>family:column-value,value,...</tt>.
    *                  Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                  <tt>key:startKey</tt> and <tt>key:stopKey</tt> van restrict search to a key interval.
    *                  {@link Comparator} can be chosen as <tt>family:column:comparator-value</tt>
    *                  among <tt>exact,prefix,substring,regex</tt>.
    *                  The default for key is <tt>prefix</tt>,
    *                  the default for columns is <tt>substring</tt>.
    *                  It can be <tt>null</tt>.
    *                  All searches are executed as prefix searches.    
    * @param filter    The names of required values as <tt>family:column,...</tt>.
    *                  <tt>*</tt> = all.
    * @param start     The time period start timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means since the beginning.
    * @param stop      The time period stop timestamp in <tt>ms</tt>.
    *                  <tt>0</tt> means till now.
    * @param ifkey     Whether give also entries keys (as <tt>key:key</tt>).
    * @param iftime    Whether give also entries timestamps (as <tt>key:time</tt>).
    * @return          The SQL request formed from the supplied arguments. */
  public String formSqlRequest(String              key,
                               Map<String, String> searchMap,
                               String              filter,
                               long                start,
                               long                stop,
                               boolean             ifkey,
                               boolean             iftime) {
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
    String where = "";
    boolean first = true;
    for (Map.Entry<String, String> entry : MapUtil.sortByValue(searchMap).entrySet()) {
      if (first) {
        first = false;
        }
      else {
        where += " and ";
        }
      if (schema().type(entry.getKey()).equals("String")) {        
        where += entry.getKey() + " = '" + entry.getValue() + "'";
        }
      else {
        where += entry.getKey() + " = " + entry.getValue();
        }
      }
    if (filter == null || filter.trim().equals("")) {
      filter = "*";
      }
    String sql = "select " + filter + " from " + _tableName;
    if (!where.equals("")) {
      sql += " where " + where;
      }
    if (_limit != 0) {
      sql += " limit " + _limit;
      }
    return sql;
    }
    
  /** TBD */
  public Map<String, Map<String, String>> interpretSqlAnswer(String answer) {
    Map<String, Map<String, String>> results = new TreeMap<>();
    Map<String, String> result;    
    String[] keyvalue;
    int i = 0;
    for (String line : answer.split("\n")) {
      result = new TreeMap<>();
      for (String r : line.split("#")) {
        keyvalue = r.split("=");
        result.put(keyvalue[0], keyvalue[1]);
        }
      results.put("" + i++, result);
      }
    return results;
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
    
  /** Give the table {@link Schema}.
    * @param schema The used {@link Schema}. */
  public Schema schema() {
    return _schema;
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
    
  /** Give the limit for the number of results.
    * @return The limit for the number of results. */
  public int limit() {
    return _limit;
    }
    
  /** Set whether the results should be in the reversed order.
    * @param reversed Whether the results should be in the reversed order. */
  public void setReversed(boolean reversed) {
    log.info("Setting reversed " + reversed);
    _reversed = reversed;
    }
    
  /** Tell, whether the results should be in the reversed order.
    * @return  Whether the results should be in the reversed order. */
  public boolean isReversed() {
    return _reversed;
    }
        
  private String _tableName;
  
  private Schema _schema;

  private int _limit = 0;
  
  private boolean _reversed = false;
    
  private Connection _connection;  
  
  private ElementFactory _ef;
  
	private String _phoenixUrl;
  
  //private static SimpleDateFormat PHOENIX_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  private static SimpleDateFormat PHOENIX_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    
  static final String JDBC_DRIVER = "org.apache.phoenix.jdbc.PhoenixDriver";
  
  /** Logging . */
  private static Logger log = Logger.getLogger(PhoenixClient.class);
    
  }
