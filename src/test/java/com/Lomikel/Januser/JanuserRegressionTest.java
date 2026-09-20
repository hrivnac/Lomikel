package com.Lomikel.Januser;

import com.Lomikel.Utils.LomikelException;
import com.astrolabsoftware.FinkBrowser.FinkPortalClient.FPC;
import com.astrolabsoftware.FinkBrowser.Januser.Classifier;
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies;
import com.astrolabsoftware.FinkBrowser.Januser.OCol;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NoSuchElementException;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

/** Focused regression tests for Januser correctness bugs. */
public final class JanuserRegressionTest {

  private JanuserRegressionTest() {}

  public static void main(String[] args) throws Exception {
    testGetOrCreateCreatesMissingVertexAndReusesExistingVertex();
    testRecipeCommitUsesClientAbstraction();
    testOColEqualityDoesNotCollapseHashCollisions();
    testFinkRegistrationPreservesNumericWeights();
    testTimerCommitsIndependentlyOfReportingInterval();
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

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new AssertionError(message);
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
