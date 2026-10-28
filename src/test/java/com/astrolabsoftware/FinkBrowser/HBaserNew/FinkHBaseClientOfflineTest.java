package com.astrolabsoftware.FinkBrowser.HBaserNew;

import cds.healpix.Healpix;
import com.Lomikel.HBaserNew.HBaseClient;
import com.Lomikel.HBaserNew.HBaseSchema;
import com.Lomikel.Utils.LomikelException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import sun.misc.Unsafe;

/** Offline regression tests: no HBase connection or writes. */
public class FinkHBaseClientOfflineTest {
  private static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static class FakeFinkClient extends FinkHBaseClient {
    String lastSearch;
    int scans;
    public FakeFinkClient() throws LomikelException { super("unused", "2181"); }
    @Override public Map<String, String> radec2keys(double ra, double dec, double delta) {
      return Map.of("key:key:prefix", "object-1,object-2");
    }
    @Override public Map<String, Map<String, String>> scan(String key, String search,
        String filter, long start, long stop, boolean ifkey, boolean iftime) {
      lastSearch = search;
      scans++;
      return Map.of("alert-" + scans, Map.of("i:objectId", "object-" + scans));
    }
  }

  public static class FakeAuxClient extends HBaseClient {
    Map<String, String> lastPixelSearch;
    boolean closed;
    boolean fail;
    int puts;
    int scans;
    int maxPrefixes;
    int observedLimit;
    int observedSearchLimit;
    boolean explicitOr;
    public FakeAuxClient() throws LomikelException { super("unused", "2181"); }
    @Override public void setSearchOperator(String operator) {
      explicitOr = "OR".equals(operator);
      super.setSearchOperator(operator);
    }
    @Override public org.apache.hadoop.hbase.client.Table connect(String tableName, String schemaName) {
      return null;
    }
    @Override public Map<String, Map<String, String>> scan(String key, Map<String, String> search,
        String filter, long start, long stop, boolean ifkey, boolean iftime) {
      lastPixelSearch = search;
      scans++;
      maxPrefixes = Math.max(maxPrefixes, search.get("key:key:prefix").split(",").length);
      observedLimit = limit();
      observedSearchLimit = searchLimit();
      if (fail) throw new IllegalStateException("synthetic scan failure");
      return Map.of("pixel_1", Map.of("i:objectId", "object-1", "i:ra", "10.0", "i:dec", "20.0"),
                    "pixel_2", Map.of("i:objectId", "object-2", "i:ra", "100.0", "i:dec", "20.0"));
    }
    @Override public Map<String, Map<String, String>> scan(String key, String search,
        String filter, long start, long stop, boolean ifkey, boolean iftime) {
      if (fail) throw new IllegalStateException("synthetic JD scan failure");
      return Map.of("2450000.5_obj_A", Map.of("i:objectId", "obj_A"));
    }
    @Override public void close() { closed = true; }
    @Override public void put(String key, String[] values) throws IOException { puts++; }
  }

  public static class FakePixelClient extends FinkHBaseClient {
    FakeAuxClient aux;
    boolean failFactory;
    public FakePixelClient() throws LomikelException { super("unused", "2181"); }
    @Override public String tableName() { return "alerts"; }
    @Override protected HBaseClient auxiliaryClient() throws LomikelException {
      if (failFactory) throw new LomikelException("synthetic connection failure");
      return aux;
    }
  }

  public static class FakeIndexBuilder extends FinkHBaseClient {
    FakeAuxClient aux;
    public FakeIndexBuilder() throws LomikelException { super("unused", "2181"); }
    @Override public void create(String name, String[] families) { setTableName(name); }
    @Override protected HBaseClient auxiliaryClient() { return aux; }
    @Override public Map<String, Map<String, String>> scan(String key, String search,
        String filter, long delay, boolean ifkey, boolean iftime) {
      return Map.of("alert", Map.of("i:objectId", "object-1", "i:ra", "10.0", "i:dec", "20.0", "i:jd", "2450000.5"));
    }
  }

  private static <T> T unconnected(Class<T> type) throws Exception {
    Field f = Unsafe.class.getDeclaredField("theUnsafe");
    f.setAccessible(true);
    return type.cast(((Unsafe) f.get(null)).allocateInstance(type));
  }

  private static void coneSearchUsesPixelPrefix() throws Exception {
    FakeFinkClient client = unconnected(FakeFinkClient.class);
    Map<String, Map<String, String>> results = client.search(10.0, 20.0, 0.1,
                                                             "i:objectId", false, false);
    require(client.scans == 2, "one main-table prefix scan per unique object ID");
    require("key:key:object-2_:prefix".equals(client.lastSearch),
            "pixel index returns object-ID prefixes, not exact alert row keys");
    require(results.size() == 2, "both alert rows must be returned");
  }

  private static void pixelIndexUsesNestedKeysAndAlwaysClosesAuxiliaryClient() throws Exception {
    FakePixelClient client = unconnected(FakePixelClient.class);
    client.aux = unconnected(FakeAuxClient.class);
    double ra = 10.0, dec = 20.0;
    long nested = Healpix.getNested(Healpix.depth(131072)).hash(Math.toRadians(ra), Math.toRadians(dec));
    Map<String, String> result = client.radec2keys(ra, dec, 0.00001);
    require(client.aux.lastPixelSearch != null, "pixel index not queried");
    Set<String> prefixes = Set.of(client.aux.lastPixelSearch.get("key:key:prefix").split(","));
    require(prefixes.contains(nested + "_"), "query must use the delimited nested hash written by createPixelTable");
    require("object-1".equals(result.get("key:key:prefix")), "object IDs must be returned as prefixes");
    require(client.aux.closed, "auxiliary client must close after successful scan");

    client.aux = unconnected(FakeAuxClient.class);
    client.aux.fail = true;
    boolean failed = false;
    try { client.radec2keys(ra, dec, 0.00001); }
    catch (IllegalStateException expected) { failed = true; }
    require(failed, "synthetic scan failure must propagate");
    require(client.aux.closed, "auxiliary client must close after failed scan");
  }

