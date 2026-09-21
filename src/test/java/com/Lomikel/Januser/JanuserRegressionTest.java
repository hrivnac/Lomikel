package com.Lomikel.Januser;

import com.Lomikel.DB.Client;
import com.Lomikel.HBaser.HBaseClient;
import com.Lomikel.Utils.LomikelException;
import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies;
import com.astrolabsoftware.FinkBrowser.Januser.OCol;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

/** Focused regression tests for Januser correctness bugs. */
public final class JanuserRegressionTest {

  private JanuserRegressionTest() {}

  public static void main(String[] args) throws Exception {
    testGetOrCreateCreatesMissingVertexAndReusesExistingVertex();
    testDeepDropHandlesCyclesAndNonJanusVertices();
    testRecipeCommitUsesClientAbstraction();
    testOColEqualityDoesNotCollapseHashCollisions();
    testFinkRegistrationPreservesNumericWeights();
    testFinkRegistrationRejectsInvalidWeightsBeforeMutation();
    testFailedStandaloneRegistrationRollsBack();
    testClassificationRejectsNonTransactionalClient();
    testFailedClassificationRollsBackReplacement();
    testClassificationCleanupPreservesUnrelatedEdges();
    testCleanOColPreservesUnrelatedBranches();
    testCorrelationRegenerationIsScoped();
    testTimerCommitsIndependentlyOfReportingInterval();
    testReopenPreservesPropertiesConfiguration();
    testMissingPropertiesFileFailsExplicitly();
    testRemoteClientConstructionPropagatesOpenFailure();
    testHertexGetOrCreateReturnsEnhancedVertices();
    testHertexEnhanceWithoutLabelReturnsOriginalVertex();
    testHBaseCloseAttemptsConnectionAfterTableFailure();
    testHertexMissingRowDoesNotFailSelectiveEnhancement();
    testHBaseEmptyResultIsIgnored();
    testWertexPreservesVertexIdentity();
    testEdgePropertiesAreValidatedBeforeMutation();
    System.out.println("JanuserRegressionTest: OK");
    }

  private static void testGetOrCreateCreatesMissingVertexAndReusesExistingVertex() {
    FakeClient client = new FakeClient();
    try {
      GremlinRecipies recipes = new GremlinRecipies(client);
      Vertex created;
      try {
        created = recipes.getOrCreate("object", new String[] {"objectId"}, new Object[] {"A"}).next();
        }
      catch (NoSuchElementException e) {
        throw new AssertionError("a missing vertex must be created", e);
        }
      require(recipes.created(), "created() must report a newly created vertex");
      require("A".equals(created.value("objectId")), "the created vertex must keep lookup properties");
      require(client.g().V().has("lbl", "object").has("objectId", "A").count().next() == 1L,
              "exactly one vertex must exist after creation");

      Vertex found = recipes.getOrCreate("object", new String[] {"objectId"}, new Object[] {"A"}).next();
      require(!recipes.created(), "created() must report reuse of an existing vertex");
      require(created.id().equals(found.id()), "the existing vertex must be returned");
      require(client.g().V().has("lbl", "object").has("objectId", "A").count().next() == 1L,
              "repeated lookup must not create a duplicate");
      }
    finally {
      client.close();
      }
    }

