package com.JHTools.HBaser;

import com.JHTools.Utils.SmallHttpClient;
import com.JHTools.Utils.Coding;
import com.JHTools.Utils.CommonException;

// org.json
import org.json.JSONObject;

// Java
import java.util.Map;
import java.util.HashMap;

// Log4J
import org.apache.log4j.Logger;

/** <code>HBaseRESTCLient</code> is the bridge to the <em>HBase</em> REST service:
  * <a href="https://www.cloudera.com/documentation/enterprise/5-9-x/topics/admin_hbase_rest_api.html">API</a>,
  * <a href="https://gist.github.com/stelcheck/3979381">filters</a>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HBaseRESTClient {
  
  /** Connect to the server.
    * @param url The url of the server. */
  public HBaseRESTClient(String url) {
    log.info("Connecting to HBase Server " + url);
    _url = url;
    }
    
  /** Get server status.
    * <pre>
    * GET /version/cluster
    * GET /status/cluster
    * GET /
    * </pre>
    * @return The full server status. */
  public String getStatus() {
    String resultString = "";
    try {
      resultString = SmallHttpClient.get(_url + "/version/cluster", null) + "\n"
                   + SmallHttpClient.get(_url + "/status/cluster",  null) + "\n"
                   + SmallHttpClient.get(_url + "/",                null);
      }
    catch (CommonException e) {
      log.info(e);
      CommonException.reportException("Request has failed", e, log);
      }
    log.debug("Result:\n" + resultString.trim());
    return resultString;
    }
    
  /** Initiate <em>scanner</em> on the server.
    * <pre>
    * PUT /-table-/scanner
    * </pre>
    * @param table  The requested table name.
    * @param filter The scanner filter (as family:column-value:comparator).
    *               May be <tt>null</tt>.
    * @param size   The number of requested results.
    *               <tt>0</tt> means no limit.
    * @param start  The start search time in ms.
    * @param end    The end search time in ms.
    * @return The assigned <em>scanner</em> id. */
  // TBD: parametrise batch size
  public String initScanner(String              table,
                            Map<String, String> filter,
                            int                 size,
                            long                start,
                            long                end) {
    log.info("Creating Scanner for " + table);
    log.info("  with filter: " + filter);
    String scanner ="<Scanner";
    if (size > 0) {
      scanner += " batch='" + size +"'";
      }
    if (start != 0 && end != 0) {
      scanner += " startTime=\"" + String.valueOf(start) + "\" endTime=\"" + String.valueOf(end) + "\"";
      }
    scanner += ">";
    if (filter != null) {
      scanner += "<filter>" + filter(filter) + "</filter>";
      }
    scanner += "</Scanner>";
    String resultString = "";
    JSONObject result = null;
    try {
      resultString = SmallHttpClient.putXML(_url + "/" + table + "/scanner", scanner, null, "Location");
      }
    catch (CommonException e) {
      log.info(e);
      CommonException.reportException("Request has failed", e, log);
      return "";
      }
    log.debug("Result:\n" + resultString.trim());
    return resultString.substring(resultString.lastIndexOf("/") + 1);
    }
    
  /** Get results.
    * <pre>
    * GET /-table-/scanner/-scannerId-
    * </pre>
    * @param table     The requested table name.
    * @param scannerId The assigned <em>scanner</em> id.
    * @return          The command result, in <em>json</em>, values byte-encoded.
    *                  May be empty. */
  public String getResultsEncoded(String table,
                                  String scannerId) {
    Map<String, String> headers = new HashMap<>();
    headers.put("Accept", "application/json");
    headers.put("Content-Type", "application/json");
    String resultString = "";
    JSONObject result = null;
    try {
      resultString = SmallHttpClient.get(_url + "/" + table + "/scanner/" + scannerId, headers);
      result = new JSONObject(resultString);
      }
    catch (CommonException e) {
      log.info(e);
      CommonException.reportException("Request has failed", e, log);
      }
    if (result == null) {
      return "";
      }
    log.debug("Result:\n" + result.toString(2));
    return result.toString(2);
    }
    
  /** Scan table.
    * @param table The requested table name.
    * @param filter The scanner filter (as family:column-value:comparator).
    *               May be <tt>null</tt>.
    * @param size   The number of requested results.
    *               <tt>0</tt> means no limit.
    * @param start  The start search time in ms.
    * @param end    The end search time in ms.
    * @return      The command result, in <em>json</em>, values byte-encoded. */
  public String scanEncoded(String              table,
                            Map<String, String> filter,
                            int                 size,
                            long                start,
                            long                end) {
    String scannerId = initScanner(table, filter, size, start, end);
    return getResultsEncoded(table, scannerId);
    }
    
  /** Initiate <em>scanner</em> on the server.
    * <pre>
    * PUT /-table-/fakerow
    * </pre>
    * @param table   The requested table name.
    * @param key     The row key (byte-encoded).
    * @param entries The {@link Map} of columns in the form <code>family:column -&gt; value</code> (byte-encoded). */
  public void putEncoded(String              table,
                         String              key,
                         Map<String, String> entries) {
    String rowsXML = "<?xml version='1.0' encoding='UTF-8' standalone='yes'?><CellSet><Row key='" + key + "'>";
    for (Map.Entry<String, String> entry : entries.entrySet()) { 
      rowsXML += "<Cell column='" + entry.getKey() + "'>" + entry.getValue() + "</Cell>";
      }
    rowsXML += "</Row></CellSet>";
    try {
      SmallHttpClient.putXML(_url + "/" + table + "/fakerow", rowsXML, null, null);
      }
    catch (CommonException e) {
      log.info(e);
      CommonException.reportException("Request has failed", e, log);
      return;
      }
    }
    
  /** Give <em>HBase</em> url.
    * @return The  <em>HBase</em> url. */
  public String url() {
    return _url;
    }
    
  /** Create <em>REST</em> filter from filter {@link Map}.
    * @param filterMap The {@link Map} of column values to filter,
    *                  in the form <tt>family:column-value:comparator</tt>.
    * @return          The JSON filter. */
  private String filter(Map<String, String> filterMap) {
    String filter = "";
    String[] column;
    String[] value;
    boolean first = true;
    filter = "{\"type\":\"FilterList\","
           + "\"op\":\"MUST_PASS_ALL\","
           + "\"filters\":[";
    for (Map.Entry<String, String> entry : filterMap.entrySet()) {
      column = entry.getKey().split(":");
      value  = entry.getValue().split(":");
      if (value[1].equals("BinaryComparator")) {
        value[0] = Coding.encode(value[0]);
        }
      if (!first) {
        filter += ",";
        }
      else {
        first = false;
        }
      filter += "{"
             +  "\"type\":\"SingleColumnValueFilter\","
             +  "\"op\":\"EQUAL\","
             +  "\"family\":\"" + Coding.encode(column[0]) + "\","
             +  "\"qualifier\":\"" + Coding.encode(column[1]) + "\","
             +  "\"latestVersion\":true,"
             +  "\"ifMissing\":true,"
             +  "\"comparator\":{"
             +  "\"type\":\"" + value[1] + "\","
             +  "\"value\":\"" + value[0] + "\""
             +  "}"
             +  "}";
      
      }
    filter += "]";
    filter += "}";
    return filter; 
    }

  @Override
  public String toString() {
    return "HBaseRESTClient(" + _url + ")";
    }
    
  private String _url;

  /** Logging . */
  private static Logger log = Logger.getLogger(HBaseRESTClient.class);

  }