  private static void jdIndexPreservesUnderscoresAndClosesAuxiliaryClient() throws Exception {
    FakePixelClient client = unconnected(FakePixelClient.class);
    client.aux = unconnected(FakeAuxClient.class);
    require("obj_A_2450000.5".equals(client.jd2keys("2450000.5", false).get("key:key:exact")),
            "JD index must preserve the whole object ID, including underscores");
    require(client.aux.closed, "JD auxiliary client must close after success");
    client.aux = unconnected(FakeAuxClient.class);
    client.aux.fail = true;
    boolean failed = false;
    try { client.jd2keys("2450000.5", false); }
    catch (IllegalStateException expected) { failed = true; }
    require(failed, "synthetic JD scan failure must propagate");
    require(client.aux.closed, "JD auxiliary client must close after failure");
  }

  private static void newEvaluatorResourcesLoadOffline() throws Exception {
    FakeFinkClient client = unconnected(FakeFinkClient.class);
    client.setSchema(new HBaseSchema("test", Map.of("i:jd", "double")));
    client.setEvaluation("jd > 0");
    require(client.evaluator() != null, "schema-backed Fink evaluator should initialize");
  }

  private static void indexConnectionFailureIsNotMistakenForNoMatches() throws Exception {
    FakePixelClient client = unconnected(FakePixelClient.class);
    client.failFactory = true;
    boolean jdFailed = false;
    try { client.jd2keys("2450000.5", false); }
    catch (IllegalStateException expected) { jdFailed = true; }
    require(jdFailed, "JD index connection error must not look like no matches");
    boolean coneFailed = false;
    try { client.radec2keys(10.0, 20.0, 0.00001); }
    catch (IllegalStateException expected) { coneFailed = true; }
    require(coneFailed, "pixel index connection error must not look like no matches");
  }

  private static void indexBuildersRestoreMainTableName() throws Exception {
    FakeIndexBuilder client = unconnected(FakeIndexBuilder.class);
    client.setTableName("alerts");
    client.aux = unconnected(FakeAuxClient.class);
    client.createPixelTable("object-");
    require("alerts".equals(client.tableName()), "pixel-table create must restore source table name");
    require(client.aux.puts == 1 && client.aux.closed, "pixel writer must write and close");
    client.aux = unconnected(FakeAuxClient.class);
    client.createJDTable("object-");
    require("alerts".equals(client.tableName()), "JD-table create must restore source table name");
    require(client.aux.puts == 1 && client.aux.closed, "JD writer must write and close");
  }

  private static void pixelBatchesAreBoundedAndFilterBeforeLimiting() throws Exception {
    FakePixelClient client = unconnected(FakePixelClient.class);
    client.aux = unconnected(FakeAuxClient.class);
    client.setLimit(Integer.MAX_VALUE);
    client.setSearchLimit(Integer.MAX_VALUE);
    client.radec2keys(10.0, 20.0, 0.01);
    require(client.aux.scans > 1, "large cone should be split into bounded batches");
    require(client.aux.maxPrefixes <= 128, "too many HBase prefix filters in one scan");
    require(client.aux.explicitOr, "distinct pixel prefixes require MUST_PASS_ONE semantics");
    client.aux = unconnected(FakeAuxClient.class);
    client.setLimit(1);
    client.setSearchLimit(1);
    Map<String, String> result = client.radec2keys(10.0, 20.0, 0.00001);
    require("object-1".equals(result.get("key:key:prefix")), "filter before result limit");
    require(client.aux.observedLimit == Integer.MAX_VALUE
            && client.aux.observedSearchLimit == Integer.MAX_VALUE,
            "pre-filter HBase limits can discard in-cone rows");
    client.failFactory = true;
    boolean oversized = false;
    try { client.radec2keys(10.0, 20.0, 1.0); }
    catch (IllegalArgumentException expected) { oversized = true; }
    require(oversized, "oversized cone must fail before opening an auxiliary connection");
  }

  public static void main(String[] args) throws Exception {
    require(FinkHBaseClient.class.getSuperclass() == com.Lomikel.HBaserNew.HBaseSQLClient.class,
            "new Fink client must inherit from new Lomikel HBase client");
    require(FinkHBaseClient.class.getMethod("assembleCurves", HBaseClient.class,
            String.class, String.class, String.class) != null,
            "curve assembly must accept the new Lomikel client type");
    require(FinkHBaseClient.class.getMethod("assembleLightCurves", HBaseClient.class,
            String.class) != null,
            "light-curve assembly must accept the new Lomikel client type");
    coneSearchUsesPixelPrefix();
    pixelIndexUsesNestedKeysAndAlwaysClosesAuxiliaryClient();
    jdIndexPreservesUnderscoresAndClosesAuxiliaryClient();
    newEvaluatorResourcesLoadOffline();
    indexConnectionFailureIsNotMistakenForNoMatches();
    indexBuildersRestoreMainTableName();
    pixelBatchesAreBoundedAndFilterBeforeLimiting();
    System.out.println("FinkHBaseClientOfflineTest PASS");
  }
}