  private static void testDeepDropHandlesCyclesAndNonJanusVertices() throws Exception {
    FakeClient client = new FakeClient();
    Thread worker = null;
    try {
      Vertex root = client.g().addV("node").property("lbl", "node").property("id", "root").next();
      Vertex[] previous = new Vertex[] {root};
      for (int layer = 0; layer < 22; layer++) {
        Vertex left = client.g().addV("node").property("lbl", "node").property("id", "left-" + layer).next();
        Vertex right = client.g().addV("node").property("lbl", "node").property("id", "right-" + layer).next();
        for (Vertex parent : previous) {
          parent.addEdge("contains", left);
          parent.addEdge("contains", right);
          }
        previous = new Vertex[] {left, right};
        }
      for (Vertex leaf : previous) {
        leaf.addEdge("contains", root);
        }
      Vertex unrelated = client.g().addV("node").property("lbl", "node").property("id", "other").next();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      worker = new Thread(() -> {
        try {
          new GremlinRecipies(client).drop("node", "id", "root", true);
          }
        catch (Throwable e) {
          failure.set(e);
          }
        }, "deep-drop-regression");
      worker.setDaemon(true);
      worker.start();
      worker.join(2000L);

      require(!worker.isAlive(), "deep drop must terminate without enumerating every simple path");
      if (failure.get() != null) {
        throw new AssertionError("deep drop must support non-JanusGraph vertices", failure.get());
        }
      require(client.g().V().has("id", "root").hasNext() == false, "deep drop must remove the selected root");
      require(client.g().V().has("id", "left-21").hasNext() == false, "deep drop must remove reachable children");
      require(client.g().V(unrelated.id()).hasNext(), "deep drop must not remove unrelated vertices");
      }
    finally {
      if (worker == null || !worker.isAlive()) {
        client.close();
        }
      }
    }

  private static void testOColEqualityDoesNotCollapseHashCollisions() {
    TinkerGraph graph = TinkerGraph.open();
    Vertex firstVertex = graph.addVertex("survey", "ZTF", "classifier", "FINK", "flavor", "", "cls", "Aa");
    Vertex secondVertex = graph.addVertex("survey", "ZTF", "classifier", "FINK", "flavor", "", "cls", "BB");
    OCol first  = new OCol(firstVertex);
    OCol second = new OCol(secondVertex);
    require(first.hashCode() == second.hashCode(), "fixture must exercise a real Java hash collision");
    require(!first.equals(second), "distinct OCols must remain unequal despite a hash collision");
    require(first.compareTo(second) != 0, "ordering must not collapse distinct OCols");
    graph.close();
    }

  private static void testRecipeCommitUsesClientAbstraction() {
    FakeClient client = new FakeClient();
    try {
      new GremlinRecipies(client).commit();
      require(client.commits() == 1, "recipe commits must be delegated to the client");
      }
    finally {
      client.close();
      }
    }

  private static void testTimerCommitsIndependentlyOfReportingInterval() throws Exception {
    Path properties = Files.createTempFile("januser-test-", ".properties");
    Files.writeString(properties, "storage.backend=inmemory\n");
    try {
      JanusClient client = new JanusClient(properties.toString());
      try {
        boolean committed = client.timer("test", 10, 100, 10);
        require(committed, "commit interval must be independent of reporting interval");
        }
      finally {
        client.close();
        }
      }
    finally {
      Files.deleteIfExists(properties);
      }
    }

  private static void testReopenPreservesPropertiesConfiguration() throws Exception {
    Path firstProperties = Files.createTempFile("januser-reopen-first-", ".properties");
    Path secondProperties = Files.createTempFile("januser-reopen-second-", ".properties");
    Files.writeString(firstProperties, "storage.backend=inmemory\n");
    Files.writeString(secondProperties, "storage.backend=inmemory\n");
    JanusClient client = null;
    try {
      client = new JanusClient(firstProperties.toString());
      client.close();
      client.open(secondProperties.toString());
      Files.delete(firstProperties);

      client.reopen();

      require(client.g().V().count().next() == 0L,
              "reopen must reuse the most recently opened file-based configuration");
      }
    finally {
      if (client != null) {
        client.close();
        }
      Files.deleteIfExists(firstProperties);
      Files.deleteIfExists(secondProperties);
      }
    }

