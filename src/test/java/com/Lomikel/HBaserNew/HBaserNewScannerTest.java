package com.Lomikel.HBaserNew;

import com.Lomikel.DB.SearchMap;
import com.Lomikel.Utils.LomikelException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import sun.misc.Unsafe;

/** Offline scanner ownership and scan-configuration checks. No HBase connection. */
public class HBaserNewScannerTest {
  private static void require(boolean yes, String message) {
    if (!yes) throw new AssertionError(message);
  }

  public static class UnconnectedClient extends HBaseClient {
    Table fake;
    public UnconnectedClient() throws LomikelException { super("unused", "2181"); }
    @Override public Table table() { return fake; }
  }

  private static class ScannerFixture {
    final AtomicBoolean closed = new AtomicBoolean();
    boolean failOnIteration;
    ScannerFixture(boolean fail) { failOnIteration = fail; }
    final ResultScanner scanner = (ResultScanner) Proxy.newProxyInstance(
      ResultScanner.class.getClassLoader(), new Class<?>[]{ResultScanner.class},
      (proxy, method, args) -> {
        if (method.getName().equals("close")) { closed.set(true); return null; }
        if (method.getName().equals("iterator")) {
          if (failOnIteration) throw new IllegalStateException("synthetic scanner failure");
          return List.of(Result.EMPTY_RESULT).iterator();
        }
        return null;
      });
  }

  private static class ClientFixture {
    UnconnectedClient client;
    List<ScannerFixture> scanners = new ArrayList<>();
    List<Scan> scans = new ArrayList<>();
    boolean failNext;
  }

  private static ClientFixture fixture() throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    ClientFixture f = new ClientFixture();
    f.client = (UnconnectedClient)((Unsafe)field.get(null)).allocateInstance(UnconnectedClient.class);
    f.client.setLimit(1);
    f.client.setSearchOperator("AND");
    f.client.fake = (Table) Proxy.newProxyInstance(Table.class.getClassLoader(),
      new Class<?>[]{Table.class}, (proxy, method, args) -> {
        if (method.getName().equals("getScanner")) {
          f.scans.add((Scan)args[0]);
          ScannerFixture scanner = new ScannerFixture(f.failNext);
          f.scanners.add(scanner);
          return scanner.scanner;
        }
        return null;
      });
    return f;
  }

  private static void scan(ClientFixture f) {
    f.client.scan(null, (SearchMap)null, "*", 0, 0, false, false);
  }

  private static void closeClosesLastScanner() throws Exception {
    ClientFixture f = fixture();
    scan(f);
    require(!f.scanners.get(0).closed.get(), "resultScanner must remain usable after scan");
    f.client.close();
    require(f.scanners.get(0).closed.get(), "closing client must close last borrowed scanner");
  }

  private static void nextScanClosesPreviousScanner() throws Exception {
    ClientFixture f = fixture();
    scan(f);
    scan(f);
    require(f.scanners.get(0).closed.get(), "replacing a scanner must close its predecessor");
    require(!f.scanners.get(1).closed.get(), "latest scanner must remain available to caller");
    f.client.close();
  }

  private static void failedScanClosesScanner() throws Exception {
    ClientFixture f = fixture();
    f.failNext = true;
    boolean failed = false;
    try { scan(f); }
    catch (IllegalStateException expected) { failed = true; }
    require(failed, "synthetic scanner failure was swallowed");
    require(f.scanners.get(0).closed.get(), "scanner leaked when iteration failed");
  }

  private static void resultsOnlyScanClosesScannerImmediately() throws Exception {
    ClientFixture f = fixture();
    f.client.scanAndClose(null, (SearchMap)null, "*", 0, 0, false, false);
    require(f.scanners.get(0).closed.get(), "results-only scan retained a scanner");
    require(f.client.resultScanner() == null, "results-only scan returned a closed scanner");
  }

  private static void rowLimitDoesNotSetTinyByteLimit() throws Exception {
    ClientFixture f = fixture();
    f.client.setSchema(new HBaseSchema("test", java.util.Map.of("r:v", "string")));
    scan(f);
    require(f.scans.get(0).getMaxResultSize() == new Scan().getMaxResultSize(),
            "row count times schema columns must not be treated as a byte limit");
    f.client.close();
  }

  private static void multiversionResultsOnlyClosesScanner() throws Exception {
    ClientFixture f = fixture();
    f.client.scan3DAndClose(null, (SearchMap)null, "*", 0, 0, false, false);
    require(f.scanners.get(0).closed.get(), "multiversion results-only scan retained a scanner");
    require(f.client.resultScanner() == null, "multiversion scanner reference not cleared");
  }

  public static void main(String[] args) throws Exception {
    closeClosesLastScanner();
    nextScanClosesPreviousScanner();
    failedScanClosesScanner();
    resultsOnlyScanClosesScannerImmediately();
    rowLimitDoesNotSetTinyByteLimit();
    multiversionResultsOnlyClosesScanner();
    System.out.println("HBaserNew scanner lifecycle checks passed");
  }
}
