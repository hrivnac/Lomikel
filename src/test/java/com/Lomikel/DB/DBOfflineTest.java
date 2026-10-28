package com.Lomikel.DB;

import com.Lomikel.Utils.LomikelException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Offline contract checks for the shared DB abstractions. */
public final class DBOfflineTest {

  private static final class FixtureSchema extends Schema<String> {
    FixtureSchema(Map<String, String> columns) { super("fixture", columns); }
    @Override public String decode(String column, String encoded) { return encoded; }
    @Override public CellContent decode2Content(String column, String encoded) { return new CellContent(encoded); }
    @Override public String encode(String column, String decoded) { return decoded; }
  }

  private static final class FixtureEvaluator extends GroovyEvaluator {
    FixtureEvaluator(Schema<?> schema) throws LomikelException { super(schema); }
    @Override protected String varName(String fullName) { return fullName.substring(2); }
  }

  private static final class FixtureClient extends Client<Object, FixtureSchema> {
    SearchMap lastSearch;
    @Override public Object connect(String tableName) throws LomikelException { return null; }
    @Override public Object connect(String tableName, String schemaName) throws LomikelException { return null; }
    @Override public Object connect(String tableName, String schemaName, int timeout) throws LomikelException { return null; }
    @Override public Object connect(String tableName, String schemaName, int timeout, int retries) throws LomikelException { return null; }
    @Override public void close() { }
    @Override public void delete(String key) { }
    @Override public Map<String, Map<String, String>> scan(String key, SearchMap search,
        String filter, long start, long stop, boolean ifkey, boolean iftime) {
      lastSearch = search;
      return new LinkedHashMap<>();
    }
    @Override public Map<String, Map<String, Map<Long, String>>> scan3D(String key,
        SearchMap search, String filter, long start, long stop, boolean ifkey, boolean iftime) {
      lastSearch = search;
      return new LinkedHashMap<>();
    }
  }

  private static void check(boolean condition, String message) {
    if (!condition) { throw new AssertionError(message); }
  }

  private static void invalid(Runnable call, String description) {
    try { call.run(); }
    catch (IllegalArgumentException expected) { return; }
    throw new AssertionError("Expected IllegalArgumentException for " + description);
  }

  public static void main(String[] args) throws Exception {
    Map<String, String> columns = new LinkedHashMap<>();
    columns.put("f:n", "double");
    FixtureSchema schema = new FixtureSchema(columns);
    check(Double.valueOf(1.25).equals(schema.castColumn("f:n", "1.25")), "numeric cast");
    check("plain".equals(schema.castColumn("f:unknown", "plain")), "unknown type is string");
    check(schema.castColumn("f:n", null) == null, "missing numeric value remains missing");

    FixtureClient client = new FixtureClient();
    client.scan(null, "f:n:1:exact,f:n:2:exact", "*", 0, 0, false, false);
    check("1,2".equals(client.lastSearch.map().get("f:n:exact")), "repeat exact terms");
    client.scan3D(null, "key:key:abc:prefix", "*", 0, 0, false, false);
    check("abc".equals(client.lastSearch.map().get("key:key:prefix")), "3D comparator parity");
    invalid(() -> client.scan(null, "f:n", "*", 0, 0, false, false), "short search term");
    invalid(() -> client.scan3D(null, "f:n", "*", 0, 0, false, false), "short 3D search term");
    invalid(() -> client.scan(null, "f:n:1,", "*", 0, 0, false, false), "trailing search term");
    invalid(() -> client.scan3D(null, "f:n:1,", "*", 0, 0, false, false), "trailing 3D search term");

    Map<String, String> evaluationColumns = new LinkedHashMap<>();
    evaluationColumns.put("r:ra", "double");
    evaluationColumns.put("r:reading", "double");
    evaluationColumns.put("r:radius", "double");
    FixtureEvaluator evaluator = new FixtureEvaluator(new FixtureSchema(evaluationColumns));
    evaluator.setVariables("reading > 3");
    check(evaluator.evalBoolean(Map.of("r:reading", "5"), "reading > 3"), "first row matches");
    try {
      evaluator.evalBoolean(Map.of(), "reading > 3");
      throw new AssertionError("a missing second-row column must not reuse the first-row value");
    }
    catch (LomikelException expected) {
      // Callers treat an unevaluable row as non-matching.
    }
    check(!evaluator.evalBoolean(Map.of("r:reading", "1"), "reading > 3"),
          "subsequent rows retain their own values");
    check(evaluator.evalBoolean(Map.of(), "true"),
          "an unreferenced registered variable does not reject a formula");
    System.out.println("DBOfflineTest PASS");
  }
}
