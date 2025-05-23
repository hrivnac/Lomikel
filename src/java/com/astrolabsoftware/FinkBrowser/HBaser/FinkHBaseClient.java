package com.astrolabsoftware.FinkBrowser.HBaser;

import com.Lomikel.HBaser.HBaseClient;
import com.Lomikel.HBaser.HBaseSQLClient;
import com.Lomikel.Utils.DateTimeManagement;
import com.Lomikel.Utils.Pair;
import com.Lomikel.Utils.LomikelException;

// HealPix
import cds.healpix.Healpix;
import cds.healpix.HealpixNested;
import cds.healpix.HealpixNestedFixedRadiusConeComputer;
import cds.healpix.HealpixNestedBMOC;
import cds.healpix.FlatHashIterator;
import static cds.healpix.VerticesAndPathComputer.LON_INDEX;
import static cds.healpix.VerticesAndPathComputer.LAT_INDEX;

// HBase
import org.apache.hadoop.hbase.TableExistsException;

// Java
import java.lang.Math;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.io.IOException;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>FinkHBaseClient</code> handles connectionto HBase table
  * with specific Fink functionality. 
  * It expects the main table with schema and two schemaless aux tables:
  * <ul>
  * <li><b>*.jd</b> table with <code>key = jd.alert</code> and one
  * column <code>i:objectId</code>.</li>
  * <li><b>*.pixel</b> table with <code>key = pixel_jd</code> and
  * columns <code>i:objectId,i:dec,i:ra</code><li>
  * </ul>
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class FinkHBaseClient extends HBaseSQLClient {
   
  /** Create.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public FinkHBaseClient(String zookeepers,
                         String clientPort) throws LomikelException {
    super(zookeepers, clientPort);
    //setFinkEvaluatorFunctions();
    }
       
  /** Create.
    * @param zookeepers The comma-separated list of zookeper ids.
    * @param clientPort The client port. 
    * @throws LomikelException If anything goes wrong. */
  public FinkHBaseClient(String zookeepers,
                         int    clientPort) throws LomikelException {
    super(zookeepers, clientPort);
    //setFinkEvaluatorFunctions();
    }
   
  /** Create.
    * @param url The HBase url.
    * @throws LomikelException If anything goes wrong. */
  public FinkHBaseClient(String url) throws LomikelException {
    super(url);
    //setFinkEvaluatorFunctions();
    }
   
  /** Create on <em>localhost</em>.
    * @throws LomikelException If anything goes wrong. */
  // TBD: is it needed, does it work ok ?
  public FinkHBaseClient() throws LomikelException {
    super(null, null);
    setFinkEvaluatorFunctions();
    }
    
  /** Setup the default sets of evaluation functions. */
  private void setFinkEvaluatorFunctions() {
    try {
      evaluator().setEvaluatorFunctions("com.astrolabsoftware.FinkBrowser.HBaser.FinkEvaluatorFunctions", "com/astrolabsoftware/FinkBrowser/HBaser/FinkEvaluatorFunctions.groovy");
      evaluator().setEvaluatorFunctions(null, "com/astrolabsoftware/FinkBrowser/WebService/FinkHBaseColumnsProcessor.groovy");
      }
    catch (LomikelException e) {
      log.error("Cannot set EvaluatorFunctions", e);
      }
    }
   
  /** Get alerts between two Julian dates (inclusive).
    * @param jdStart   The starting Julian date (including day franction).
    * @param jdStop    The stopping Julian date (including day franction).
    * @param reversed  Wheter results should be reversly ordered.
    *                  <tt>true</tt> implies that results limits will be counted backwards.
    * @param filter    The names of required values as <tt>family:column,...</tt>.
    *                  It can be <tt>null</tt>.
    * @param ifkey     Whether give also entries keys.
    * @param iftime    Whether give also entries timestamps.
    * @return          The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> search(String  jdStart,
                                                 String  jdStop,
                                                 boolean reversed,
                                                 String  filter,
                                                 boolean ifkey,
                                                 boolean iftime)  {
    log.debug("Searching for alerts in jd interval: " + jdStart + " - " + jdStop);
    Map<String, String> searchMap = jd2keys(jdStart, jdStop, reversed);
    if (searchMap.isEmpty()) {
      return new TreeMap<String, Map<String, String>>();
      }
   // searching each entry separately to profit from HBase start/stop row optimisation
    Map<String, Map<String, String>> allResults = new TreeMap<>();
    Map<String, Map<String, String>> aResult;
    Map<String, String> sMap;
    for (String key : searchMap.get("key:key:exact").split(",")) {
      aResult = scan(null,
                "key:key:" + key + ":exact",
                filter,
                0,
                0,
                ifkey,
                iftime);
      allResults.putAll(aResult);
      }
    return allResults;
    }
    
   /** Get alerts within a spacial cone (inclusive).
    * @param ra     The central value of ra (in deg).
    * @param dec    The central value of dec (in deg).
    * @param delta  The maximal angular distance from the central direction (in deg).
    * @param filter The names of required values as <tt>family:column,...</tt>.
    *               It can be <tt>null</tt>.
    * @param ifkey  Whether give also entries keys.
    * @param iftime Whether give also entries timestamps.
    * @return       The {@link Map} of {@link Map}s of results as <tt>key-&t;{family:column-&gt;value}</tt>. */
  public Map<String, Map<String, String>> search(double  ra,
                                                 double  dec,
                                                 double  delta,
                                                 String  filter,
                                                 boolean ifkey,
                                                 boolean iftime)  {
    log.debug("Searching for alerts within " + delta + " deg of (ra, dec) = (" + ra + ", " + dec + ")");
    Map<String, String> searchMap = radec2keys(ra, dec, delta);
    if (searchMap.isEmpty()) {
      return new TreeMap<String, Map<String, String>>();
      }
    // searching each entry separately to profit from HBase start/stop row optimisation
    Map<String, Map<String, String>> allResults = new TreeMap<>();
    Map<String, Map<String, String>> aResult;
    Map<String, String> sMap;
    for (String key : searchMap.get("key:key:exact").split(",")) {
      aResult = scan(null,
                "key:key:" + key + ":exact",
                filter,
                0,
                0,
                ifkey,
                iftime);
      allResults.putAll(aResult);
      }
    return allResults;
    }
  
  /** Give all objectIds corresponding to specified Julian Date.
    * It uses *.jd table.
    * @param jd        The Julian Data (with day fraction).
    * @param reversed  Wheter results should be reversly ordered.
    *                  <tt>true</tt> implies that results limits will be counted backwards.
    * @return          The {@link Map} of corresponding keys of the main table,
    *                  in the format expected for the scan methods. */
  public Map<String, String> jd2keys(String jd,
                                     boolean reversed) {
    Map<String, String> searchMap = new TreeMap<>();
    try {
      HBaseClient client = new HBaseClient(zookeepers(), clientPort());
      client.connect(tableName() + ".jd");
      client.setReversed(reversed);
      client.setLimit(limit());
      client.setSearchLimit(searchLimit());
      Map<String, Map<String, String>> results = client.scan(null,
                                                             "key:key:" + jd,
                                                             null,
                                                             0,
                                                             0,
                                                             false,
                                                             false);
      String keys = results.keySet().stream().map(m -> {String[] key = m.split("_"); return key[1] + "_" + key[0];}).collect(Collectors.joining(","));
      if (keys != null && !keys.trim().equals("")) { 
        searchMap.put("key:key:exact", keys);
        }
      client.close();
      }
    catch (LomikelException e) {
      log.error("Cannot search", e);
      }
    return searchMap;
    }
   
  /** Give all objectIds between two specified Julian Dates (inclusive).
    * It uses *.jd table.
    * @param jdStart   The start Julian Data (with day fraction), evaluated as literal prefix scan.
    * @param jdStart   The stop Julian Data (with day fraction), evaluated as literal prefix scan.
    * @param reversed  Wheter results should be reversly ordered.
    *                  <tt>true</tt> implies that results limits will be counted backwards.
    * @return          The {@link Map} of corresponding keys of the main table,
    *                  in the format expected for the scan methods. */
  public Map<String, String> jd2keys(String jdStart,
                                     String jdStop,
                                     boolean reversed)  {
    Map<String, String> searchMap = new TreeMap<>();
    try {
      HBaseClient client = new HBaseClient(zookeepers(), clientPort());
      client.connect(tableName() + ".jd");
      client.setRangeScan(true);
      client.setReversed(reversed);
      client.setLimit(limit());
      client.setSearchLimit(searchLimit());
      Map<String, Map<String, String>> results = client.scan(null,
                                                             "key:key:" + jdStart + ":prefix," + "key:key:" + jdStop + ":prefix",
                                                             null,
                                                             0,
                                                             0,
                                                             false,
                                                             false);
      String keys = results.keySet().stream().map(m -> {String[] key = m.split("_"); return key[1] + "_" + key[0];}).collect(Collectors.joining(","));
      if (keys != null && !keys.trim().equals("")) { 
        searchMap.put("key:key:exact", keys);
        }
      client.close();
      }
    catch (LomikelException e) {
      log.error("Cannot search", e);
      }
    return searchMap;
    }
    
  /** Give all objectIds within a spacial cone.
    * It uses *.pixel table.
    * @param ra    The central value of ra/lon  (in deg).
    * @param dec   The central value of dec/lat (in deg).
    * @param delta The maximal angular distance from the central direction (in deg).
    * @return      The {@link Map} of corresponding keys of the main table,
    *              in the format expected for the scan methods. */
  public Map<String, String> radec2keys(double ra,
                                        double dec,
                                        double delta)  {
    double coneCenterLon = Math.toRadians(ra);
    double coneCenterLat = Math.toRadians(dec);
    double coneRadiusDel = Math.toRadians(delta);
    //HealpixNestedFixedRadiusConeComputer cc = _hn.newConeComputer(coneRadiusDel);     // beta code!!
    HealpixNestedFixedRadiusConeComputer cc = _hn.newConeComputerApprox(coneRadiusDel); // robust code
    HealpixNestedBMOC bmoc = cc.overlappingCenters(coneCenterLon, coneCenterLat);
    String pixs = "" + _hn.toRing(_hn.hash(coneCenterLon, coneCenterLat));
    log.debug("Central pixel: " + pixs);
    int n = 0;
    FlatHashIterator hIt = bmoc.flatHashIterator();
    //while (hIt.hasNext()) {
    //  pixs +=  _hn.toRing(hIt.next()) + ",";
    //  n++;
    //  }
    for (HealpixNestedBMOC.CurrentValueAccessor cell : bmoc) {
      // cell.getDepth(), cell.isFull(), cell.getRawValue()
      pixs += "," + _hn.toRing(cell.getHash());
      n++;
      } 
    log.debug("" + n + " cells found (using nside = " + _NSIDE + ", depth = " + Healpix.depth(_NSIDE) + ")");
    Map<String, String> pixMap = new TreeMap<>();
    pixMap.put("key:key:prefix", pixs);
    Map<String, String> searchMap = new TreeMap<>();
    try {
      HBaseClient client = new HBaseClient(zookeepers(), clientPort());
      client.connect(tableName() + ".pixel", null);
      client.setLimit(limit());
      client.setSearchLimit(searchLimit());
      Map<String, Map<String, String>> results = client.scan(null,
                                                             pixMap,
                                                             "i:objectId",
                                                             0,
                                                             0,
                                                             false,
                                                             false);
      //log.info(results);
      String keys = results.values().stream().map(m -> m.get("i:objectId")).collect(Collectors.joining(","));
      if (keys != null && !keys.trim().equals("")) { 
        searchMap.put("key:key:prefix", keys);
        }
      client.close();
      }
    catch (LomikelException e) {
      log.error("Cannot search", e);
      }
    return searchMap;
    }
    
  /** Give the timeline for the column. It makes use of the Julian Date alert time
    * instead of HBase timestamp. 
    * @param columnName The name of the column.
    * @param search     The search terms as <tt>family:column:value,...</tt>.
    *                   Key can be searched with <tt>family:column = key:key<tt> "pseudo-name".
    *                   {@link Comparator} can be chosen as <tt>family:column:value:comparator</tt>
    *                   among <tt>exact,prefix,substring,regex</tt>.
    *                   The default for key is <tt>prefix</tt>,
    *                   the default for columns is <tt>substring</tt>.
    *                   It can be <tt>null</tt>.
    *                   All searches are executed as prefix searches.    
    * @return         The {@link Set} of {@link Pair}s of JulianDate-value. */
  @Override
  public Set<Pair<String, String>> timeline(String columnName,
                                            String search) {
    log.debug("Getting alerts timeline of " + columnName + " with " + search);
    Set<Pair<String, String>> tl = new TreeSet<>();
    Map<String, Map<String, String>> results = scan(null, search, columnName + ",i:jd", 0, false, false);
    Pair<String, String> p;
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      if (!entry.getKey().startsWith("schema")) {
        p = Pair.of(entry.getValue().get("i:jd"    ),
                    entry.getValue().get(columnName));
        tl.add(p);
        }
      }
    return tl;
    }
    
  /** Give all recent values of the column. It makes use of the Julian Date alert time
    * instead of HBase timestamp. 
    * Results are ordered by the Julian Date alert time, so evetual limits on results
    * number will be apllied backwards in Julian date time.
    * @param columnName     The name of the column.
    * @param prefixValue    The column value prefix to search for.
    * @param minutes        How far into the past it should search. 
    * @param getValues      Whether to get column values or row keys.
    * @return               The {@link Set} of different values of that column. */
  @Override
  public Set<String> latests(String  columnName,
                             String  prefixValue,
                             long    minutes,
                             boolean getValues) {
    log.debug("Getting " + columnName + " of alerts prefixed by " + prefixValue + " from last " + minutes + " minutes");
    Set<String> l = new TreeSet<>();
    double nowJD = DateTimeManagement.julianDate();
    double minJD = nowJD - minutes / 60.0 / 24.0;
    Map<String, Map<String, String>> results = search(String.valueOf(minJD),
                                                      String.valueOf(nowJD),
                                                      true,
                                                      columnName,
                                                      false,
                                                      false);
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      l.add(getValues ? entry.getValue().get(columnName) : entry.getKey());
      }
    return l;
    }
    
  /** Give all recent values of the column.
    * The original implementation from {@link HBaseClient}.
    * Results are ordered by the row key, so evetual limits on results
    * number will be apllied to them and not to the time.
    * @param columnName     The name of the column.
    * @param substringValue The column value substring to search for.
    * @param minutes        How far into the past it should search (in minutes). 
    * @param getValues      Whether to get column values or row keys.
    * @return               The {@link Set} of different values of that column. */
  public Set<String> latestsT(String  columnName,
                              String  prefixValue,
                              long    minutes,
                              boolean getValues) {
    return super.latests(columnName, prefixValue, minutes, getValues);
    }
  
  /** Create aux pixel map hash table.
    * @param keyPrefixSearch The prefix search of row key.
    * @throws LomikelException If anything goes wrong.
    * @throws LomikelException If anything goes wrong. */
  // BUG: should write numberts with schema
  public void createPixelTable(String keyPrefixSearch) throws LomikelException, IOException {
    String pixelTableName = tableName() + ".pixel";
    try {
      create(pixelTableName, new String[]{"i", "b", "d", "a"});
      }
    catch (TableExistsException e) {
      log.warn("Table " + pixelTableName + " already exists, will be reused");
      }
    HBaseClient pixelClient  = new HBaseClient(zookeepers(), clientPort());
    pixelClient.connect(pixelTableName,  null);
    Map<String, Map<String, String>> results = scan(null, "key:key:" + keyPrefixSearch + ":prefix", "i:objectId,i:ra,i:dec", 0, false, false);    
    String objectId;
    String ra;
    String dec;
    String key;
    log.debug("Writing " + pixelTableName + "...");
    int n = 0;
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      objectId = entry.getValue().get("i:objectId");
      ra       = entry.getValue().get("i:ra");
      dec      = entry.getValue().get("i:dec");
      pixelClient.put(Long.toString(_hn.hash(Math.toRadians(Double.valueOf(ra)),
                                             Math.toRadians(Double.valueOf(dec)))) + "_" + objectId,
                      new String[]{"i:ra:"       + ra,
                                   "i:dec:"      + dec,
                                   "i:objectId:" + objectId});
      System.out.print(".");
      if (n++ % 100 == 0) {
        System.out.print(n-1);
        }
      }
    System.out.println();
    log.debug("" + n + " rows written");
    pixelClient.close();
    }
  
  /** Create aux jd map hash table.
    * @param keyPrefixSearch The prefix search of row key.
    * @throws IOException      If anything goes wrong.
    * @throws LomikelException If anything goes wrong. */
  // BUG: should write numbers with schema
  public void createJDTable(String keyPrefixSearch) throws LomikelException, IOException {
    String jdTableName = tableName() + ".jd";
    try {
      create(jdTableName, new String[]{"i", "b", "d", "a"});
      }
    catch (TableExistsException e) {
      log.warn("Table " + jdTableName + " already exists, will be reused");
      }
    HBaseClient jdClient  = new HBaseClient(zookeepers(), clientPort());
    jdClient.connect(jdTableName,  null);
    Map<String, Map<String, String>> results = scan(null, "key:key:" + keyPrefixSearch + ":prefix", "i:objectId,i:jd", 0, false, false);    
    String objectId;
    String jd;
    String key;
    log.debug("Writing " + jdTableName + "...");
    int n = 0;
    for (Map.Entry<String, Map<String, String>> entry : results.entrySet()) {
      objectId = entry.getValue().get("i:objectId");
      jd       = entry.getValue().get("i:jd");
      jdClient.put(jd + "_" + objectId,
                      new String[]{"i:jd:"       + jd,
                                   "i:objectId:" + objectId});
      System.out.print(".");
      if (n++ % 100 == 0) {
        System.out.print(n-1);
        }
      }
    System.out.println();
    log.debug("" + n + " rows written");
    jdClient.close();
    }    
  /** Assemble curves of variable columns from another table
    * as multi-versioned columns of the current table.
    * All previous lightcurves for selected <em>objectId</em>s are deleted.
    * @param sourceClient The {@link HBaseClient} of the source table.
    *                     It should be already opened and connected with appropriate schema.
    * @param objectIds    The comma-separated list of <em>objectIds</em> to extract.
    * @param columns      The comma-separated list of columns (incl. families) to extract.
    * @param schemaName   The name of the schema to be created in the new table.
    *                     The columns in the new table will belong to the <em>c</em> family
    *                     and will have the type of <em>double</em>. */
  public void assembleCurves(HBaseClient sourceClient,
                             String      objectIds,
                             String      columns,
                             String      schemaName) {
    String[] schema = columns.split(",");
    for (int i = 0; i < schema.length; i++) {
      schema[i] = "c:" + schema[i].split(":")[1] + ":double";
      }
    try {
      put(schemaName, schema);
      }
    catch (IOException e) {
      log.error("Cannot create schema " + schemaName + " = " + schema, e);
      }
    try {
      connect(tableName(), schemaName);
      }
    catch (LomikelException e) {
      log.error("Cannot reconnect to " + tableName() + " with new schema", e);
      }
    Map<String, Map<String, String>> results;
    Set<String> curves = new TreeSet<>();
    String value;
    for (String objectId : objectIds.split(",")) {
      delete(objectId);
      results = sourceClient.scan(null, "key:key:" + objectId + ":prefix", columns, 0, false, false);
      log.debug("Adding " + objectId + "[" + results.size() + "]");
      for (Map.Entry<String, Map<String, String>> row : results.entrySet()) {
        curves.clear();
        for (Map.Entry<String, String> e : row.getValue().entrySet()) {
          value = e.getValue();
          if (!value.trim().equals("NaN") && !value.trim().equals("null")) {
            curves.add("c:" + e.getKey().split(":")[1] + ":" + value.trim());
            }
          }
        try {
          if (!curves.isEmpty()) {
            put(objectId, curves.toArray(new String[0]));
            }
          }
        catch (IOException e) {
          log.error("Cannot insert " + objectId + " = " + curves, e);
         }
        }
      }
    }
    
  /** Assemble lightcurves from another table
    * as multi-versioned columns of the current table.
    * All previous lightcurves for selected <em>objectId</em>s are deleted.
    * The colums schema is embedded in this class sourcecode.
    * @param sourceClient The {@link HBaseClient} of the source table.
    *                     It should be already opened and connected with appropriate schema.
    * @param objectIds    The comma-separated list of <em>objectId</em>s to extract. */
   public void assembleLightCurves(HBaseClient sourceClient,
                                   String      objectIds) {
    String columns = "i:jd,d:lc_features_g,d:lc_features_r";
    String schemaName = "schema_lc_0_0_0";
    int slength = LIGHTCURVE_SCHEMA.length;
    String[] schema     = new String[2 * slength];
    String[] subcolumns = new String[2 * slength];
    for (int i = 0; i < slength; i++) {
      schema[    i          ] = "c:lc_g_" + LIGHTCURVE_SCHEMA[i] + ":double";
      schema[    i + slength] = "c:lc_r_" + LIGHTCURVE_SCHEMA[i] + ":double";
      subcolumns[i          ] = "c:lc_g_" + LIGHTCURVE_SCHEMA[i];
      subcolumns[i + slength] = "c:lc_r_" + LIGHTCURVE_SCHEMA[i];
      }
    try {
      put(schemaName, schema);
      }
    catch (IOException e) {
      log.error("Cannot create schema " + schemaName + " = " + schema, e);
      }
    try {
      connect(tableName(), schemaName);
      }
    catch (LomikelException e) {
      log.error("Cannot reconnect to " + tableName() + " with new schema", e);
      }
    Map<String, Map<String, String>> results;
    Set<String> curves = new TreeSet<>();
    int i;
    for (String objectId : objectIds.split(",")) {
      delete(objectId);
      results = sourceClient.scan(null, "key:key:" + objectId + ":prefix", columns, 0, false, false);
      log.debug("Adding " + objectId + "[" + results.size() + "]");
      for (Map.Entry<String, Map<String, String>> row : results.entrySet()) {
        curves.clear();
        i = 0;
        for (Map.Entry<String, String> e : row.getValue().entrySet()) {
          if (e.getValue().contains("]")) {            
            for (String value : e.getValue().replaceAll("\\[", "").replaceAll("]", "").split(",")) {
              if (!value.trim().equals("NaN") && !value.trim().equals("null")) {
                curves.add(subcolumns[i] + ":" + value.trim());
                }
              i++;
              }
            }
          else {
            curves.add("c:jd:" + e.getValue());
            }
          }
        try {
          if (!curves.isEmpty()) {
            put(objectId, curves.toArray(new String[0]));
            }
          }
        catch (IOException e) {
          log.error("Cannot insert " + objectId + " = " + curves, e);
          }
        }
      }
    }
    
  private static String[] LIGHTCURVE_SCHEMA = new String[]{"lc00",
                                                           "lc01",
                                                           "lc02",
                                                           "lc03",
                                                           "lc04",
                                                           "lc05",
                                                           "lc06",
                                                           "lc07",
                                                           "lc08",
                                                           "lc09",
                                                           "lc10",
                                                           "lc11",
                                                           "lc12",
                                                           "lc13",
                                                           "lc14",
                                                           "lc15",
                                                           "lc16",
                                                           "lc17",
                                                           "lc18",
                                                           "lc19",
                                                           "lc20",
                                                           "lc21",
                                                           "lc22",
                                                           "lc23",
                                                           "lc24",
                                                           "lc25",
                                                           "lc26"};
    
  private static int _NSIDE = 131072; // BUG: magic number 
    
  private static HealpixNested _hn = Healpix.getNested(Healpix.depth(_NSIDE));  
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkHBaseClient.class);

  }
