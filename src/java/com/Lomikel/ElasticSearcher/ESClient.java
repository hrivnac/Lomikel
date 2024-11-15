package com.Lomikel.ElasticSearcher;

import com.Lomikel.Utils.SmallHttpClient;
import com.Lomikel.Utils.LomikelException;

// org.json      
import org.json.JSONObject;
import org.json.JSONArray;

// Java
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
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
                            .collect(Collectors.joining("\n"));
    log.info("Inserting " + idxName + "[" + command.size() + "]");
    //String answer = _httpClient.postJSON(_url + "/" + idxName + "/_doc" , jsonCmd, null, null);
    String answer = _httpClient.postJSON(_url + "/" + idxName + "/_bulk" , jsonCmd + "\n", null, null);
    _commands.remove(idxName);
    }
    
  /** Commit all new values into index.
    * @throws LomikelException If anything goes wrong. */
  public void commit() {
    _commands.forEach((k, v) -> {
      try {
        commit(k);
        }
      catch (LomikelException e) {
        log.error("Cannot commit " + k, e);
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
    double dist = ang * Math.PI * 6371008.7714 / 180.0; //org.elasticsearch.common.geo.GeoUtils.EARTH_MEAN_RADIUS                                                                                                                                               
    return search(idxName, new JSONObject().put("query",
                           new JSONObject().put("geo_distance",
                                                new JSONObject().put(fieldName,
                                                                     new JSONObject().put("lat", lat)
                                                                                     .put("lon", lon))
                                                                .put("distance", dist)))
                                           .toString());
    }
    
  /** Search value.
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
                                           .toString());
    }
    
  /** Search value.
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
                                           .toString());
    }
    
  /** Search value.
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
                                           .toString());
    }
    
  /** Search value.
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
                                           .toString());
    }
    
  /** Search value in range.
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
                                           .put("size", _sizeSearch)
                                           .toString());
    }
    
  /** Search value in range.
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
                                           .put("size", _sizeSearch)
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
    
  // ===========================================================================
  
  /** Set the limit on number of results to show.
    * @param size The limit on number of results to show. The defauld is <tt>10</tt>. */
  public void setSizeSearch(int size) {
    _sizeSearch = size;
    }
    
  /** Set the limit on number of entries to insert in one call (per index).
    * @param size The limit on number of entries to in sert in one call. The defauld is <tt>1</tt>.*/
  public void setSizePut(int size) {
    _sizeSearch = size;
    }
 
  private String _url;
  
  private SmallHttpClient _httpClient = new SmallHttpClient();
  
  private int _sizeSearch = 10;
  
  private int _sizePut = 1;
   
  private ConcurrentMap<String, List<String>> _commands = new ConcurrentHashMap<>();
        
  /** Logging . */
  private static Logger log = LogManager.getLogger(ESClient.class);

  }
