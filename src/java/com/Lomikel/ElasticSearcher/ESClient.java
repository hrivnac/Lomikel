package com.Lomikel.ElasticSearcher;

import com.Lomikel.Utils.SmallHttpClient;
import com.Lomikel.Utils.LomikelException;

// org.json      
import org.json.JSONObject;
import org.json.JSONArray;

// Java
import java.util.List;
import java.util.ArrayList;

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
    
  /** Create new <em>ElasticSearch</em> simple index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  fieldType The indexed field type <tt>geo_point, double)</tt>.
    * @throws LomikelException If anything goes wrong. */
  public void createIndex(String idxName,
                          String fieldName,
                          String fieldType) throws LomikelException { 
    String jsonCmd = new JSONObject().put("mappings",
                                          new JSONObject().put("properties",
                                                               new JSONObject().put(fieldName,
                                                                                    new JSONObject().put("type", fieldType)))).toString();
    log.info("Creating index " + jsonCmd);
    SmallHttpClient httpClient = new SmallHttpClient();
    String answer = httpClient.putJSON(_url + "/" + idxName, jsonCmd, null, null);
    log.info(answer);
    }

  /** Create new <em>ElasticSearch</em> <em>GeoPoint</em> index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @throws LomikelException If anything goes wrong. */
  public void createGeoPointIndex(String idxName,
                                  String fieldName) throws LomikelException {
    createIndex(idxName, fieldName, "geo_point");
    }
  
  /** Create new <em>ElasticSearch</em> double index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @throws LomikelException If anything goes wrong. */
  public void createDoubleIndex(String idxName,
                                String fieldName) throws LomikelException {
    createIndex(idxName, fieldName, "double");
    }
  
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
    String jsonCmd = new JSONObject().put("text", rowkey)
                                     .put(fieldName,
                                          new JSONObject().put("lat", lat)
                                                          .put("lon", lon)).toString();
    log.info("Inserting " + jsonCmd);
    SmallHttpClient httpClient = new SmallHttpClient();
    String answer = httpClient.postJSON(_url + "/" + idxName + "/_doc" , jsonCmd, null, null);
    }
    
  /** Insert new double value entry into index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  rowkey    The rowkey value.
    * @param  value     The double value.
    * @throws LomikelException If anything goes wrong. */
  public void putDouble(String idxName, 
                        String fieldName,
                        String rowkey,
                        double value) throws LomikelException { 
    String jsonCmd = new JSONObject().put("text",    rowkey)
                                     .put(fieldName, value).toString();
    log.info("Inserting " + jsonCmd);
    SmallHttpClient httpClient = new SmallHttpClient();
    String answer = httpClient.postJSON(_url + "/" + idxName + "/_doc" , jsonCmd, null, null);
    }

  /** Search <em>ra,dec</em> <em>GeoPoint</em>s from index.                                                                                                                                                                                                     
    * @param  idxName   The index name.                                                                                                                                                                                                                           
    * @param  fieldName The indexed field name.
    * @param  ra        The <tt>ra</tt> value.                                                                                                                                                                                                                    
    * @param  dec       The <tt>dec</tt> value.                                                                                                                                                                                                                   
    * @param  ang       The cone ang value.                                                                                                                                                                                                                       
    * @return           The rowkey value.                                                                                                                                                                                                                         
    * @throws LomikelException If anything goes wrong. */
  public List<String> searchGeoPoint(String idxName,
                                     String fieldName,
                                     double ra,
                                     double dec,
                                     double ang) throws LomikelException {
    double lat = dec;
    double lon = ra - 180;
    double dist = ang * Math.PI * 6371008.7714 / 180.0; //org.elasticsearch.common.geo.GeoUtils.EARTH_MEAN_RADIUS                                                                                                                                               
    String answer = "no answer";
    List<String> results = new ArrayList<>();
    try {
      String jsonCmd = new JSONObject().put("query",
                                            new JSONObject().put("geo_distance",
                                                                 new JSONObject().put(fieldName,
                                                                                      new JSONObject().put("lat", lat)
                                                                                                      .put("lon", lon))
                                                                                 .put("distance", dist))).toString();
      log.info("Searching " + jsonCmd);
      SmallHttpClient httpClient = new SmallHttpClient();
      answer = httpClient.postJSON(_url + "/" + idxName + "/_search", jsonCmd, null, null);
      JSONObject answerJ = new JSONObject(answer);
      JSONArray hitsJ = answerJ.getJSONObject("hits").getJSONArray("hits");
      for (Object o : hitsJ) {
        results.add(((JSONObject)o).getJSONObject("_source").getString("text"));
        }
      }
    catch (Exception e) {
      log.error("No results found", e);
      log.info("Elastic search answer:\t" + answer);
      }
    log.info("" + results.size() + " results found");
    return results;
    }
    
  /** Search double value from index.
    * @param  idxName   The index name.
    * @param  fieldName The indexed field name.
    * @param  minValue  The minimal double value.
    * @param  maxValue  The maximal double value.
    * @return           The rowkey value.
    * @throws LomikelException If anything goes wrong. */
  public List<String> searchDouble(String idxName,
                                   String fieldName,
                                   double minValue,
                                   double maxValue) throws LomikelException {
    String answer = "no answer";
    List<String> results = new ArrayList<>();
    try {
      String jsonCmd = new JSONObject().put("query",
                                            new JSONObject().put("range",
                                                                 new JSONObject().put(fieldName,
                                                                                      new JSONObject().put("gte", minValue)
                                                                                                      .put("lte", maxValue)))).toString();
      log.info("Searching " + jsonCmd);
      SmallHttpClient httpClient = new SmallHttpClient();
      answer = httpClient.postJSON(_url + "/" + idxName + "/_search", jsonCmd, null, null);
      JSONObject answerJ = new JSONObject(answer);
      JSONArray hitsJ = answerJ.getJSONObject("hits").getJSONArray("hits");
      for (Object o : hitsJ) {
        results.add(((JSONObject)o).getJSONObject("_source").getString("text"));
        }
      }
    catch (Exception e) {
      log.error("No results found", e);
      log.info("Elastic search answer:\t" + answer);
      }
    log.info("" + results.size() + " results found");
    return results;
    }
 
  private String _url;
        
  /** Logging . */
  private static Logger log = LogManager.getLogger(ESClient.class);

  }