  private static void testCorrelationRegenerationIsScoped() throws Exception {
    Path properties = Files.createTempFile("januser-correlation-test-", ".properties");
    Files.writeString(properties, "storage.backend=inmemory\n");
    JanusClient client = null;
    try {
      client = new JanusClient(properties.toString());
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      TestClassifier selected = new TestClassifier();
      OtherClassifier unrelated = new OtherClassifier();
      recipes.registerOCol(selected, "selected", "ZTF-shared", 1.0, "[1]", "[1.0]");
      recipes.registerOCol(unrelated, "unrelated", "ZTF-shared", 1.0, "[1]", "[1.0]");

      Vertex selectedOCol = client.g().V().has("lbl", "OCol").has("cls", "selected").next();
      Vertex object = client.g().V().has("lbl", "object").has("objectId", "ZTF-shared").next();
      Vertex externalA = client.g().addV("OCol").property("lbl", "OCol").
                                property("survey", "LSST").property("classifier", "OTHER").
                                property("flavor", "").property("cls", "external-a").next();
      Vertex externalB = client.g().addV("OCol").property("lbl", "OCol").
                                property("survey", "LSST").property("classifier", "OTHER").
                                property("flavor", "").property("cls", "external-b").next();
      externalA.addEdge("overlaps", externalB, "lbl", "overlaps", "marker", "keep");
      selectedOCol.addEdge("overlaps", externalA, "lbl", "overlaps", "marker", "stale");
      Vertex malformedUnrelated = client.g().addV("OCol").property("lbl", "OCol").
                                          property("classifier", "BROKEN").property("flavor", "").
                                          property("cls", "malformed-unrelated").next();
      malformedUnrelated.addEdge("deepcontains", object, "lbl", "deepcontains", "weight", 1.0);

      recipes.generateCorrelations(selected);

      require(client.g().E().has("marker", "keep").hasNext(),
              "regeneration must preserve overlaps outside the requested classifier scope");
      require(!client.g().E().has("marker", "stale").hasNext(),
              "regeneration must remove stale overlaps adjacent to requested OCols");
      require(!client.g().V().has("lbl", "OCol").has("cls", "unrelated").bothE("overlaps").hasNext(),
              "regeneration must not include classifications outside the requested scope");
      require(client.g().V(selectedOCol).outE("overlaps").where(inV().is(selectedOCol)).
                       has("intersection", 1.0).has("sizeIn", 1.0).has("sizeOut", 1.0).
                       count().next() == 1L,
              "regeneration must create the expected weighted scoped correlation");
      }
    finally {
      if (client != null) {
        client.close();
        }
      Files.deleteIfExists(properties);
      }
    }

  private static void testClassificationRejectsNonTransactionalClient() throws Exception {
    FakeClient client = new FakeClient();
    try {
      boolean rejected = false;
      try {
        new FinkGremlinRecipies(client).classifySource(new TestClassifier(), "ZTF-remote");
        }
      catch (UnsupportedOperationException expected) {
        rejected = true;
        }
      require(rejected, "atomic classification must reject clients without transaction rollback");
      require(client.g().V().count().next() == 0L,
              "transaction capability must be checked before graph mutation");
      }
    finally {
      client.close();
      }
    }

  private static void testFailedClassificationRollsBackReplacement() throws Exception {
    Path properties = Files.createTempFile("januser-classification-test-", ".properties");
    Files.writeString(properties, "storage.backend=inmemory\n");
    JanusClient client = null;
    try {
      client = new JanusClient(properties.toString());
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      FailingClassifier classifier = new FailingClassifier();
      recipes.registerOCol(classifier, "old", "ZTF-atomic", 1.0, "[1]", "[1.0]");

      try {
        recipes.classifySource(classifier, "ZTF-atomic");
        throw new AssertionError("classification fixture must fail");
        }
      catch (LomikelException expected) {
        // Expected: verify the original committed classification below.
        }

      require(client.g().V().has("lbl", "OCol").has("cls", "old").out("deepcontains").
                       has("objectId", "ZTF-atomic").hasNext(),
              "failed replacement must preserve the prior classification");
      require(!client.g().V().has("lbl", "OCol").has("cls", "new").out("deepcontains").
                        has("objectId", "ZTF-atomic").hasNext(),
              "failed replacement must not commit a partial new classification");
      }
    finally {
      if (client != null) {
        client.close();
        }
      Files.deleteIfExists(properties);
      }
    }

