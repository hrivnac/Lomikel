package com.Lomikel.DB;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import org.apache.hadoop.hbase.KeyValue;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import sun.misc.Unsafe;

/** Exercises the real row filter in both HBase callers, without connecting. */
public final class DBHBaseCallerTest {
  private static <T> T unconnected(Class<T> type) throws Exception {
    Field field = Unsafe.class.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
  }

  private static void setField(Object target, String name, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static boolean evaluate(Object client, Result row) throws Exception {
    Method method = client.getClass().getDeclaredMethod("evaluateResult", Result.class);
    method.setAccessible(true);
    return (boolean) method.invoke(client, row);
  }

  private static Result row(String column, String value) {
    return Result.create(Arrays.asList(new KeyValue(Bytes.toBytes("row"), Bytes.toBytes("r"),
        Bytes.toBytes(column), Bytes.toBytes(Double.parseDouble(value)))));
  }

  private static void checkRows(Object client) throws Exception {
    if (!evaluate(client, row("reading", "5"))) {
      throw new AssertionError(client.getClass() + " failed to match first row");
    }
    if (evaluate(client, row("other", "8"))) {
      throw new AssertionError(client.getClass() + " reused a previous row's reading");
    }
    if (evaluate(client, row("reading", "1"))) {
      throw new AssertionError(client.getClass() + " failed subsequent-row evaluation");
    }
  }

  public static void main(String[] args) throws Exception {
    Map<String, String> columns = Map.of("r:reading", "double", "r:other", "double");
    com.Lomikel.HBaser.HBaseClient oldClient = unconnected(com.Lomikel.HBaser.HBaseClient.class);
    com.Lomikel.HBaser.HBaseSchema oldSchema = new com.Lomikel.HBaser.HBaseSchema("test", columns);
    com.Lomikel.HBaser.HBaseEvaluator oldEvaluator = new com.Lomikel.HBaser.HBaseEvaluator(oldSchema);
    oldEvaluator.setVariables("reading > 3");
    oldClient.setSchema(oldSchema);
    setField(oldClient, "_evaluator", oldEvaluator);
    setField(oldClient, "_formula", "reading > 3");
    checkRows(oldClient);

    com.Lomikel.HBaserNew.HBaseClient newClient = unconnected(com.Lomikel.HBaserNew.HBaseClient.class);
    com.Lomikel.HBaserNew.HBaseSchema newSchema = new com.Lomikel.HBaserNew.HBaseSchema("test", columns);
    com.Lomikel.HBaserNew.HBaseEvaluator newEvaluator = new com.Lomikel.HBaserNew.HBaseEvaluator(newSchema);
    newEvaluator.setVariables("reading > 3");
    newClient.setSchema(newSchema);
    setField(newClient, "_evaluator", newEvaluator);
    setField(newClient, "_formula", "reading > 3");
    checkRows(newClient);
    System.out.println("DBHBaseCallerTest PASS");
  }
}
