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
import org.apache.log4j.Logger;

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
    
  /** Create new <em>ElasticSearch</em> index.
    * @param  idxName The index name.
    * @param  idxType The index type.
    * @throws LomikelException If anything goes wrong. */
  public void createIndex(String idxName,   
                          String idxType) throws LomikelException { 
    String jsonCmd = new JSONObject().put("mappings",
                                          new JSONObject().put("properties",
                                                               new JSONObject().put("location",
                                                                                    new JSONObject().put("type", idxType)))).toString();
    log.info("Creating index " + jsonCmd);
    SmallHttpClient httpClient = new SmallHttpClient();
    String answer = httpClient.putJSON(_url + "/" + idxName, jsonCmd, null, null);
    log.info(answer);
    }
    
  /** Insert new <em>ra,dec</em> <em>GeoPoint</em> entry into index.
    * @param  idxName The index name.
    * @param  value   The rowkey value.
    * @param  ra      The <tt>ra</tt> value.
    * @param  dec     The <tt>dec</tt> value.
    * @throws LomikelException If anything goes wrong. */
  public void putGeoPoint(String idxName, 
                          String value,
                          double ra,
                          double dec) throws LomikelException { 
    double lat = dec;
    double lon = ra - 180;
    String jsonCmd = new JSONObject().put("text", value)
                                     .put("location",
                                          new JSONObject().put("lat", lat)
                                                          .put("lon", lon)).toString();
    log.info("Inserting " + jsonCmd);
    SmallHttpClient httpClient = new SmallHttpClient();
    String answer = httpClient.postJSON(_url + "/" + idxName + "/_doc" , jsonCmd, null, null);
    //log.info(answer);
    }
    
  /** Search <em>ra,dec</em> <em>GeoPoint</em>s from index.
    * @param  idxName The index name.
    * @param  ra      The <tt>ra</tt> value.
    * @param  dec     The <tt>dec</tt> value.
    * @param  ang     The cone ang value.
    * @return value   The rowkey value.
    * @throws LomikelException If anything goes wrong. */
  public List<String> searchGeoPoint(String idxName,
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
                                                                 new JSONObject().put("location",
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
 
  private String _url;
        
  /** Logging . */
  private static Logger log = Logger.getLogger(ESClient.class);

  }