  private static void testFailedStandaloneRegistrationRollsBack() throws Exception {
    Path properties = Files.createTempFile("januser-registration-test-", ".properties");
    Files.writeString(properties, "storage.backend=inmemory\n");
    JanusClient client = null;
    try {
      client = new JanusClient(properties.toString());
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      TestClassifier classifier = new TestClassifier();
      java.util.Map<String, Object> invalid = new java.util.LinkedHashMap<>();
      invalid.put("weight", 1.0);
      invalid.put(null, "invalid-key");
      try {
        recipes.registerOCol(classifier, "failed", "ZTF-failed", invalid, true);
        throw new AssertionError("invalid property key must fail registration");
        }
      catch (RuntimeException expected) {
        // A later successful registration must not commit this failed transaction.
        }

      recipes.registerOCol(classifier, "successful", "ZTF-successful",
                           1.0, "[1]", "[1.0]");
      require(!client.g().V().has("lbl", "OCol").has("cls", "failed").hasNext() &&
              !client.g().V().has("lbl", "object").has("objectId", "ZTF-failed").hasNext(),
              "failed standalone registration must be rolled back before a later commit");
      }
    finally {
      if (client != null) {
        client.close();
        }
      Files.deleteIfExists(properties);
      }
    }

  private static void testClassificationCleanupPreservesUnrelatedEdges() throws Exception {
    Path properties = Files.createTempFile("januser-classification-edge-test-", ".properties");
    Files.writeString(properties, "storage.backend=inmemory\n");
    JanusClient client = null;
    try {
      client = new JanusClient(properties.toString());
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      TestClassifier classifier = new TestClassifier();
      recipes.registerOCol(classifier, "old", "ZTF-edge-scope",
                           1.0, "[1]", "[1.0]");
      Vertex ocol = client.g().V().has("lbl", "OCol").has("cls", "old").next();
      Vertex object = client.g().V().has("lbl", "object").
                             has("objectId", "ZTF-edge-scope").next();
      ocol.addEdge("audit", object, "lbl", "deepcontains", "marker", "preserve");
      recipes.commit();

      recipes.classifySource(classifier, "ZTF-edge-scope");

      require(client.g().E().hasLabel("audit").has("marker", "preserve").hasNext(),
              "classification cleanup must preserve unrelated structural edges");
      require(!client.g().E().hasLabel("deepcontains").hasNext(),
              "classification cleanup must remove prior deepcontains memberships");
      }
    finally {
      if (client != null) {
        client.close();
        }
      Files.deleteIfExists(properties);
      }
    }

  private static void testCleanOColPreservesUnrelatedBranches() throws Exception {
    FakeClient client = new FakeClient();
    try {
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      TestClassifier classifier = new TestClassifier();
      Vertex ocol = client.g().addV("OCol").property("lbl", "OCol").
                          property("survey", "ZTF").property("classifier", "TAG").
                          property("flavor", "").property("cls", "cleanup").next();
      Vertex object = client.g().addV("object").property("lbl", "object").next();
      Vertex alert = client.g().addV("alert").property("lbl", "alert").next();
      Vertex unrelated = client.g().addV("unrelated").property("lbl", "unrelated").next();
      Vertex victim = client.g().addV("victim").property("lbl", "victim").next();
      Object alertId = alert.id();
      Object victimId = victim.id();
      ocol.addEdge("deepcontains", object);
      object.addEdge("contains", alert);
      ocol.addEdge("audit", unrelated, "lbl", "deepcontains");
      unrelated.addEdge("links", victim);

      recipes.cleanOCol(classifier, "cleanup");

      require(!client.g().V(alertId).hasNext(),
              "OCol cleanup must drop descendants of real memberships");
      require(client.g().V(victimId).hasNext(),
              "OCol cleanup must preserve descendants of unrelated edges");
      }
    finally {
      client.close();
      }
    }

