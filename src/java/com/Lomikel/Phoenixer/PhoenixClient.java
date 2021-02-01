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

/** <code>PhoenixProxyClient</code> connects directly to Phoenix.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class PhoenixClient {

  /** Create and connect to the default Phoenix.
    * @param ef The {@link ElementFactory} for {@link Element}s creation.
    * @throws LomikelException If anything goes wrong. */
  public PhoenixClient(ElementFactory ef) throws LomikelException {
    this(DEFAULT_PHOENIX_URL, ef);
    }
    
  /** Create and connect to Phoenix.
    * @param phoenixUrl The {@link Phoenix} url.
    *                   If <code>null</code>, no connection will be made.
    * @param ef The {@link ElementFactory} for {@link Element}s creation.
    * @throws LomikelException If anything goes wrong. */
  public PhoenixClient(String         phoenixUrl,
                       ElementFactory ef) throws LomikelException {
    _phoenixUrl = phoenixUrl;
    _ef = ef;
    if (phoenixUrl != null) {
      log.info("Opening " + phoenixUrl);
      connect();
      }
    }

  /** Get all {@link Vertex}es satisfying search.
    * @param vertexIds The {@link Vertex} identificators,
    *                  starting with its type and continuing with
    *                  pairs of property name and value.
    * @return The {@link List} of found {@link Vertex}es.
    *         They are created, if needed.
    * @throws LomikelException If anything goes wrong. */
  // TBD: switch should not be necessary
  public List<Vertex>	 vertexes(Object... vertexIds) throws LomikelException {
    Element prototype = null;
    boolean first = true;
    boolean isName = true;
    String name = "";
    for (Object o : vertexIds) {
      if (first) {
        prototype = _ef.element(o.toString());
        first = false;
        }
      else {
        if (isName) {
          name = o.toString();
          isName = false;
          }
        else {
          prototype.set(name, o);
          isName = true;
          }
        }
      }
    List<Element> elements = search(prototype);
    List<Vertex> vertexes = new ArrayList<>();
    for (Element element : elements) {
      log.info(element);
      vertexes.add(element.vertex());
      }
    return vertexes;
    }
    
  /** Search for {@link List} of {@link Element}s in remote Phoenix. 
    * @param prototype The {@link Element} with filled searched values.
    * @return          The {@link List} of found {@link Element}s.
    * @throws LomikelException If anhything goes wrong. */
  public List<Element> search(Element prototype) throws LomikelException {
    return parsePhoenixResults(phoenixSearch(prototype), prototype);
    }
    
  /** Send a simple sql clause to the remote Phoenix.
    * @param sql The sql clause.
    * @return    The search result.
    * @throws LomikelException If anhything goes wrong. */
  public String search(String sql) throws LomikelException {
    return query(sql);
    }
     
  /** Get {@link Element} in remote Phoenix. 
    * @param vertex The {@link Vertex} with filled searched values.
    * @return       The found {@link Element}.
    * @throws LomikelException If anhything goes wrong. */    
  public Element get(Vertex vertex) throws LomikelException {
    Element prototype = prototype(vertex);
    List<Element> elements = search(prototype);
    if (elements.size() == 0) {
      throw new LomikelException("No Element found for " + vertex);
      }
    else if (elements.size() > 1) {
      throw new LomikelException("" + elements.size() + " Elements found for " + vertex);
      }
    return elements.get(0);
    }
    
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
	
  /** Connect to Phoenix server. */
  public void connect() {
    log.info("Connecting to " + _phoenixUrl);
    try {
      Class.forName(JDBC_DRIVER);
      _connection = DriverManager.getConnection(_phoenixUrl);
      }
    catch (ClassNotFoundException | SQLException e) {
      log.error("Cannot connect to " + _phoenixUrl, e);
      }
    }
	  
  /** Close the connection to the Phoenix server. */
  // TBD: call it
  public void close() {
    log.info("Closing connection");
    try {
      if (_connection != null)
        _connection.close();
        }
    catch (SQLException e) {
      e.printStackTrace();
      }
    }
        
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
