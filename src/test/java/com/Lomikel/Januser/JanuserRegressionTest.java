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
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

/** Focused regression tests for Januser correctness bugs. */
public final class JanuserRegressionTest {

  private JanuserRegressionTest() {}

  public static void main(String[] args) throws Exception {
    testGetOrCreateCreatesMissingVertexAndReusesExistingVertex();
    testGetOrCreateSkipsWildcardProperties();
    testGetOrCreateMaterializesBeforeReturning();
    testGetOrCreateRequiresMatchingNativeLabel();
    testLabelMirrorCannotBeOverwritten();
    testMetaSchemaUnionsPropertiesAcrossSameLabelElements();
    testMetaSchemaPreservesAllEndpointPairs();
    testDeepDropHandlesCyclesAndNonJanusVertices();
    testRecipeCommitUsesClientAbstraction();
    testRecipeCommitSupportsTransactionFreeGraphs();
    testGimmePreservesPropertyTypes();
    testGimmeScopesCycleIdentityPerDestination();
    testGimmePreservesIdentityWithoutBidirectionalTraversal();
    testGimmeBoundedDepthIsPathOrderIndependent();
    testGimmeTracksIncomparableDepthBudgets();
    testOColEqualityDoesNotCollapseHashCollisions();
    testFinkRegistrationPreservesNumericWeights();
    testFinkRegistrationReplacementRemovesStaleAttributes();
    testFinkRegistrationReplacementCollapsesParallelEdges();
    testFailedNontransactionalReplacementPreservesExistingEdge();
    testConcurrentFinkRegistrationDoesNotDuplicateEdges();
    testFinkRegistrationRejectsInvalidWeightsBeforeMutation();
    testFinkRegistrationUsesOperationTimestamp();
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
    testMalformedFinkHBaseUrlDoesNotPoisonCache();
    testHertexMissingRowDoesNotFailSelectiveEnhancement();
    testHertexHandlesMissingRowKeyConfiguration();
    testHBaseEmptyResultIsIgnored();
    testWertexPreservesVertexIdentity();
    testDirectedEdgeResetIgnoresReverseEdge();
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

  private static void testGetOrCreateSkipsWildcardProperties() {
    FakeClient client = new FakeClient();
    try {
      GremlinRecipies recipes = new GremlinRecipies(client);
      Vertex existing = client.g().addV("object").
                               property("lbl", "object").
                               property("objectId", "wildcard-existing").
                               property("kind", "real").
                               next();

      Vertex found = recipes.getOrCreate("object",
                                         new String[] {"objectId", "kind"},
                                         new Object[] {"wildcard-existing", "*"}).next();
      require(!recipes.created(), "a wildcard lookup must reuse the matching vertex");
      require(existing.id().equals(found.id()), "wildcard lookup returned the wrong vertex");
      require(client.g().V().has("lbl", "object").has("objectId", "wildcard-existing").
                             count().next() == 1L,
              "wildcard lookup must not create a duplicate vertex");
      require("real".equals(found.value("kind")),
              "wildcard lookup must not overwrite the existing property");

      Vertex created = recipes.getOrCreate("object",
                                           new String[] {"objectId", "kind"},
                                           new Object[] {"wildcard-created", "*"}).next();
      require(recipes.created(), "a missing wildcard lookup must create a vertex");
      require(!created.property("kind").isPresent(),
              "a wildcard must not be persisted as a literal property value");
      }
    finally {
      client.close();
      }
    }

  private static void testGetOrCreateMaterializesBeforeReturning() {
    FakeClient client = new FakeClient();
    try {
      GremlinRecipies recipes = new GremlinRecipies(client);
      org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal<Vertex, Vertex> first =
        recipes.getOrCreate("object", "objectId", "deferred");
      org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal<Vertex, Vertex> second =
        recipes.getOrCreate("object", "objectId", "deferred");
      Vertex firstVertex = first.next();
      Vertex secondVertex = second.next();
      require(firstVertex.id().equals(secondVertex.id()),
              "deferred getOrCreate traversals must resolve to one materialized vertex");
      require(client.g().V().hasLabel("object").has("objectId", "deferred").count().next() == 1L,
              "deferred getOrCreate calls must not create duplicate vertices");
      }
    finally {
      client.close();
      }
    }

  private static void testGetOrCreateRequiresMatchingNativeLabel() {
    FakeClient client = new FakeClient();
    try {
      client.g().addV("wrong-native").property("lbl", "desired").
                 property("objectId", "native-check").next();
      GremlinRecipies recipes = new GremlinRecipies(client);
      Vertex result = recipes.getOrCreate("desired", "objectId", "native-check").next();
      require("desired".equals(result.label()) && "desired".equals(result.value("lbl")),
              "getOrCreate must require both native label and indexed lbl to match");
      require(client.g().V().hasLabel("desired").has("objectId", "native-check").count().next() == 1L,
              "a mismatched native-label vertex must not suppress correct creation");
      }
    finally {
      client.close();
      }
    }

  private static void testLabelMirrorCannotBeOverwritten() throws Exception {
    FakeClient client = new FakeClient();
    TinkerGraph cloneGraph = TinkerGraph.open();
    try {
      GremlinRecipies recipes = new GremlinRecipies(client);
      Vertex source = recipes.getOrCreate("source",
                                          new String[] {"id", "lbl"},
                                          new Object[] {"source-1", "corrupt-vertex"}).next();
      Vertex target = recipes.getOrCreate("target", "id", "target-1").next();
      recipes.addEdge(source, target, "relation",
                      new String[] {"lbl", "value"},
                      new Object[] {"corrupt-edge", 1}, true);
      new TestWertex(source, null).addEdge("wrapped", target, "lbl", "corrupt-wrapped");
      source.property("lbl").property("provenance", "source-label");

      GraphTraversalSource cloneTraversal = cloneGraph.traversal();
      recipes.gimme(source, cloneTraversal, 0, 1, false, null);
      require("source-label".equals(cloneTraversal.V().hasLabel("source").next().
                                                property("lbl").property("provenance").value()),
              "cloning must preserve meta-properties attached to the mirrored lbl property");
      recipes.createMetaSchema();

      requireAllLabelsMirrored(client.g());
      requireAllLabelsMirrored(cloneTraversal);
      }
    finally {
      client.close();
      cloneGraph.close();
      }
    }

  private static void requireAllLabelsMirrored(GraphTraversalSource traversal) {
    for (Vertex vertex : traversal.V().toList()) {
      require(vertex.property("lbl").isPresent() && vertex.label().equals(vertex.value("lbl")),
              "vertex lbl must exactly mirror its native label");
      }
    for (Edge edge : traversal.E().toList()) {
      require(edge.property("lbl").isPresent() && edge.label().equals(edge.value("lbl")),
              "edge lbl must exactly mirror its native label");
      }
    }

  private static void testMetaSchemaUnionsPropertiesAcrossSameLabelElements() throws Exception {
    FakeClient client = new FakeClient();
    TinkerGraph graph = (TinkerGraph)client.g().getGraph();
    Vertex first = client.g().addV("node").property("firstProperty", "one").next();
    Vertex second = client.g().addV("node").property("secondProperty", "two").next();
    Vertex target = client.g().addV("target").next();
    first.addEdge("relation", target, "firstEdgeProperty", "one");
    second.addEdge("relation", target, "secondEdgeProperty", "two");

    new GremlinRecipies(client).createMetaSchema();

    try (GraphTraversalSource check = graph.traversal()) {
      Vertex metaNode = check.V().hasLabel("MetaGraph").
                              has("MetaLabel", "node").next();
      require(metaNode.property("firstProperty").isPresent() &&
              metaNode.property("secondProperty").isPresent(),
              "meta schema must union properties from every vertex with the same label");
      org.apache.tinkerpop.gremlin.structure.Edge metaEdge =
        check.E().hasLabel("MetaGraph").has("MetaLabel", "relation").next();
      require(metaEdge.property("firstEdgeProperty").isPresent() &&
              metaEdge.property("secondEdgeProperty").isPresent(),
              "meta schema must union properties from every edge with the same label");
      }
    finally {
      graph.close();
      }
    }

  private static void testMetaSchemaPreservesAllEndpointPairs() throws Exception {
    FakeClient client = new FakeClient();
    TinkerGraph graph = (TinkerGraph)client.g().getGraph();
    Vertex alpha = client.g().addV("alpha").next();
    Vertex beta = client.g().addV("beta").next();
    Vertex gamma = client.g().addV("gamma").next();
    Vertex delta = client.g().addV("delta").next();
    alpha.addEdge("relation", beta);
    gamma.addEdge("relation", delta);

    new GremlinRecipies(client).createMetaSchema();

    try (GraphTraversalSource check = graph.traversal()) {
      Set<String> endpointPairs = new HashSet<>();
      for (org.apache.tinkerpop.gremlin.structure.Edge edge :
             check.E().hasLabel("MetaGraph").has("MetaLabel", "relation").toList()) {
        endpointPairs.add(edge.outVertex().value("MetaLabel") + "->" +
                          edge.inVertex().value("MetaLabel"));
        }
      require(endpointPairs.equals(Set.of("alpha->beta", "gamma->delta")),
              "meta schema must preserve every endpoint-label pair used by an edge label");
      }
    finally {
      graph.close();
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

  private static void testRecipeCommitSupportsTransactionFreeGraphs() throws Exception {
    TinkerGraph graph = TinkerGraph.open();
    try (GraphTraversalSource source = graph.traversal()) {
      source.addV("node").iterate();
      new GremlinRecipies(source).commit();
      require(source.V().hasLabel("node").count().next() == 1L,
              "commit must be a no-op when a raw graph does not support transactions");
      }
    finally {
      graph.close();
      }
    }

  private static void testGimmePreservesPropertyTypes() throws Exception {
    TinkerGraph sourceGraph = TinkerGraph.open();
    TinkerGraph targetGraph = TinkerGraph.open();
    try (GraphTraversalSource source = sourceGraph.traversal();
         GraphTraversalSource target = targetGraph.traversal()) {
      Vertex original = source.addV("node").
                               property("text", "value").
                               property("integer", 7).
                               property("flag", true).
                               property("decimal", 2.5d).next();
      original.property(VertexProperty.Cardinality.list, "tag", "x", "source", "first");
      original.property(VertexProperty.Cardinality.list, "tag", "y", "source", "second");
      Vertex clone = new GremlinRecipies(source).gimme(original, target, 0, 0, false, null);
      require(clone.value("text").equals("value") && clone.value("text") instanceof String,
              "gimme must preserve string properties");
      require(clone.value("integer").equals(7) && clone.value("integer") instanceof Integer,
              "gimme must preserve integer properties");
      require(clone.value("flag").equals(true) && clone.value("flag") instanceof Boolean,
              "gimme must preserve boolean properties");
      require(clone.value("decimal").equals(2.5d) && clone.value("decimal") instanceof Double,
              "gimme must preserve double properties");
      Set<Object> tags = new HashSet<>();
      Set<Object> tagSources = new HashSet<>();
      Iterator<VertexProperty<Object>> tagProperties = clone.properties("tag");
      while (tagProperties.hasNext()) {
        VertexProperty<Object> tag = tagProperties.next();
        tags.add(tag.value());
        tagSources.add(tag.value("source"));
        }
      require(tags.equals(Set.of("x", "y")) &&
              tagSources.equals(Set.of("first", "second")),
              "gimme must preserve multi-valued properties and their meta-properties");
      }
    finally {
      sourceGraph.close();
      targetGraph.close();
      }
    }

  private static void testGimmeScopesCycleIdentityPerDestination() throws Exception {
    TinkerGraph sourceGraph = TinkerGraph.open();
    TinkerGraph firstTargetGraph = TinkerGraph.open();
    TinkerGraph secondTargetGraph = TinkerGraph.open();
    try (GraphTraversalSource source = sourceGraph.traversal();
         GraphTraversalSource firstTarget = firstTargetGraph.traversal();
         GraphTraversalSource secondTarget = secondTargetGraph.traversal()) {
      Vertex first = source.addV("node").
                            property(org.apache.tinkerpop.gremlin.structure.T.id, "source-first").next();
      Vertex second = source.addV("node").
                             property(org.apache.tinkerpop.gremlin.structure.T.id, "source-second").next();
      first.addEdge("links", second, "rank", 3);
      first.addEdge("links", second, "rank", 4);
      GremlinRecipies recipes = new GremlinRecipies(source);

      Vertex firstClone = recipes.gimme(first, firstTarget, -1, -1, true, null);
      secondTarget.addV("sentinel").
                   property(org.apache.tinkerpop.gremlin.structure.T.id, firstClone.id()).iterate();
      Vertex secondClone = recipes.gimme(first, secondTarget, -1, -1, true, null);

      require(secondClone.label().equals("node"),
              "gimme must not reuse identity state from another destination graph");
      require(secondTarget.V().count().next() == 3L,
              "gimme must create both cycle vertices beside an ID-colliding destination vertex");
      require(secondTarget.E().hasLabel("links").count().next() == 2L,
              "gimme must preserve parallel edges without duplicating traversal copies");
      require(new HashSet<>(secondTarget.E().hasLabel("links").values("rank").toList()).
                equals(Set.of(3, 4)),
              "gimme must preserve properties on every parallel edge");
      }
    finally {
      sourceGraph.close();
      firstTargetGraph.close();
      secondTargetGraph.close();
      }
    }

  private static void testGimmePreservesIdentityWithoutBidirectionalTraversal() throws Exception {
    TinkerGraph sourceGraph = TinkerGraph.open();
    TinkerGraph targetGraph = TinkerGraph.open();
    try (GraphTraversalSource source = sourceGraph.traversal();
         GraphTraversalSource target = targetGraph.traversal()) {
      Vertex a = source.addV("node").next();
      Vertex b = source.addV("node").next();
      Vertex c = source.addV("node").next();
      Vertex d = source.addV("node").next();
      a.addEdge("links", b);
      a.addEdge("links", c);
      b.addEdge("links", d);
      c.addEdge("links", d);
      d.addEdge("links", a);

      new GremlinRecipies(source).gimme(a, target, 0, -1, false, null);

      require(target.V().count().next() == 4L,
              "gimme must clone shared and cyclic descendants once without bidirectional traversal");
      require(target.E().hasLabel("links").count().next() == 5L,
              "gimme must terminate while preserving every outbound edge in a directed cycle");
      }
    finally {
      sourceGraph.close();
      targetGraph.close();
      }
    }

  private static void testGimmeBoundedDepthIsPathOrderIndependent() throws Exception {
    for (boolean shallowPathFirst : new boolean[] {true, false}) {
      TinkerGraph sourceGraph = TinkerGraph.open();
      TinkerGraph targetGraph = TinkerGraph.open();
      try (GraphTraversalSource source = sourceGraph.traversal();
           GraphTraversalSource target = targetGraph.traversal()) {
        Vertex a = source.addV("node").next();
        Vertex x = source.addV("node").next();
        Vertex b = source.addV("node").next();
        Vertex c = source.addV("node").next();
        if (shallowPathFirst) {
          a.addEdge("links", x);
          a.addEdge("links", b);
          }
        else {
          a.addEdge("links", b);
          a.addEdge("links", x);
          }
        x.addEdge("links", b);
        b.addEdge("links", c);

        new GremlinRecipies(source).gimme(a, target, 0, 2, false, null);

        require(target.V().count().next() == 4L &&
                target.E().hasLabel("links").count().next() == 4L,
                "gimme bounded-depth output must not depend on path visitation order");
        }
      finally {
        sourceGraph.close();
        targetGraph.close();
        }
      }
    }

  private static void testGimmeTracksIncomparableDepthBudgets() throws Exception {
    TinkerGraph sourceGraph = TinkerGraph.open();
    TinkerGraph targetGraph = TinkerGraph.open();
    try (GraphTraversalSource source = sourceGraph.traversal();
         GraphTraversalSource target = targetGraph.traversal()) {
      Vertex root = source.addV("node").property("name", "root").next();
      Vertex shared = source.addV("node").property("name", "shared").next();
      Vertex outward = source.addV("node").property("name", "outward").next();
      Vertex unlocked = source.addV("node").property("name", "unlocked").next();
      shared.addEdge("links", root);
      root.addEdge("links", shared);
      shared.addEdge("links", outward);
      unlocked.addEdge("links", outward);

      new GremlinRecipies(source).gimme(root, target, 1, 2, true, null);

      require(new HashSet<>(target.V().values("name").toList()).
                equals(Set.of("root", "shared", "outward", "unlocked")),
              "gimme must explore every nondominated inbound/outbound depth budget");
      require(target.E().hasLabel("links").count().next() == 4L,
              "gimme must preserve edges discovered through incomparable depth budgets");
      }
    finally {
      sourceGraph.close();
      targetGraph.close();
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

  private static void testFinkRegistrationUsesOperationTimestamp() throws Exception {
    FakeClient client = new FakeClient();
    try {
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      TestClassifier classifier = new TestClassifier();
      recipes.registerOCol(classifier, "first", "timestamp-first", 1.0,
                           "[1]", "[1.0]");
      String firstDate = client.g().V().has("objectId", "timestamp-first").
                               values("importDate").next().toString();
      Thread.sleep(20L);
      recipes.registerOCol(classifier, "second", "timestamp-second", 1.0,
                           "[2]", "[1.0]");
      String secondDate = client.g().V().has("objectId", "timestamp-second").
                                values("importDate").next().toString();
      require(!firstDate.equals(secondDate),
              "each registration must record its operation time, not recipe construction time");
      }
    finally {
      client.close();
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

  private static void testMalformedFinkHBaseUrlDoesNotPoisonCache() throws Exception {
    FakeClient client = new FakeClient();
    try {
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      for (int attempt = 0; attempt < 2; attempt++) {
        boolean rejected = false;
        try {
          recipes.fhclient("malformed");
          }
        catch (LomikelException expected) {
          rejected = true;
          }
        require(rejected, "each malformed HBase URL call must fail explicitly");
        require(recipes.hbaseUrl() == null,
                "a malformed HBase URL must not enter the client cache");
        try {
          recipes.fhclient();
          throw new AssertionError("malformed URL must not initialize an HBase client");
          }
        catch (LomikelException expected) {
          // Expected: cache remains uninitialized.
          }
        }
      }
    finally {
      client.close();
      }
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

  private static void testHertexHandlesMissingRowKeyConfiguration() throws Exception {
    FakeClient client = new FakeClient();
    EmptyHBaseClient hbase = allocateWithoutConstructor(EmptyHBaseClient.class);
    Hertex.setHBaseClient(hbase);
    try {
      Vertex unconfigured = client.g().addV("unconfigured").next();
      NoRowkeyHertex noMapping = new NoRowkeyHertex(unconfigured);
      require(noMapping.rowkey() == null,
              "Hertex without row-key configuration must remain usable without HBase dressing");

      Wertex.setRowkeyName("configured-missing", MissingRowkeyHertex.class, "objectId");
      Vertex missingProperty = client.g().addV("configured-missing").next();
      MissingRowkeyHertex missing = new MissingRowkeyHertex(missingProperty);
      require(missing.rowkey() == null,
              "Hertex with a missing configured property must not dereference null row-key values");

      long before = client.g().V().count().next();
      try {
        Hertex.getOrCreate("unmapped-label", "row", client.g(), false);
        throw new AssertionError("missing row-key mappings must be rejected");
        }
      catch (IllegalStateException expected) {
        require(client.g().V().count().next() == before,
                "missing row-key mapping rejection must happen before graph mutation");
        }
      }
    finally {
      Hertex.setHBaseClient(null);
      client.close();
      }
    }

  private static void testDirectedEdgeResetIgnoresReverseEdge() {
    FakeClient client = new FakeClient();
    try {
      Vertex source = client.g().addV("node").next();
      Vertex target = client.g().addV("node").next();
      org.apache.tinkerpop.gremlin.structure.Edge forward =
        source.addEdge("links", target, "value", "forward");
      org.apache.tinkerpop.gremlin.structure.Edge reverse =
        target.addEdge("links", source, "value", "reverse");
      GremlinRecipies recipes = new GremlinRecipies(client);

      recipes.addEdge(source, target, "links", new String[] {"value"},
                      new Object[] {"updated"}, true);

      require("updated".equals(forward.value("value")),
              "reset must update the requested directed edge");
      require("reverse".equals(reverse.value("value")),
              "reset must not modify the reverse edge");
      require(recipes.getEdge(source, target, "links").equals(java.util.List.of(forward)) &&
              recipes.getEdge(target, source, "links").equals(java.util.List.of(reverse)),
              "getEdge must respect source-to-destination direction");
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

  private static void testFinkRegistrationReplacementRemovesStaleAttributes() {
    FakeClient client = new FakeClient();
    try {
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      TestClassifier classifier = new TestClassifier();
      java.util.Map<String, Object> original = new java.util.LinkedHashMap<>();
      original.put("weight", 1.0);
      original.put("obsolete", "stale");
      recipes.registerOCol(classifier, "replacement", "ZTF-replacement", original, true);

      java.util.Map<String, Object> replacement = new java.util.LinkedHashMap<>();
      replacement.put("weight", 2.0);
      replacement.put("current", "fresh");
      replacement.put("lbl", "corrupt");
      recipes.registerOCol(classifier, "replacement", "ZTF-replacement", replacement, true);

      Edge edge = client.g().E().hasLabel("deepcontains").next();
      require(client.g().E().hasLabel("deepcontains").count().next() == 1L,
              "replacement must retain exactly one registration edge");
      require(!edge.property("obsolete").isPresent(),
              "replacement must remove attributes omitted by the new payload");
      require(Double.valueOf(2.0).equals(edge.value("weight")) &&
              "fresh".equals(edge.value("current")),
              "replacement must store exactly the new registration attributes");
      require("deepcontains".equals(edge.value("lbl")),
              "replacement must preserve the structural edge marker");

      java.util.Map<String, Object> appended = new java.util.LinkedHashMap<>();
      appended.put("weight", 3.0);
      appended.put("lbl", "corrupt");
      recipes.registerOCol(classifier, "replacement", "ZTF-appended", appended, false);
      Edge appendedEdge = client.g().V().has("objectId", "ZTF-appended").
                                inE("deepcontains").next();
      require("deepcontains".equals(appendedEdge.value("lbl")),
              "append registration must preserve the structural edge marker");
      }
    finally {
      client.close();
      }
    }

  private static void testFinkRegistrationReplacementCollapsesParallelEdges() {
    FakeClient client = new FakeClient();
    try {
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      TestClassifier classifier = new TestClassifier();
      java.util.Map<String, Object> original = new java.util.LinkedHashMap<>();
      original.put("weight", 1.0);
      original.put("obsolete", "first");
      recipes.registerOCol(classifier, "parallel", "ZTF-parallel", original, true);
      Vertex ocol = client.g().V().hasLabel("OCol").has("cls", "parallel").next();
      Vertex object = client.g().V().hasLabel("object").has("objectId", "ZTF-parallel").next();
      ocol.addEdge("deepcontains", object, "lbl", "deepcontains", "obsolete", "second");

      java.util.Map<String, Object> replacement = new java.util.LinkedHashMap<>();
      replacement.put("weight", 2.0);
      replacement.put("current", "only");
      recipes.registerOCol(classifier, "parallel", "ZTF-parallel", replacement, true);

      java.util.List<Edge> edges = client.g().V(ocol).outE("deepcontains").
                                         where(inV().is(object)).toList();
      require(edges.size() == 1, "replacement must collapse parallel registration edges");
      Edge edge = edges.get(0);
      require(!edge.property("obsolete").isPresent() &&
              Double.valueOf(2.0).equals(edge.value("weight")) &&
              "only".equals(edge.value("current")),
              "collapsed replacement edge must contain exactly the new payload");
      }
    finally {
      client.close();
      }
    }

  private static void testFailedNontransactionalReplacementPreservesExistingEdge() {
    FakeClient client = new FakeClient();
    try {
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      TestClassifier classifier = new TestClassifier();
      java.util.Map<String, Object> original = new java.util.LinkedHashMap<>();
      original.put("weight", 1.0);
      original.put("stable", "keep");
      recipes.registerOCol(classifier, "safe-failure", "ZTF-safe-failure", original, true);

      java.util.Map<String, Object> invalid = new java.util.LinkedHashMap<>();
      invalid.put("weight", 2.0);
      invalid.put("", "invalid-key");
      expectIllegalArgument(
        () -> recipes.registerOCol(classifier, "safe-failure", "ZTF-safe-failure", invalid, true),
        "invalid replacement property must fail");

      java.util.List<Edge> edges = client.g().E().hasLabel("deepcontains").toList();
      require(edges.size() == 1, "failed nontransactional replacement must preserve one old edge");
      Edge edge = edges.get(0);
      require("keep".equals(edge.value("stable")) &&
              Double.valueOf(1.0).equals(edge.value("weight")),
              "failed nontransactional replacement must preserve the old payload");
      }
    finally {
      client.close();
      }
    }

  private static void testConcurrentFinkRegistrationDoesNotDuplicateEdges() throws Exception {
    FakeClient client = new FakeClient();
    try {
      FinkGremlinRecipies recipes = new FinkGremlinRecipies(client);
      TestClassifier classifier = new TestClassifier();
      int threadCount = 24;
      java.util.concurrent.CountDownLatch ready =
        new java.util.concurrent.CountDownLatch(threadCount);
      java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      java.util.List<Thread> threads = new java.util.ArrayList<>();
      for (int i = 0; i < threadCount; i++) {
        final int weight = i + 1;
        Thread thread = new Thread(() -> {
          try {
            ready.countDown();
            start.await();
            java.util.Map<String, Object> attributes = new java.util.LinkedHashMap<>();
            attributes.put("weight", (double)weight);
            attributes.put("writer", weight);
            recipes.registerOCol(classifier, "concurrent", "ZTF-concurrent", attributes, true);
            }
          catch (Throwable t) {
            failure.compareAndSet(null, t);
            }
          });
        threads.add(thread);
        thread.start();
        }
      require(ready.await(10, java.util.concurrent.TimeUnit.SECONDS),
              "concurrent registration workers did not become ready");
      start.countDown();
      for (Thread thread : threads) {
        thread.join(10000L);
        require(!thread.isAlive(), "concurrent registration worker did not terminate");
        }
      if (failure.get() != null) {
        throw new AssertionError("concurrent registration failed", failure.get());
        }

      require(client.g().V().has("lbl", "OCol").has("cls", "concurrent").count().next() == 1L,
              "concurrent registration must create one OCol vertex");
      require(client.g().V().has("lbl", "object").has("objectId", "ZTF-concurrent").count().next() == 1L,
              "concurrent registration must create one object vertex");
      require(client.g().E().hasLabel("deepcontains").count().next() == 1L,
              "concurrent replacement must retain one registration edge");
      requireAllLabelsMirrored(client.g());
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

  public static final class NoRowkeyHertex extends Hertex {

    public NoRowkeyHertex(Vertex vertex) {
      super(vertex, (String[])null);
      }

    }

  public static final class MissingRowkeyHertex extends Hertex {

    public MissingRowkeyHertex(Vertex vertex) {
      super(vertex, (String[])null);
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
