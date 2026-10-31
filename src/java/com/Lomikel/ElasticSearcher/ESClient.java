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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.Base64;
import java.nio.charset.StandardCharsets;

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
    if (_auth == null) {
      _auth = new HashMap<>();
      _auth.put("Authorization", "Basic " + Base64.getEncoder().encodeToString(CREDENTIALS.getBytes(StandardCharsets.UTF_8)));
      }
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
    String answer = _httpClient.putJSON(_url + "/" + idxName, jsonCmd, _auth, null);
    log.info(answer);
    }
  
  // Delete/clean index =========================================================

  /** Delete an entire <em>ElasticSearch</em> index, including its mappings.
    * The name must identify one ordinary index (no wildcards or aliases).
    * Commit or discard pending bulk writes before deleting it.
    * @param  idxName The exact index name.
    * @throws LomikelException If the request fails or is not acknowledged. */
  public synchronized void deleteIndex(String idxName) throws LomikelException {
    checkIndexLifecycleName(idxName);
    String answer = _httpClient.delete(_url + "/" + idxName, _auth);
    try {
      JSONObject result = new JSONObject(answer);
      if (result.has("error") || !Boolean.TRUE.equals(result.opt("acknowledged"))) {
        throw new LomikelException("Index deletion was not acknowledged for " + idxName);
        }
      }
    catch (RuntimeException e) {
      throw new LomikelException("Invalid index deletion response for " + idxName, e);
      }
    log.info("Deleted index " + idxName);
    }

  /** Remove every document from an index while retaining its mappings.
    * The deletion completes synchronously and refreshes the index. Concurrent
    * writers can add documents afterwards. Reject a partial deletion.
    * @param  idxName The exact index name.
    * @throws LomikelException If the request fails or any document remains
    *                          undeleted in the reported batch. */
  public synchronized void cleanIndex(String idxName) throws LomikelException {
    checkIndexLifecycleName(idxName);
    String query = new JSONObject().put("query", new JSONObject().put("match_all", new JSONObject())).toString();
    String answer = _httpClient.postJSON(_url + "/" + idxName + "/_delete_by_query?refresh=true&wait_for_completion=true",
                                         query, _auth, null);
    try {
      JSONObject result = new JSONObject(answer);
      long total = result.getLong("total");
      long deleted = result.getLong("deleted");
      long conflicts = result.getLong("version_conflicts");
      JSONArray failures = result.getJSONArray("failures");
      if (result.has("error") || !Boolean.FALSE.equals(result.opt("timed_out")) ||
          total < 0 || deleted < 0 || total != deleted || conflicts != 0 || failures.length() != 0) {
        throw new LomikelException("Incomplete index cleaning for " + idxName);
        }
      log.info("Cleaned " + deleted + " documents from index " + idxName);
      }
    catch (RuntimeException e) {
      throw new LomikelException("Invalid index cleaning response for " + idxName, e);
      }
    }

  /** Keep destructive operations confined to one concrete index, not an alias.
    * Elasticsearch's delete-by-query accepts aliases, including aliases that
    * expand to multiple indices. Its index-info response is keyed by concrete
    * index name, so require exactly the requested name before any mutation. */
  private void checkIndexLifecycleName(String idxName) throws LomikelException {
    if (idxName == null || idxName.length() > 255 ||
        !idxName.matches("[a-z0-9][a-z0-9_.-]*")) {
      throw new LomikelException("Invalid index name for destructive operation");
      }
    List<String> pending = _commands.get(idxName);
    if (pending != null && !pending.isEmpty()) {
      throw new LomikelException("Pending bulk writes for " + idxName + "; commit before changing the index");
      }
    for (BufferedDouble entry : _bufferedDoubles.values()) {
      if (idxName.equals(entry.index))
        throw new LomikelException("Pending buffered doubles for " + idxName + "; flush before changing the index");
      }
    String answer = _httpClient.get(_url + "/" + idxName, _auth);
    try {
      JSONObject indexes = new JSONObject(answer);
      if (indexes.length() != 1 || !indexes.has(idxName)) {
        throw new LomikelException("Not a single concrete index: " + idxName);
        }
      indexes.getJSONObject(idxName);
      }
    catch (RuntimeException e) {
      throw new LomikelException("Invalid index lookup response for " + idxName, e);
      }
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
    //String answer = _httpClient.postJSON(_url + "/" + idxName + "/_doc" , jsonCmd, _auth, null);
    String answer = _httpClient.postNDJSON(_url + "/" + idxName + "/_bulk" , jsonCmd + "\n", _auth, null);
    JSONObject answerJson;
    try {
      answerJson = new JSONObject(answer);
      answerJson.getBoolean("errors");
      answerJson.getJSONArray("items");
      }
    catch (RuntimeException e) {
      throw new LomikelException("Invalid bulk response for " + idxName + ": " + answer, e);
      }
    JSONArray items = answerJson.getJSONArray("items");
    if (items.length() != command.size() / 2) {
      throw new LomikelException("Incomplete bulk response for " + idxName +
                                 ": expected " + (command.size() / 2) +
                                 " items, got " + items.length());
      }
    List<String> failures = new ArrayList<>();
    for (int i = 0; i < items.length(); i++) {
      try {
        JSONObject expected = new JSONObject(command.get(i * 2));
        String action = expected.keys().next();
        String id = expected.getJSONObject(action).getString("_id");
        JSONObject operation = items.getJSONObject(i);
        if (operation.length() != 1 || !operation.has(action)) {
          failures.add("item " + i + " expected " + action + " _id=" + id +
                       " but got " + operation);
          continue;
          }
        JSONObject item = operation.getJSONObject(action);
        Object statusValue = item.opt("status");
        if (!(statusValue instanceof Number) ||
            ((Number) statusValue).intValue() != ((Number) statusValue).doubleValue() ||
            !id.equals(item.optString("_id", null)) ||
            item.has("error") || ((Number) statusValue).intValue() < 200 ||
            ((Number) statusValue).intValue() >= 300) {
          failures.add("item " + i + " " + action + " expected _id=" + id +
                       " got _id=" + item.optString("_id", "?") +
                       " status=" + statusValue + " error=" + item.opt("error"));
          }
        }
      catch (RuntimeException e) {
        throw new LomikelException("Invalid bulk item " + i + " for " + idxName +
                                   ": " + items.opt(i), e);
        }
      }
    if (answerJson.getBoolean("errors") || !failures.isEmpty()) {
      throw new LomikelException("Bulk failure for " + idxName + ": " + failures);
      }
    log.info("Imported: " + items.length() + " of " + idxName);
    _commands.remove(idxName);
    }
    
  /** Commit all new values into index.
    * @throws LomikelException If an index fails. */
  public void commit() throws LomikelException {
    for (String idxName : new ArrayList<>(_commands.keySet())) {
      commit(idxName);
      }
    }
    
  /** Commit all new values into index.
    * @param n Number of attempts.
    * @throws LomikelException If all attempts fail. */
  public void commitWithRetry(int n) throws LomikelException {
    if (n <= 0) throw new IllegalArgumentException("Attempts must be positive: " + n);
    for (String k : new ArrayList<>(_commands.keySet())) {
      int m = n;
      while (m > 0) {
        try {
          commit(k);
          m = 0;
          }
        catch (LomikelException e) {
          m--;
          if (m == 0) {
            throw new LomikelException("Cannot commit " + k + " after " + n + " attempts: " + e.getMessage(), e);
            }
          else {
            log.warn("Retrying commit, m = " + m);
            }
          }
        }
      }
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
    List<String> results = new ArrayList<>();
    log.info("Searching " + jsonCmd);
    String answer;
    try {
      answer = _httpClient.postJSON(_url + "/" + idxName + "/_search", jsonCmd, _auth, null);
      }
    catch (LomikelException e) {
      throw new LomikelException("Search failed for " + idxName, e);
      }
    try {
      JSONObject answerJ = new JSONObject(answer);
      JSONArray hitsJ = answerJ.getJSONObject("hits").getJSONArray("hits");
      for (Object o : hitsJ) {
        results.add(((JSONObject)o).getString("_id"));
        }
      }
    catch (RuntimeException e) {
      throw new LomikelException("Invalid search response for " + idxName + ": " + answer, e);
      }
    log.info("" + results.size() + " results found");
    return results;
    }
        
  // Get =======================================================================  
    
  /** Get field value from index.
    * @param  idxName   The index name.
    * @param  idxValue  The index value.
    * @param  fieldName The indexed field name.
    * @return           The field value. <tt>0</tt> if no value found.
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
    * @return           The field values. Empty array if no value found.
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
    
  // Update Array ==============================================================

  /** Opt-in, per-client buffer of idempotent double-array updates. A successful
    * bulk item is removed; an ambiguous response retains its items for replay.
    * A single document update is capped at 64 KiB and a batch at 256 documents.
    * Call flushBufferedDoubleArrays before observing final index sizes. */
  public synchronized void bufferDoubleArray(String idxName, String fieldName,
                                             String idxValue, double fieldValue) throws LomikelException {
    if (idxName == null || !idxName.matches("[a-z0-9][a-z0-9_.-]*") ||
        fieldName == null || fieldName.isEmpty() || idxValue == null ||
        !Double.isFinite(fieldValue)) {
      throw new IllegalArgumentException("Invalid buffered double-array update");
      }
    String key = new JSONArray().put(idxName).put(fieldName).put(idxValue).toString();
    BufferedDouble entry = _bufferedDoubles.get(key);
    if (entry != null && entry.values.contains(fieldValue)) return;
    // Encode only the incoming number. The JSON line's fixed overhead is
    // measured once per document; each subsequent value adds a comma and its
    // ASCII JSON number. Never copy/serialize the growing set per admission.
    String number = new JSONArray().put(fieldValue).toString();
    int emptyBytes = entry == null ? bufferedLine(new BufferedDouble(idxName, fieldName, idxValue))
                                     .getBytes(StandardCharsets.UTF_8).length : 0;
    int delta = number.length() - 2 + (entry == null ? 0 : 1);
    int base = entry == null ? emptyBytes : entry.bytes;
    int candidateBytes = base + delta;
    if (candidateBytes > MAX_BUFFERED_BYTES) {
      // Rotate the full document before accepting the next value. A failed
      // flush leaves the old chunk intact and does not accept the new value.
      if (entry != null) emptyBytes = bufferedLine(new BufferedDouble(idxName, fieldName, idxValue))
                                       .getBytes(StandardCharsets.UTF_8).length;
      if (emptyBytes + number.length() - 2 > MAX_BUFFERED_BYTES)
        throw new IllegalArgumentException("Single buffered document exceeds request byte limit");
      flushBufferedDoubleArrays(10);
      entry = null;
      candidateBytes = emptyBytes + number.length() - 2;
      }
    int oldBytes = entry == null ? 0 : entry.bytes;
    if (_bufferedDoubles.size() >= MAX_BUFFERED_DOCS && entry == null ||
        _bufferedBytes - oldBytes + candidateBytes > MAX_BUFFERED_BYTES) {
      flushBufferedDoubleArrays(10);
      entry = null;
      oldBytes = 0;
      if (emptyBytes == 0) emptyBytes = bufferedLine(new BufferedDouble(idxName, fieldName, idxValue))
                                         .getBytes(StandardCharsets.UTF_8).length;
      candidateBytes = emptyBytes + number.length() - 2;
      }
    if (candidateBytes > MAX_BUFFERED_BYTES)
      throw new IllegalArgumentException("Single buffered document exceeds request byte limit");
    if (entry == null) entry = new BufferedDouble(idxName, fieldName, idxValue);
    entry.values.add(fieldValue);
    entry.bytes = candidateBytes;
    _bufferedBytes += candidateBytes - oldBytes;
    _bufferedDoubles.put(key, entry);
    }

  /** Flush queued updates through Elasticsearch _bulk. Only definite failed
    * items are retried; unknown transport/response outcomes remain replayable.
    * Existing array entries are checked by the Painless script before append. */
  public synchronized void flushBufferedDoubleArrays(int attempts) throws LomikelException {
    if (attempts <= 0) throw new IllegalArgumentException("Attempts must be positive");
    for (int attempt = 0; attempt < attempts && !_bufferedDoubles.isEmpty(); attempt++) {
      List<String> keys = new ArrayList<>(_bufferedDoubles.keySet());
      StringBuilder body = new StringBuilder();
      for (String key : keys) body.append(bufferedLine(_bufferedDoubles.get(key)));
      String answer;
      try {
        answer = _httpClient.postNDJSON(_url + "/_bulk", body.toString(), _auth, null);
        }
      catch (LomikelException e) {
        if (attempt + 1 == attempts) throw new LomikelException("Buffered bulk transport failed after retries", e);
        continue;
        }
      // Validate the entire response before removing anything. On malformed or
      // incomplete responses, replay is safe because the script is idempotent.
      Set<String> succeeded = new LinkedHashSet<>();
      try {
        JSONObject response = new JSONObject(answer);
        boolean errors = response.getBoolean("errors");
        JSONArray items = response.getJSONArray("items");
        if (items.length() != keys.size()) throw new IllegalArgumentException("Incomplete bulk items");
        boolean failed = false;
        for (int i = 0; i < items.length(); i++) {
          BufferedDouble expected = _bufferedDoubles.get(keys.get(i));
          JSONObject operation = items.getJSONObject(i);
          if (operation.length() != 1) throw new IllegalArgumentException("Invalid bulk action");
          JSONObject item = operation.getJSONObject("update");
          if (!expected.id.equals(item.getString("_id")) ||
              !expected.index.equals(item.getString("_index"))) throw new IllegalArgumentException("Mismatched bulk item");
          Object statusValue = item.get("status");
          if (!(statusValue instanceof Number) ||
              ((Number) statusValue).doubleValue() != ((Number) statusValue).intValue())
            throw new IllegalArgumentException("Invalid bulk status");
          int status = ((Number) statusValue).intValue();
          if (status >= 200 && status < 300 && !item.has("error")) {
            String result = item.getString("result");
            if (!result.equals("updated") && !result.equals("created") && !result.equals("noop"))
              throw new IllegalArgumentException("Invalid bulk update result");
            succeeded.add(keys.get(i));
            }
          else failed = true;
          }
        if (errors != failed) throw new IllegalArgumentException("Inconsistent bulk errors flag");
        }
      catch (RuntimeException e) {
        throw new LomikelException("Invalid buffered bulk response; all items retained", e);
        }
      for (String key : succeeded) _bufferedBytes -= _bufferedDoubles.remove(key).bytes;
      }
    if (!_bufferedDoubles.isEmpty()) throw new LomikelException("Buffered double bulk failed after " + attempts +
                                                        " attempts; pending documents=" + _bufferedDoubles.size());
    }


  private String bufferedLine(BufferedDouble entry) {
    JSONObject metadata = new JSONObject().put("update", new JSONObject().put("_index", entry.index)
                                                                  .put("_id", entry.id));
    JSONObject script = new JSONObject().put("lang", "painless")
        .put("source", "def a = ctx._source[params.field]; if (a == null) { a = new ArrayList(); ctx._source[params.field] = a; } boolean changed = false; for (def raw : params.values) { double v = (double) raw; if (!a.contains(v)) { a.add(v); changed = true; } } if (!changed) { ctx.op = 'noop'; }")
        .put("params", new JSONObject().put("field", entry.field).put("values", new JSONArray(entry.values)));
    JSONObject update = new JSONObject().put("scripted_upsert", true).put("script", script)
                                       .put("upsert", new JSONObject());
    return metadata.toString() + "\n" + update.toString() + "\n";
    }

  private static final int MAX_BUFFERED_DOCS = 256;
  private static final int MAX_BUFFERED_BYTES = 65536;
  private int _bufferedBytes;
  private final Map<String, BufferedDouble> _bufferedDoubles = new LinkedHashMap<>();
  private static class BufferedDouble {
    final String index, field, id;
    int bytes;
    final LinkedHashSet<Double> values = new LinkedHashSet<>();
    BufferedDouble(String index, String field, String id) {
      this.index = index; this.field = field; this.id = id;
      }

    }
  
  /** Update values in double array index.
    * It is automatically commited.
    * @param  idxName    The index name.
    * @param  fieldName  The indexed field name.
    * @param  idxValue   The index value.
    * @param  fieldValue The indexed field value to be added to array
    *                    (if not yet present).
    * @param n Number of attempts.
    * @throws LomikelException If all attempts fail. */
  public void updateDoubleArrayWithRetry(String idxName,
                                         String fieldName,
                                         String idxValue,
                                         double fieldValue,
                                         int    n) throws LomikelException {
    if (n <= 0) throw new IllegalArgumentException("Attempts must be positive: " + n);
    int m = n;
    while (m > 0) {
      try {
        updateDoubleArray(idxName, fieldName, idxValue, fieldValue);
        m = 0;
        }
      catch (LomikelException e) {
        m--;
        if (m == 0) {
          throw new LomikelException("Cannot update " + idxName + "/" + fieldName + " = " + idxValue + "/" + fieldValue, e);
          }
        else {
          log.warn("Retrying update, m = " + m);
          }
        }
      }
    }
  /** Update values in geo_point array index.
    * It is automatically commited.
    * @param  idxName    The index name.
    * @param  fieldName  The indexed geo_point field name.
    * @param  idxValue   The index value.
    * @param  ra         The indexed ra value to be added to array
    *                    (if not yet present).
    * @param  dec        The indexed dec value to be added to array
    *                    (if not yet present).
    * @param n Number of attempts.
    * @throws LomikelException If all attempts fail. */
  public void updateGeoPointArrayWithRetry(String idxName,
                                           String fieldName,
                                           String idxValue,
                                           double ra,
                                           double dec,
                                           int    n) throws LomikelException {
    if (n <= 0) throw new IllegalArgumentException("Attempts must be positive: " + n);
    int m = n;
    while (m > 0) {
      try {
        updateGeoPointArray(idxName, fieldName, idxValue, ra, dec);
        m = 0;
        }
      catch (LomikelException e) {
        m--;
        if (m == 0) {
          throw new LomikelException("Cannot update " + idxName + "/" + fieldName + " = " + idxValue + "/" + ra + "-" + dec, e);
          }
        else {
          log.warn("Retrying update, m = " + m);
          }
        }
      }
    }
   
  /** Update values in double array index.
    * It is automatically commited.
    * @param  idxName    The index name.
    * @param  fieldName  The indexed double field name.
    * @param  idxValue   The index value.
    * @param  fieldValue The indexed field value to be added to array
    *                    (if not yet present).
    * @throws LomikelException If anything goes wrong. */
  public void updateDoubleArray(String idxName,
                                String fieldName,
                                String idxValue,
                                double fieldValue) throws LomikelException {
  String script = "{\n" +
                  "  \"scripted_upsert\": true,\n" +
                  "  \"script\": {\n" +
                  "    \"lang\": \"painless\",\n" +
                  "    \"source\": \"double v = (double) params.value; if (ctx._source." + fieldName + " == null) { ctx._source." + fieldName + " = [v]; } else if (!ctx._source." + fieldName + ".contains(v)) { ctx._source." + fieldName + ".add(v); } else { ctx.op = 'noop'; }\",\n" +
                  "    \"params\": {\n" +
                  "      \"value\": " + fieldValue + "\n" +
                  "    }\n" +
                  "  },\n" +
                  "  \"upsert\": {}\n" +
                  "}";                            
    String answer = _httpClient.postNDJSON(_url + "/" + idxName + "/_update/" + idxValue, script, _auth, null);
    checkUpdateResponse(idxName, idxValue, answer);
    }
    
  /** Update values in geo_point array index.
    * It is automatically committed.
    * @param  idxName    The index name.
    * @param  fieldName  The indexed geo_point field name.
    * @param  idxValue   The index value.
    * @param  latValue   The indexed ra value to be added to array
    *                    (if not yet present).
    * @param  lonValue   The indexed dec value to be added to array
    *                    (if not yet present).
    * @throws LomikelException If anything goes wrong. */
  public void updateGeoPointArray(String idxName,
                                  String fieldName,
                                  String idxValue,
                                  double ra,
                                  double dec) throws LomikelException {  
    double lat = dec;
    double lon = ra - 180;
    String script = "{\n" +
                    "  \"scripted_upsert\": true,\n" +
                    "  \"script\": {\n" +
                    "    \"lang\": \"painless\",\n" +
                    "    \"source\": \"Map v = [\\\"lat\\\": params.lat, \\\"lon\\\": params.lon];  " +
                    "if (ctx._source." + fieldName + " == null) { " +
                    "ctx._source." + fieldName + " = new ArrayList(); " +
                    "ctx._source." + fieldName + ".add(v); " +
                    "} else if (ctx._source." + fieldName + " instanceof List) { " +
                    "if (!ctx._source." + fieldName + ".contains(v)) { " +
                    "ctx._source." + fieldName + ".add(v); " +
                    "} else { ctx.op = 'noop'; } " +
                    "} else { " +
                    "def old = ctx._source." + fieldName + "; " +
                    "ctx._source." + fieldName + " = new ArrayList(); " +
                    "ctx._source." + fieldName + ".add(old); " +
                    "if (!ctx._source." + fieldName + ".contains(v)) { " +
                    "ctx._source." + fieldName + ".add(v); " +
                    "} else { ctx.op = 'noop'; } " +
                    "}\",\n" +
                    "    \"params\": {\n" +
                    "      \"lat\": " + lat + ",\n" +
                    "      \"lon\": " + lon + "\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"upsert\": {}\n" +
                    "}"; 
    String answer = _httpClient.postNDJSON(_url + "/" + idxName + "/_update/" + idxValue, script, _auth, null);
    checkUpdateResponse(idxName, idxValue, answer);
    }    
    
  /** Reject an Elasticsearch update error even when the HTTP status is 200. */
  private void checkUpdateResponse(String idxName, String idxValue, String answer) throws LomikelException {
    JSONObject response;
    try {
      response = new JSONObject(answer);
      }
    catch (RuntimeException e) {
      throw new LomikelException("Invalid update response for " + idxName + "/" + idxValue + ": " + answer, e);
      }
    if (response.has("error")) {
      throw new LomikelException("Update failed for " + idxName + "/" + idxValue + ": " + response.get("error"));
      }
    String result = response.optString("result", "");
    if (!result.equals("updated") && !result.equals("created") && !result.equals("noop")) {
      throw new LomikelException("Invalid update result for " + idxName + "/" + idxValue + ": " + result);
      }
    }

  // Info ======================================================================

  /** Give the size of the index.
    * @param  idxName The index name.
    * @return         The size of the index.
    * @throws LomikelException If anything goes wrong. */
  public int size(String idxName) throws LomikelException {
    String answer;
    String jsonCmd = new JSONObject().put("query",
                                          new JSONObject().put("match_all",
                                                               new JSONObject()))
                                     .toString();
    try {
      answer = _httpClient.postJSON(_url + "/" + idxName + "/_count", jsonCmd, _auth, null);
      }
    catch (LomikelException e) {
      throw new LomikelException("Count failed for " + idxName, e);
      }
    try {
      return new JSONObject(answer).getInt("count");
      }
    catch (RuntimeException e) {
      throw new LomikelException("Invalid count response for " + idxName + ": " + answer, e);
      }
    }
    
  // ===========================================================================
  
  /** Set the limit on number of results to show.
    * @param size The limit on number of results to show. The defauld is <tt>10</tt>. */
  public void setSizeSearch(int size) {
    _size = size;
    }
    
  /** Convert astronomical <tt>ra</tt> into geophysical <tt>lon<tt>.
    * @param The astronomical <tt>ra [deg]</tt>.
    * @return The geophysical <tt>lon [deg]<tt>. */
  public static double ra2lon(double ra) {
    double lon = ra % 360.0;
    if (lon < 0) lon += 360.0;
    if (lon > 180.0) lon -= 360.0;
    return lon;
    }
    
  private String _url;
  
  private SmallHttpClient _httpClient = new SmallHttpClient();
  
  private int _size= 10;
  
  private Map<String, String> _auth;
  
  // TBD: it shoould not be hardwired here
  private static String CREDENTIALS = "elastic:elastic";
   
  private ConcurrentMap<String, List<String>> _commands = new ConcurrentHashMap<>();
        
  /** Logging . */
  private static Logger log = LogManager.getLogger(ESClient.class);

  }
