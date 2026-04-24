package com.Lomikel.ElasticSearcher;

import com.Lomikel.Utils.SmallHttpClient;
import static com.Lomikel.Utils.Constants.π;
import com.Lomikel.Utils.LomikelException;

// org.json      
import org.json.JSONObject;
import org.json.JSONArray;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>ESClient</code> handles access to <em>ElasticSearch</em> database.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * <tt>T</tt>: the table representation.
  * <tt>S</tt>: the {@link Schema}.
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class ESClient {
  
  /** Create and connect to <em>ElasticSearch</em> server.
    * @param url The <em>ElasticSearch</em> server. */
  public ESClient(String url) {
    log.info("Connection to " + url);
    _url = url;    
    }
    
  // Create index ==============================================================  
    
  /** Create new <em>ElasticSearch</em> simple index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  fieldType The indexed field type <tt>geo_point, double, long, date_nanos</tt>.
    * @throws LomikelException If anything goes wrong. */
  public void createIndex(String idxName,
                          String fieldName,
                          String fieldType) throws LomikelException { 
    String jsonCmd = new JSONObject().put("mappings",
                                          new JSONObject().put("properties",
                                                               new JSONObject().put(fieldName,
                                                                                    new JSONObject().put("type", fieldType))))
                                     .toString();
    log.info("Creating index " + jsonCmd);
    String answer = _httpClient.putJSON(_url + "/" + idxName, jsonCmd, null, null);
    log.info(answer);
    }
  
  // Put value =================================================================  
    
  /** Insert new <em>ra,dec</em> <em>GeoPoint</em> entry into index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  rowkey    The rowkey value.
    * @param  ra        The <tt>ra</tt> value.
    * @param  dec       The <tt>dec</tt> value.
    * @throws LomikelException If anything goes wrong. */
  public void putGeoPoint(String idxName, 
                          String fieldName,
                          String rowkey,
                          double ra,
                          double dec) throws LomikelException { 
    double lat = dec;
    double lon = ra - 180;
    put(idxName,
        new JSONObject().put("index",
                             new JSONObject().put("_id", rowkey)),
        new JSONObject().put(fieldName,
                             new JSONObject().put("lat", lat)
                                             .put("lon", lon)));
    }
    
  /** Insert new String[] values entry into index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  rowkey    The rowkey value.
    * @param  value     The field values.
    * @throws LomikelException If anything goes wrong. */
  public void putValue(String   idxName, 
                       String   fieldName,
                       String   rowkey,
                       String[] value) throws LomikelException { 
    put(idxName,
        new JSONObject().put("index",
                             new JSONObject().put("_id", rowkey)),
        new JSONObject().put(fieldName, value));
    }
    
  /** Insert new String value entry into index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  rowkey    The rowkey value.
    * @param  value     The field value.
    * @throws LomikelException If anything goes wrong. */
  public void putValue(String idxName, 
                       String fieldName,
                       String rowkey,
                       String value) throws LomikelException { 
    put(idxName,
        new JSONObject().put("index",
                             new JSONObject().put("_id", rowkey)),
        new JSONObject().put(fieldName, value));
    }
    
  /** Insert new long[] values entry into index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  rowkey    The rowkey value.
    * @param  value     The field values.
    * @throws LomikelException If anything goes wrong. */
  public void putValue(String idxName, 
                       String fieldName,
                       String rowkey,
                       long[] value) throws LomikelException { 
    put(idxName,
        new JSONObject().put("index",
                             new JSONObject().put("_id", rowkey)),
        new JSONObject().put(fieldName, value));
    }
    
  /** Insert new long value entry into index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  rowkey    The rowkey value.
    * @param  value     The field value.
    * @throws LomikelException If anything goes wrong. */
  public void putValue(String idxName, 
                       String fieldName,
                       String rowkey,
                       long   value) throws LomikelException { 
    put(idxName,
        new JSONObject().put("index",
                             new JSONObject().put("_id", rowkey)),
        new JSONObject().put(fieldName, value));
    }
    
  /** Insert new double value entry into index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  rowkey    The rowkey value.
    * @param  value     The field value.
    * @throws LomikelException If anything goes wrong. */
  public void putValue(String idxName, 
                       String fieldName,
                       String rowkey,
                       double value) throws LomikelException { 
    put(idxName,
        new JSONObject().put("index",
                             new JSONObject().put("_id", rowkey)),
        new JSONObject().put(fieldName, value));
    }
    
  /** Insert new double[] values entry into index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  rowkey    The rowkey value.
    * @param  value     The field values.
    * @throws LomikelException If anything goes wrong. */
  public void putValue(String   idxName, 
                       String   fieldName,
                       String   rowkey,
                       double[] value) throws LomikelException { 
    put(idxName,
        new JSONObject().put("index",
                             new JSONObject().put("_id", rowkey)),
        new JSONObject().put(fieldName, value));
    }
        
  /** Insert new value into index.
    * @param  idxName The index name.
    * @param  idx The index json entry.
    * @param  cmd The cpommand json entry.
    * @throws LomikelException If anything goes wrong. */
  private void put(String     idxName,
                   JSONObject idx,
                   JSONObject cmd) throws LomikelException {
   List<String> cmdList;
   if (!_commands.containsKey(idxName)) {
     cmdList = new ArrayList<>();
     }
   else {
     cmdList = _commands.get(idxName);
     }
   cmdList.add(idx.toString());
   cmdList.add(cmd.toString());
   _commands.put(idxName, cmdList);
   }
    
  /** Commit new values into index.
    * @param  idxName The index name.
    * @throws LomikelException If anything goes wrong. */
  public void commit(String idxName) throws LomikelException {
    List<String> command = _commands.get(idxName);
    String jsonCmd = command.stream()
                            .map(Object::toString)
                            .collect(Collectors.joining("\n")) + "\n";
    //log.info(jsonCmd);                        
    //log.info("Inserting " + idxName + "[" + command.size() + "]");
    //String answer = _httpClient.postJSON(_url + "/" + idxName + "/_doc" , jsonCmd, null, null);
    String answer = _httpClient.postNDJSON(_url + "/" + idxName + "/_bulk" , jsonCmd + "\n", null, null);
    JSONObject answerJson = new JSONObject(answer);
    if (answerJson.getBoolean("errors")) {
      throw new LomikelException("HTTP Post error");
      }
    log.info("Imported: " + answerJson.getJSONArray("items").length() + " of " + idxName);
    _commands.remove(idxName);
    }
    
  /** Commit all new values into index. */
  public void commit() {
    _commands.forEach((k, v) -> {
      try {
        commit(k);
        }
      catch (LomikelException e) {
        log.error("Cannot commit " + k, e);
        }});
    }
    
  /** Commit all new values into index.
    * @param n The number of retries (of each value) before failing. */
  public void commitWithRetry(int n) {
    _commands.forEach((k, v) -> {
      int m = n;
      while (m > 0) {
        try {
          commit(k);
          m = 0;
          }
        catch (LomikelException e) {
          m--;
          if (m == 0) {
            log.error("Cannot commit " + k, e);
            }
          else {
            log.warn("Retrying commit, m = " + m);
            }
          }
        }});
    }

  // Search ====================================================================  
    
  /** Search <em>ra,dec</em> <em>GeoPoint</em>s in distance.                                                                                                                                                                                                     
    * @param  idxName   The index name.                                                                                                                                                                                                                           
    * @param  fieldName The indexed field name.
    * @param  ra        The <tt>ra</tt> value.                                                                                                                                                                                                                    
    * @param  dec       The <tt>dec</tt> value.                                                                                                                                                                                                                   
    * @param  ang       The cone ang value.                                                                                                                                                                                                                       
    * @return           The rowkey values.                                                                                                                                                                                                                         
    * @throws LomikelException If anything goes wrong. */
  public List<String> searchGeoPoint(String idxName,
                                     String fieldName,
                                     double ra,
                                     double dec,
                                     double ang) throws LomikelException {
    double lat = dec;
    double lon = ra - 180;
    double dist = ang * π * 6371008.7714 / 180.0; //org.elasticsearch.common.geo.GeoUtils.EARTH_MEAN_RADIUS                                                                                                                                               
    return search(idxName, new JSONObject().put("query",
                           new JSONObject().put("geo_distance",
                                                new JSONObject().put(fieldName,
                                                                     new JSONObject().put("lat", lat)
                                                                                     .put("lon", lon))
                                                                .put("distance", dist)))
                                           .put("size", _size)
                                           .toString());
    }
    
  /** Search String value.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  value     The field value.
    * @return           The rowkey values.
    * @throws LomikelException If anything goes wrong. */
  public List<String> searchValue(String idxName,
                                  String fieldName,
                                  String value) throws LomikelException {
    return search(idxName, new JSONObject().put("query",
                                                new JSONObject().put("match",
                                                                     new JSONObject().put(fieldName, value)))
                                           .put("size", _size)
                                           .toString());
    }
    
  /** Search long value.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  value     The field value.
    * @return           The rowkey values.
    * @throws LomikelException If anything goes wrong. */
  public List<String> searchValue(String idxName,
                                  String fieldName,
                                  long   value) throws LomikelException {
    return search(idxName, new JSONObject().put("query",
                                                new JSONObject().put("match",
                                                                     new JSONObject().put(fieldName, value)))
                                           .put("size", _size)
                                           .toString());
    }
    
  /** Search double value.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  value     The field value.
    * @return           The rowkey values.
    * @throws LomikelException If anything goes wrong. */
  public List<String> searchValue(String idxName,
                                  String fieldName,
                                  double value) throws LomikelException {
    return search(idxName, new JSONObject().put("query",
                                                new JSONObject().put("match",
                                                                     new JSONObject().put(fieldName, value)))
                                           .put("size", _size)
                                           .toString());
    }
    
  /** Search value in String range.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  minValue  The minimal value.
    * @param  maxValue  The maximal value.
    * @return           The rowkey values.
    * @throws LomikelException If anything goes wrong. */
  public List<String> searchRange(String idxName,
                                  String fieldName,
                                  String minValue,
                                  String maxValue) throws LomikelException {
    return search(idxName, new JSONObject().put("query",
                                                new JSONObject().put("range",
                                                                     new JSONObject().put(fieldName,
                                                                                          new JSONObject().put("gte", minValue)
                                                                                                          .put("lte", maxValue))))
                                           .put("size", _size)
                                           .toString());
    }
    
  /** Search value in long range.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  minValue  The minimal value.
    * @param  maxValue  The maximal value.
    * @return           The rowkey values.
    * @throws LomikelException If anything goes wrong. */
  public List<String> searchRange(String idxName,
                                  String fieldName,
                                  long   minValue,
                                  long   maxValue) throws LomikelException {
    return search(idxName, new JSONObject().put("query",
                                                new JSONObject().put("range",
                                                                     new JSONObject().put(fieldName,
                                                                                          new JSONObject().put("gte", minValue)
                                                                                                          .put("lte", maxValue))))
                                           .put("size", _size)
                                           .toString());
    }
    
  /** Search value in double range.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  minValue  The minimal value.
    * @param  maxValue  The maximal value.
    * @return           The rowkey values.
    * @throws LomikelException If anything goes wrong. */
  public List<String> searchRange(String idxName,
                                  String fieldName,
                                  double minValue,
                                  double maxValue) throws LomikelException {
    return search(idxName, new JSONObject().put("query",
                                                new JSONObject().put("range",
                                                                     new JSONObject().put(fieldName,
                                                                                          new JSONObject().put("gte", minValue)
                                                                                                          .put("lte", maxValue))))
                                           .put("size", _size)
                                           .toString());
    }

  /** Search value from index.
    * @param  idxName The index name.
    * @param  jsonCmd The json command to execute.
    * @return         The rowkey values.
    * @throws LomikelException If anything goes wrong. */
  private List<String> search(String idxName,
                              String jsonCmd) throws LomikelException {
    String answer = "no answer";
    List<String> results = new ArrayList<>();
    log.info("Searching " + jsonCmd);
    try {
      answer = _httpClient.postJSON(_url + "/" + idxName + "/_search", jsonCmd, null, null);
      JSONObject answerJ = new JSONObject(answer);
      JSONArray hitsJ = answerJ.getJSONObject("hits").getJSONArray("hits");
      for (Object o : hitsJ) {
        results.add(((JSONObject)o).getString("_id"));
        }
      }
    catch (Exception e) {
      log.error("No results found", e);
      log.info("Elastic search answer:\t" + answer);
      }
    log.info("" + results.size() + " results found");
    return results;
    }
    
  // Get =======================================================================  
    
  /** Get field value from index.
    * @param  idxName   The index name.
    * @param  idxValue  The index value.
    * @param  fieldName The indexed field name.
    * @return         The field value.
    * @throws LomikelException If anything goes wrong. */
  // TBD: check value vs array
  // TBD: do for String and long (and check)
  private double getDouble(String idxName,
                           String idxValue,
                           String fieldName) throws LomikelException {
    String answer = "no answer";
    double value = 0;
    log.info("Searching " + idxName + "=" + idxValue + "/" + fieldName);
    try {
      answer = _httpClient.get(_url + "/" + idxName + "/_doc/" + idxValue + "?_source_includes=" + fieldName, null);
      JSONObject answerJ = new JSONObject(answer);
      value = answerJ.getJSONObject("_source")
                     .getDouble(fieldName);
      }
    catch (Exception e) {
      log.warn("No value found");
      //log.error("No results found", e);
      //log.info("Elastic search answer:\t" + answer);
      }
    return value;
    }
    
  /** Get field values from index.
    * @param  idxName   The index name.
    * @param  idxValue  The index value.
    * @param  fieldName The indexed field name.
    * @return         The field values.
    * @throws LomikelException If anything goes wrong. */
  private double[] getDoubleArray(String idxName,
                                  String idxValue,
                                  String fieldName) throws LomikelException {
    String answer = "no answer";
    double[] value = new double[]{};
    log.info("Searching " + idxName + "=" + idxValue + "/" + fieldName);
    try {
      answer = _httpClient.get(_url + "/" + idxName + "/_doc/" + idxValue + "?_source_includes=" + fieldName, null);
      JSONObject answerJ = new JSONObject(answer);
      JSONArray jvalue = answerJ.getJSONObject("_source")
                                .getJSONArray(fieldName);
      value = new double[jvalue.length()];
      for (int i = 0; i < jvalue.length(); i++) {
        value[i] = jvalue.getDouble(i);
        }
      }
    catch (Exception e) {
      log.warn("No value found");
      //log.error("No results found", e);
      //log.info("Elastic search answer:\t" + answer);
      }
    return value;
    }
    
  // Info ======================================================================
  
  /** Give the size of the index.
    * @param  idxName The index name.
    * @return         The size of the index.
    * @throws LomikelException If anything goes wrong. */
  public int size(String idxName) throws LomikelException {
    int sz = 0;
    String answer = "";
    try {
      Object match_all = null;
      String jsonCmd = new JSONObject().put("query",
                                            new JSONObject().put("match_all",
                                                                 new JSONObject()))
                                       .toString();
      answer = _httpClient.postJSON(_url + "/" + idxName + "/_count", jsonCmd, null, null);
      JSONObject answerJ = new JSONObject(answer);
      sz = answerJ.getInt("count");
      }
    catch (Exception e) {
      log.error("size not found", e);
      log.info("Elastic search answer:\t" + answer);
      }
    return sz;
    }
    
  // ===========================================================================
  
  /** Set the limit on number of results to show.
    * @param size The limit on number of results to show. The defauld is <tt>10</tt>. */
  public void setSizeSearch(int size) {
    _size = size;
    }
 
  private String _url;
  
  private SmallHttpClient _httpClient = new SmallHttpClient();
  
  private int _size= 10;
   
  private ConcurrentMap<String, List<String>> _commands = new ConcurrentHashMap<>();
        
  /** Logging . */
  private static Logger log = LogManager.getLogger(ESClient.class);

  }