  private static void testMissingPropertiesFileFailsExplicitly() throws Exception {
    Path missing = Files.createTempFile("januser-missing-", ".properties");
    Files.delete(missing);
    try {
      new JanusClient(missing.toString());
      throw new AssertionError("a missing properties file must fail construction");
      }
    catch (IllegalArgumentException expected) {
      require(expected.getCause() instanceof java.io.IOException,
              "configuration load failure must retain its IOException cause");
      }
    }

  private static void testRemoteClientConstructionPropagatesOpenFailure() {
    try {
      new DirectGremlinClient("localhost", -1);
      throw new AssertionError("invalid remote connection parameters must fail construction");
      }
    catch (IllegalStateException expected) {
      require(expected.getCause() != null, "remote connection failure must retain its cause");
      }

    AtomicBoolean closed = new AtomicBoolean();
    try {
      new FailingInitializationClient(closed);
      throw new AssertionError("connect failure must fail construction");
      }
    catch (IllegalStateException expected) {
      require(closed.get(), "failed construction must close partially initialized resources");
      require(expected.getSuppressed().length == 1,
              "cleanup failure must be suppressed on the original connect failure");
      }
    }

  private static void testHertexGetOrCreateReturnsEnhancedVertices() throws Exception {
    HBaseClient hbase = allocateWithoutConstructor(HBaseClient.class);
    GraphTraversalSource source = TinkerGraph.open().traversal();
    try {
      Client.registerVertexType("enhanced", TestWertex.class);
      Wertex.setRowkeyName("enhanced", TestWertex.class, "rowkey");
      Hertex.setHBaseClient(hbase);

      java.util.List<Vertex> vertices = Hertex.getOrCreate("enhanced", "row-1", source, "");

      require(vertices.size() == 1, "Hertex getOrCreate must return one matching vertex");
      require(vertices.get(0) instanceof TestWertex,
              "Hertex getOrCreate must return the enhanced representation");
      }
    finally {
      Hertex.setHBaseClient(null);
      source.close();
      }
    }

  private static void testHertexEnhanceWithoutLabelReturnsOriginalVertex() throws Exception {
    HBaseClient hbase = allocateWithoutConstructor(HBaseClient.class);
    TinkerGraph graph = TinkerGraph.open();
    Vertex original = graph.addVertex("rowkey", "row-1");
    try {
      Hertex.setHBaseClient(hbase);
      require(Hertex.enhance(original, null) == original,
              "enhancing a vertex without lbl must safely return the original vertex");
      }
    finally {
      Hertex.setHBaseClient(null);
      graph.close();
      }
    }

  private static void testHBaseCloseAttemptsConnectionAfterTableFailure() throws Exception {
    AtomicBoolean connectionClosed = new AtomicBoolean();
    HBaseClient client = allocateWithoutConstructor(HBaseClient.class);
    org.apache.hadoop.hbase.client.Table table =
      (org.apache.hadoop.hbase.client.Table)java.lang.reflect.Proxy.newProxyInstance(
        JanuserRegressionTest.class.getClassLoader(),
        new Class<?>[] {org.apache.hadoop.hbase.client.Table.class},
        (proxy, method, args) -> {
          if (method.getName().equals("close")) {
            throw new java.io.IOException("intentional table close failure");
            }
          return primitiveDefault(method.getReturnType());
          });
    org.apache.hadoop.hbase.client.Connection connection =
      (org.apache.hadoop.hbase.client.Connection)java.lang.reflect.Proxy.newProxyInstance(
        JanuserRegressionTest.class.getClassLoader(),
        new Class<?>[] {org.apache.hadoop.hbase.client.Connection.class},
        (proxy, method, args) -> {
          if (method.getName().equals("close")) {
            connectionClosed.set(true);
            }
          return primitiveDefault(method.getReturnType());
          });
    setField(client, "_table", table);
    setField(client, "_connection", connection);

    client.close();

    require(connectionClosed.get(),
            "HBase close must attempt connection cleanup after table cleanup fails");
    }

