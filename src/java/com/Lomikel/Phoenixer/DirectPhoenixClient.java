package com.Lomikel.Phoenixer;

import com.Lomikel.Utils.LomikelException;
import com.Lomikel.DB.SearchMap;

// Java
import java.util.Map;
import java.util.TreeMap;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>DirectPhoenixClient</code> connects to Phoenix directly.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class DirectPhoenixClient extends PhoenixClient {
    
  /** Create and do not connect to Phoenix.
    * @throws LomikelException If anything goes wrong. */
  public DirectPhoenixClient() throws LomikelException {
    super();
    }
    
  /** Create and connect to Phoenix.
    * @param phoenixUrl The {@link Phoenix} url.
    * @throws LomikelException If anything goes wrong. */
  public DirectPhoenixClient(String phoenixUrl) throws LomikelException {
    super(phoenixUrl);
    }
           
  // TBD @Override
  public Map<String, Map<String, Object>> scan2objects(String    key,
                                                       SearchMap searchMap,
                                                       String    filter,
                                                       long      start,
                                                       long      stop,
                                                       boolean   ifkey,
                                                       boolean   iftime) {
    String sql = formSqlRequest(key, searchMap, filter, start, stop, ifkey, iftime);
    return query2map(sql);
    }
                       
  /** Process the <em>Phoenix</em> SQL and give answer.
    * @param sql The SQL query.
    * @return The parsed result in the same form as {@link #scan(String, SearchMap, String, long, long, boolean, boolean)}. */
  public Map<String, Map<String, Object>> query2map(String sql) {
    log.info("Query: " + sql);
    Statement st = null;
    Map<String, Map<String, Object>> results = new TreeMap<>();
    Map<String, Object> result;    
    String key;
    String[] kv;
    try {
      st = connection().createStatement();
      ResultSet rs = st.executeQuery(sql);
      ResultSetMetaData md = rs.getMetaData();
      while (rs.next()) {
        result = new TreeMap<>();
        for (int i = 0; i < md.getColumnCount(); i++) {
          key = md.getColumnName(i + 1).toLowerCase();
          result.put(key, rs.getObject(i + 1));
          }
        kv = new String[schema().rowkeyNames().length];
        for (int i = 0; i < schema().rowkeyNames().length; i++) {
          kv[i] = result.get(schema().rowkeyNames()[i]).toString();
          }
        results.put(String.join("#", kv), result);
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
    log.debug("Result: " + results);
    return results;
    } 
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(DirectPhoenixClient.class);
    
  }
