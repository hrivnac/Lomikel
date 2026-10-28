package com.astrolabsoftware.FinkBrowser.HBaserNew;

import com.Lomikel.HBaserNew.HBaseClient;
import com.Lomikel.HBaserNew.HBaseSQLClient;
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
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.Set;
import java.util.TreeSet;
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

  /** Create on <em>localhost</em>. Evaluator functions are installed when
    * a schema-backed evaluation formula is subsequently configured.
    * @throws LomikelException If anything goes wrong. */
  public FinkHBaseClient() throws LomikelException {
    super(null, null);
    }

  @Override
  public void setEvaluation(String formula) {
    super.setEvaluation(formula);
    if (formula != null && !formula.isBlank() && evaluator() != null) {
      setFinkEvaluatorFunctions();
      }
    }

  /** Setup the default sets of evaluation functions. */
  private void setFinkEvaluatorFunctions() {
    try {
      evaluator().setEvaluatorFunctions("com.astrolabsoftware.FinkBrowser.HBaserNew.FinkEvaluatorFunctions", "com/astrolabsoftware/FinkBrowser/HBaserNew/FinkEvaluatorFunctions.groovy");
      evaluator().setEvaluatorFunctions(null, "com/astrolabsoftware/FinkBrowser/WebService/FinkHBaseColumnsProcessor.groovy");
      }
    catch (LomikelException e) {
      log.error("Cannot set EvaluatorFunctions", e);
      }
    }

  /** Create an auxiliary index client; overridable for offline tests. */
  protected HBaseClient auxiliaryClient() throws LomikelException {
    return new HBaseClient(zookeepers(), clientPort()) {
      @Override protected void handleScanIOException(IOException failure) {
        throw new IllegalStateException("Cannot scan auxiliary HBase index", failure);
        }
      };
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
    // The pixel index stores object IDs, not complete alert row keys.
    for (String key : searchMap.get("key:key:prefix").split(",")) {
      aResult = scan(null,
                "key:key:" + key + "_:prefix",
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
    HBaseClient client = null;
    try {
      client = auxiliaryClient();
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
      String keys = results.keySet().stream().map(FinkHBaseClient::mainKeyFromJDIndex)
                           .filter(key -> key != null)
                           .collect(Collectors.joining(","));
      if (keys != null && !keys.trim().equals("")) {
        searchMap.put("key:key:exact", keys);
        }
      }
    catch (LomikelException e) {
      throw new IllegalStateException("Cannot query auxiliary HBase index", e);
      }
    finally {
      if (client != null) client.close();
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
    HBaseClient client = null;
    try {
      client = auxiliaryClient();
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
      String keys = results.keySet().stream().map(FinkHBaseClient::mainKeyFromJDIndex)
                           .filter(key -> key != null)
                           .collect(Collectors.joining(","));
      if (keys != null && !keys.trim().equals("")) {
        searchMap.put("key:key:exact", keys);
        }
      }
    catch (LomikelException e) {
      throw new IllegalStateException("Cannot query auxiliary HBase index", e);
      }
    finally {
      if (client != null) client.close();
      }
    return searchMap;
    }

  /** The first underscore separates a JD from a possibly underscored object ID. */
  private static String mainKeyFromJDIndex(String indexKey) {
    int separator = indexKey.indexOf('_');
    if (separator <= 0 || separator == indexKey.length() - 1) return null;
    return indexKey.substring(separator + 1) + "_" + indexKey.substring(0, separator);
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
    if (!Double.isFinite(ra) || !Double.isFinite(dec) || !Double.isFinite(delta)
        || dec < -90 || dec > 90 || delta < 0 || delta > 180) {
      throw new IllegalArgumentException("Invalid cone coordinates or radius");
      }
    double coneCenterLon = Math.toRadians(ra);
    double coneCenterLat = Math.toRadians(dec);
    double coneRadiusDel = Math.toRadians(delta);
    HealpixNestedFixedRadiusConeComputer cc = _hn.newConeComputerApprox(coneRadiusDel);
    HealpixNestedBMOC bmoc = cc.overlappingCells(coneCenterLon, coneCenterLat);
    // A depth-17 cone can contain millions of pixels; reject oversized requests
    // before allocating or contacting HBase. This index needs a coarser design
    // for large cones, rather than an unbounded OR filter or heap allocation.
    if (bmoc.computeDeepSize() > MAX_CONE_PIXELS) {
      throw new IllegalArgumentException("Cone exceeds " + MAX_CONE_PIXELS + " pixel-index prefixes");
      }
    Map<String, String> searchMap = new TreeMap<>();
    HBaseClient client = null;
    try {
      client = auxiliaryClient();
      client.connect(tableName() + ".pixel", null);
      // Candidate rows outside the cone must not consume the result limit.
      client.setLimit(Integer.MAX_VALUE);
      client.setSearchLimit(Integer.MAX_VALUE);
      client.setSearchOperator("OR");
      Set<String> objectIds = new TreeSet<>();
      int resultLimit = limit() > 0 ? limit() : Integer.MAX_VALUE;
      List<String> batch = new ArrayList<>(PIXEL_BATCH_SIZE);
      long centralPixel = _hn.hash(coneCenterLon, coneCenterLat);
      batch.add(centralPixel + "_");
      FlatHashIterator pixels = bmoc.flatHashIterator();
      while (pixels.hasNext() && objectIds.size() < resultLimit) {
        long pixel = pixels.next();
        if (pixel != centralPixel) batch.add(pixel + "_");
        if (batch.size() == PIXEL_BATCH_SIZE) {
          addPixelBatch(client, batch, ra, dec, delta, objectIds, resultLimit);
          batch.clear();
          }
        }
      if (!batch.isEmpty() && objectIds.size() < resultLimit) {
        addPixelBatch(client, batch, ra, dec, delta, objectIds, resultLimit);
        }
      if (!objectIds.isEmpty()) {
        searchMap.put("key:key:prefix", String.join(",", objectIds));
        }
      }
    catch (LomikelException e) {
      throw new IllegalStateException("Cannot query auxiliary HBase index", e);
      }
    finally {
      if (client != null) client.close();
      }
    return searchMap;
    }

  /** Query one bounded OR batch, closing its raw scanner before the next batch. */
  private void addPixelBatch(HBaseClient client, List<String> batch,
                             double ra, double dec, double delta,
                             Set<String> objectIds, int resultLimit) {
    Map<String, String> pixMap = Map.of("key:key:prefix", String.join(",", batch));
    try {
      Map<String, Map<String, String>> results = client.scan(null, pixMap,
                                                              "i:objectId,i:ra,i:dec",
                                                              0, 0, false, false);
      for (Map<String, String> row : results.values()) {
        if (withinCone(ra, dec, delta, row)) {
          String id = row.get("i:objectId");
          if (id != null && !id.isBlank()) objectIds.add(id);
          if (objectIds.size() >= resultLimit) break;
          }
        }
      }
    finally {
      client.closeResultScanner();
      }
    }

  /** Check the actual position, not just its coarse HEALPix candidate cell. */
  private static boolean withinCone(double ra, double dec, double delta,
                                    Map<String, String> row) {
    try {
      double otherRa = Double.parseDouble(row.get("i:ra"));
      double otherDec = Double.parseDouble(row.get("i:dec"));
      if (!Double.isFinite(otherRa) || !Double.isFinite(otherDec) || otherDec < -90 || otherDec > 90) {
        return false;
        }
      double dLat = Math.toRadians(otherDec - dec) / 2;
      double dLon = Math.toRadians(otherRa - ra) / 2;
      double a = Math.pow(Math.sin(dLat), 2)
               + Math.cos(Math.toRadians(dec)) * Math.cos(Math.toRadians(otherDec))
               * Math.pow(Math.sin(dLon), 2);
      return 2 * Math.asin(Math.min(1, Math.sqrt(Math.max(0, a)))) <= Math.toRadians(delta) + 1e-12;
      }
    catch (NullPointerException | NumberFormatException badIndexRow) {
      return false;
      }
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
    String sourceTableName = tableName();
    String pixelTableName = sourceTableName + ".pixel";
    try {
      create(pixelTableName, new String[]{"i", "b", "d", "a"});
      }
    catch (TableExistsException e) {
      log.warn("Table " + pixelTableName + " already exists, will be reused");
      }
    finally {
      // HBaseClient.create mutates the connected client's tableName metadata.
      setTableName(sourceTableName);
      }
    HBaseClient pixelClient = auxiliaryClient();
    try {
      pixelClient.connect(pixelTableName, null);
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
      }
    finally {
      pixelClient.close();
      }
    }

  /** Create aux jd map hash table.
    * @param keyPrefixSearch The prefix search of row key.
    * @throws IOException      If anything goes wrong.
    * @throws LomikelException If anything goes wrong. */
  // BUG: should write numbers with schema
  public void createJDTable(String keyPrefixSearch) throws LomikelException, IOException {
    String sourceTableName = tableName();
    String jdTableName = sourceTableName + ".jd";
    try {
      create(jdTableName, new String[]{"i", "b", "d", "a"});
      }
    catch (TableExistsException e) {
      log.warn("Table " + jdTableName + " already exists, will be reused");
      }
    finally {
      setTableName(sourceTableName);
      }
    HBaseClient jdClient = auxiliaryClient();
    try {
      jdClient.connect(jdTableName, null);
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
      }
    finally {
      jdClient.close();
      }
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

  private static final int PIXEL_BATCH_SIZE = 128;
  private static final long MAX_CONE_PIXELS = 100_000;
  private static int _NSIDE = 131072; // On-disk pixel index uses this depth.

  private static HealpixNested _hn = Healpix.getNested(Healpix.depth(_NSIDE));

  /** Logging . */
  private static Logger log = LogManager.getLogger(FinkHBaseClient.class);

  }