  private static void testHertexMissingRowDoesNotFailSelectiveEnhancement() throws Exception {
    EmptyHBaseClient hbase = allocateWithoutConstructor(EmptyHBaseClient.class);
    TinkerGraph graph = TinkerGraph.open();
    Vertex vertex = graph.addVertex("lbl", "missing-row", "rowkey", "row-1");
    try {
      Wertex.setRowkeyName("missing-row", Hertex.class, "rowkey");
      Hertex.setHBaseClient(hbase);

      Vertex enhanced = new Hertex(vertex, "");

      require(enhanced.id().equals(vertex.id()),
              "missing HBase rows must leave the graph vertex usable");
      require(!vertex.property("hbase").isPresent(),
              "missing HBase rows must not mark the vertex as HBase-backed");

      hbase.resultMode = "missing-key";
      new Hertex(vertex, "");
      require(!vertex.property("hbase").isPresent(),
              "a response without the requested row must not mark the vertex as HBase-backed");

      hbase.resultMode = "empty-row";
      new Hertex(vertex, "");
      require(!vertex.property("hbase").isPresent(),
              "an empty requested row must not mark the vertex as HBase-backed");
      }
    finally {
      Hertex.setHBaseClient(null);
      graph.close();
      }
    }

  private static void testHBaseEmptyResultIsIgnored() throws Exception {
    EmptyHBaseClient hbase = allocateWithoutConstructor(EmptyHBaseClient.class);
    require(!hbase.addEmptyResult(),
            "an HBase Result without a row key must be ignored safely");
    }

  private static void testWertexPreservesVertexIdentity() {
    FakeClient client = new FakeClient();
    try {
      Wertex.setRowkeyName("wrapped", TestWertex.class, "rowkey");
      Vertex source = client.g().addV("wrapped").property("lbl", "wrapped").
                            property("rowkey", "source").next();
      Vertex target = client.g().addV("wrapped").property("lbl", "wrapped").
                            property("rowkey", "target").next();
      Vertex wrappedSource = new TestWertex(source, "");
      Vertex wrappedTarget1 = new TestWertex(target, "");
      Vertex wrappedTarget2 = new TestWertex(target, "");

      require(wrappedTarget1.equals(target), "a Wertex must equal its underlying vertex");
      require(target.equals(wrappedTarget1), "vertex identity equality must remain symmetric");
      require(wrappedTarget1.equals(wrappedTarget2),
              "wrappers around the same vertex must compare equal");
      require(wrappedTarget1.hashCode() == target.hashCode(),
              "a Wertex must preserve the underlying vertex hash code");

      GremlinRecipies recipes = new GremlinRecipies(client);
      recipes.addEdge(wrappedSource, wrappedTarget2, "links");
      recipes.addEdge(source, target, "links");
      require(client.g().E().hasLabel("links").count().next() == 1L,
              "dressed endpoints must not create duplicate edges");
      }
    finally {
      client.close();
      }
    }

  private static void testEdgePropertiesAreValidatedBeforeMutation() {
    FakeClient client = new FakeClient();
    try {
      Vertex source = client.g().addV("node").next();
      Vertex target = client.g().addV("node").next();
      GremlinRecipies recipes = new GremlinRecipies(client);
      String[] names = new String[] {"first", "second"};

      expectIllegalArgument(
        () -> recipes.addEdge(source, target, "double-edge", names,
                              new Double[] {1.0}, false),
        "mismatched Double edge properties must be rejected");
      expectIllegalArgument(
        () -> recipes.addEdge(source, target, "string-edge", names,
                              new String[] {"one"}, false),
        "mismatched String edge properties must be rejected");
      expectIllegalArgument(
        () -> recipes.addEdge(source, target, "object-edge", names,
                              new Object[] {1}, false),
        "mismatched Object edge properties must be rejected");
      require(client.g().E().count().next() == 0L,
              "invalid edge properties must not create partial edges");

      recipes.addEdge(source, target, "existing", new String[] {"first", "second"},
                      new Object[] {"old-first", "old-second"}, false);
      expectIllegalArgument(
        () -> recipes.addEdge(source, target, "existing", names,
                              new Object[] {"new-first"}, true),
        "mismatched reset properties must be rejected");
      org.apache.tinkerpop.gremlin.structure.Edge edge =
        client.g().E().hasLabel("existing").next();
      require("old-first".equals(edge.value("first")) &&
              "old-second".equals(edge.value("second")),
              "invalid reset properties must not partially update an edge");
      }
    finally {
      client.close();
      }
    }

