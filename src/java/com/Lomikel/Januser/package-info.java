/**
 * Gremlin and JanusGraph access, lifecycle, recipe, and vertex-enrichment
 * support.
 *
 * <h2>Architecture</h2>
 *
 * <p>The package has four cooperating layers:</p>
 * <ul>
 *   <li>{@link com.Lomikel.Januser.ModifyingGremlinClient} owns a traversal
 *       source and the commit/close lifecycle. Implementations that can roll
 *       back also implement
 *       {@link com.Lomikel.Januser.TransactionalGremlinClient}.</li>
 *   <li>{@link com.Lomikel.Januser.JanusClient} opens an embedded JanusGraph
 *       connection from explicit HBase settings or a JanusGraph properties
 *       file. It is the mutation-capable client for operations that require a
 *       real transaction.</li>
 *   <li>{@link com.Lomikel.Januser.DirectGremlinClient} submits traversal
 *       bytecode to a remote Gremlin server. It is appropriate for
 *       traversal-only operations; recipes that call host-side element
 *       methods still require provider elements that support those methods.</li>
 *   <li>{@link com.Lomikel.Januser.StringGremlinClient} submits complete
 *       Gremlin scripts and returns driver results. It is intentionally
 *       separate from the typed traversal recipe API.</li>
 * </ul>
 *
 * <p>{@link com.Lomikel.Januser.GremlinRecipies} contains the Java mutation
 * and graph-lifecycle primitives. The Groovy
 * {@code GremlinRecipiesGT}/{@code GremlinRecipiesG} pair adds traversal and
 * analysis helpers while retaining the same client contract.</p>
 *
 * <h2>Lifecycle and transaction ownership</h2>
 *
 * <p>Constructing a recipe with a {@link
 * com.Lomikel.Januser.ModifyingGremlinClient} delegates commit and close to
 * that client. Constructing it directly with a traversal source leaves close
 * ownership with the caller and commits only when the underlying graph
 * advertises transactions. Operations that promise atomic replacement must
 * require a rollback-capable context: either a {@link
 * com.Lomikel.Januser.TransactionalGremlinClient} or a directly attached
 * traversal source whose graph advertises transactions. They must not emulate
 * rollback on a non-transactional remote connection.</p>
 *
 * <h2>Graph identity</h2>
 *
 * <p>Lomikel stores each native vertex and edge label again in the indexed
 * {@code lbl} property. Valid data therefore obeys:</p>
 * <pre>
 * vertex.label() == vertex.value("lbl")
 * edge.label()   == edge.value("lbl")
 * </pre>
 *
 * <p>Recipes normally constrain both values before reusing or mutating an
 * element. The duplicated property exists because native labels are not
 * indexable in the deployed graph schema; it is not a substitute for native
 * label validation.</p>
 *
 * <h2>Optional enrichment</h2>
 *
 * <p>{@link com.Lomikel.Januser.Wertex} and its HBase/Phoenix specializations
 * decorate graph vertices with external database values. They are a
 * compatibility and enrichment layer, not general-purpose replacements for
 * every TinkerPop {@code Vertex} implementation. Code that only needs graph
 * properties should keep the provider vertex unchanged.</p>
 */
package com.Lomikel.Januser;
