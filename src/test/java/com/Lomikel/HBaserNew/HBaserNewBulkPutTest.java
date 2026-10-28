package com.Lomikel.HBaserNew;

import com.Lomikel.Utils.LomikelException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;
import sun.misc.Unsafe;

/** Offline bounded HBase batch-write checks; no cluster contacted. */
public class HBaserNewBulkPutTest {
  private static void require(boolean yes, String message) {
    if (!yes) throw new AssertionError(message);
  }
  public static class UnconnectedClient extends HBaseClient {
    Table fake;
    public UnconnectedClient() throws LomikelException { super("unused", "2181"); }
    @Override public Table table() { return fake; }
  }
  private static Map<String, Map<String, String>> rows(int count) {
    Map<String, Map<String, String>> rows = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) rows.put("row-" + i, Map.of("r:v", "x"));
    return rows;
  }
  private static void boundedWritesPreserveAllRows() throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    UnconnectedClient client = (UnconnectedClient)((Unsafe)field.get(null))
        .allocateInstance(UnconnectedClient.class);
    List<Integer> sizes = new ArrayList<>();
    List<String> keys = new ArrayList<>();
    client.fake = (Table) Proxy.newProxyInstance(Table.class.getClassLoader(),
        new Class<?>[]{Table.class}, (proxy, method, args) -> {
          if (method.getName().equals("put") && args[0] instanceof List) {
            @SuppressWarnings("unchecked") List<Put> batch = (List<Put>) args[0];
            sizes.add(batch.size());
            for (Put put : batch) keys.add(Bytes.toString(put.getRow()));
          }
          return null;
        });
    Map<String, Map<String, String>> all = rows(2001);
    client.put(all);
    require(sizes.size() > 1 && sizes.stream().allMatch(size -> size > 0 && size <= 1000),
            "bulk puts must bound each submitted batch to at most 1000 rows");
    require(keys.equals(new ArrayList<>(all.keySet())), "batching lost or reordered rows");
  }

  private static void failedBatchStopsFurtherWrites() throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    UnconnectedClient client = (UnconnectedClient)((Unsafe)field.get(null))
        .allocateInstance(UnconnectedClient.class);
    int[] calls = {0};
    client.fake = (Table) Proxy.newProxyInstance(Table.class.getClassLoader(),
        new Class<?>[]{Table.class}, (proxy, method, args) -> {
          if (method.getName().equals("put") && args[0] instanceof List) {
            calls[0]++;
            if (calls[0] == 2) throw new IOException("synthetic second-batch failure");
          }
          return null;
        });
    boolean failed = false;
    try { client.put(rows(3001)); }
    catch (IOException expected) { failed = true; }
    require(failed && calls[0] == 2,
            "failed batch must propagate IOException without submitting later chunks");
  }

  private static void singlePutKeepsColonsInValues() throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    UnconnectedClient client = (UnconnectedClient)((Unsafe)field.get(null))
        .allocateInstance(UnconnectedClient.class);
    Put[] captured = {null};
    client.fake = (Table) Proxy.newProxyInstance(Table.class.getClassLoader(),
        new Class<?>[]{Table.class}, (proxy, method, args) -> {
          if (method.getName().equals("put") && args[0] instanceof Put) captured[0] = (Put)args[0];
          return null;
        });
    client.put("row", new String[]{"r:v:hello:world"});
    require(captured[0] != null, "single put was not submitted");
    byte[] value = org.apache.hadoop.hbase.CellUtil.cloneValue(
        captured[0].get(Bytes.toBytes("r"), Bytes.toBytes("v")).get(0));
    require("hello:world".equals(Bytes.toString(value)),
            "colon-separated put must not truncate a value containing colons");
  }

  public static void main(String[] args) throws Exception {
    boundedWritesPreserveAllRows();
    failedBatchStopsFurtherWrites();
    singlePutKeepsColonsInValues();
    System.out.println("HBaserNew bounded bulk put checks passed");
  }
}