  private static void expectIllegalArgument(Runnable action, String message) {
    try {
      action.run();
      throw new AssertionError(message);
      }
    catch (IllegalArgumentException expected) {
      // Expected validation failure.
      }
    }

  private static Object primitiveDefault(Class<?> type) {
    if (!type.isPrimitive() || type == void.class) return null;
    if (type == boolean.class) return false;
    if (type == char.class) return '\0';
    if (type == byte.class) return (byte)0;
    if (type == short.class) return (short)0;
    if (type == int.class) return 0;
    if (type == long.class) return 0L;
    if (type == float.class) return 0.0f;
    return 0.0d;
    }

  private static void setField(Object target, String name, Object value) throws Exception {
    java.lang.reflect.Field field = HBaseClient.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
    }

  @SuppressWarnings("unchecked")
  private static <T> T allocateWithoutConstructor(Class<T> type) throws Exception {
    Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
    java.lang.reflect.Field field = unsafeClass.getDeclaredField("theUnsafe");
    field.setAccessible(true);
    Object unsafe = field.get(null);
    return (T)unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
    }

  private static void testFinkRegistrationPreservesNumericWeights() {
    FakeClient client = new FakeClient();
    try {
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      recipes.registerOCol(new TestClassifier(), "candidate", "ZTF1", 1.0,
                           "[1, 2]", "[0.25, 0.75]");
      Object weight = client.g().E().hasLabel("deepcontains").values("weight").next();
      Object weights = client.g().E().hasLabel("deepcontains").values("weights").next();
      require(weight instanceof Double, "aggregate weight must be stored as a number");
      require("0.25, 0.75".equals(weights), "per-instance weights must preserve parsed values");
      }
    finally {
      client.close();
      }
    }

  private static void testFinkRegistrationRejectsInvalidWeightsBeforeMutation() {
    FakeClient client = new FakeClient();
    try {
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      TestClassifier classifier = new TestClassifier();
      double[] invalidWeights = new double[] {-1.0, Double.NaN,
                                               Double.POSITIVE_INFINITY,
                                               Double.NEGATIVE_INFINITY};
      for (double invalidWeight : invalidWeights) {
        expectIllegalArgument(
          () -> recipes.registerOCol(classifier, "invalid", "ZTF-invalid",
                                     invalidWeight, "[1]", "[1.0]"),
          "invalid aggregate weights must be rejected");
        }
      for (double invalidWeight : invalidWeights) {
        expectIllegalArgument(
          () -> recipes.registerOCol(classifier, "invalid", "ZTF-invalid",
                                     1.0, java.util.List.of("1"),
                                     java.util.List.of(invalidWeight)),
          "invalid per-instance weights must be rejected");
        }
      expectIllegalArgument(
        () -> recipes.registerOCol(classifier, "invalid", "ZTF-invalid",
                                   1.0, java.util.List.of("1", "2"),
                                   java.util.List.of(1.0)),
        "instance and weight counts must match");
      require(client.g().V().count().next() == 0L &&
              client.g().E().count().next() == 0L,
              "invalid registration payloads must not mutate the graph");

      java.util.Map<String, Object> legacyAttributes = new java.util.LinkedHashMap<>();
      legacyAttributes.put("weight", "1.5");
      legacyAttributes.put("origin", "legacy-map");
      recipes.registerOCol(classifier, "legacy", "ZTF-legacy",
                           legacyAttributes, true);
      Object storedWeight = client.g().E().hasLabel("deepcontains").
                                  values("weight").next();
      require(storedWeight instanceof Double &&
              ((Double)storedWeight).doubleValue() == 1.5,
              "legacy numeric-string map weights must remain accepted and become numeric");
      }
    finally {
      client.close();
      }
    }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
      }
    }

  private static final class FailingInitializationClient extends GremlinClient {

    private final AtomicBoolean _closed;

    private FailingInitializationClient(AtomicBoolean closed) {
      super("test", 1, true);
      _closed = closed;
      initialize("test", 1);
      }

    @Override
    public void open(String hostname, int port) {}

    @Override
    public void connect() {
      throw new IllegalStateException("intentional connect failure");
      }

    @Override
    public void close() {
      _closed.set(true);
      throw new IllegalStateException("intentional cleanup failure");
      }
    }

  private static final class FakeClient implements ModifyingGremlinClient {

    private final GraphTraversalSource _source = TinkerGraph.open().traversal();
    private int _commits;

    @Override
    public GraphTraversalSource g() {
      return _source;
      }

    @Override
    public void commit() {
      _commits++;
      }

    private int commits() {
      return _commits;
      }

    @Override
    public void close() {
      try {
        _source.close();
        }
      catch (Exception e) {
        throw new RuntimeException(e);
        }
      }
    }

  public static final class TestWertex extends Wertex {

    public TestWertex(Vertex vertex, String fields) {
      super(vertex, fields == null ? null : fields.split(","));
      }

    @Override
    public Client client() {
      return null;
      }
    }

  public static final class EmptyHBaseClient extends HBaseClient {

    public String resultMode;

    public EmptyHBaseClient() throws LomikelException {
      super(null, (String)null);
      }

    @Override
    public java.util.Map<String, java.util.Map<String, String>> scan(
      String key, String search, String filter, long start, long stop,
      boolean ifkey, boolean iftime) {
      if ("missing-key".equals(resultMode)) {
        return java.util.Collections.singletonMap(
          "other-row", java.util.Collections.singletonMap("i:value", "other"));
        }
      if ("empty-row".equals(resultMode)) {
        return java.util.Collections.singletonMap(key, java.util.Collections.emptyMap());
        }
      return java.util.Collections.emptyMap();
      }

    public boolean addEmptyResult() {
      return addResult(org.apache.hadoop.hbase.client.Result.EMPTY_RESULT,
                       new java.util.TreeMap<>(), "*", false, true);
      }
    }

  private static final class OtherClassifier extends Classifier {

    private OtherClassifier() {
      setType(Type.XMATCH);
      setFlavor("other");
      }

    @Override
    public void classify(FinkGremlinRecipies recipes, String oid) throws LomikelException {}

    @Override
    public String survey() {
      return "ZTF";
      }

    @Override
    public FPC fpc() {
      return null;
      }
    }

  private static final class FailingClassifier extends Classifier {

    private FailingClassifier() {
      setType(Type.TAG);
      setFlavor("atomic-test");
      }

    @Override
    public void classify(FinkGremlinRecipies recipes, String oid) throws LomikelException {
      recipes.registerOCol(this, "new", oid, 2.0, "[2]", "[2.0]");
      throw new LomikelException("intentional classification failure");
      }

    @Override
    public String survey() {
      return "ZTF";
      }

    @Override
    public FPC fpc() {
      return null;
      }
    }

  private static final class TestClassifier extends Classifier {

    private TestClassifier() {
      setType(Type.TAG);
      setFlavor("");
      }

    @Override
    public void classify(FinkGremlinRecipies recipes, String oid) throws LomikelException {}

    @Override
    public String survey() {
      return "ZTF";
      }

    @Override
    public FPC fpc() {
      return null;
      }
    }
  }
