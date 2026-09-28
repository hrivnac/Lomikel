/**
 * Fink-specific graph model, mutation recipes, and traversal analyses.
 *
 * <h2>Recipe composition</h2>
 *
 * <ul>
 *   <li>{@link com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipies}
 *       contains mutation and lifecycle operations, including classification
 *       replacement and overlap regeneration.</li>
 *   <li>{@code FinkGremlinRecipiesGT} contains Groovy traversal and analysis
 *       operations.</li>
 *   <li>{@code FinkGremlinRecipiesG} combines both APIs without adding a
 *       second lifecycle or graph state.</li>
 * </ul>
 *
 * <h2>Core graph model</h2>
 *
 * <p>An {@code object} vertex is identified by {@code objectId}. An
 * {@code OCol} vertex represents one logical classification collection,
 * identified by {@code survey}, {@code classifier}, {@code flavor}, and
 * {@code cls}. A {@code deepcontains} edge points from an {@code OCol} to an
 * {@code object} and stores the numeric classification weight and optional
 * per-instance data. An {@code overlaps} edge points from a destination
 * {@code OCol} to the source {@code OCol} it interprets and stores the numeric
 * intersection.</p>
 *
 * <p>Logical identity does not imply physical uniqueness. Existing databases
 * can contain duplicate physical vertices or parallel edges. Recipes must
 * preserve their documented first/max/order behavior unless an explicit data
 * migration establishes stronger uniqueness constraints.</p>
 *
 * <h2>Label and transaction invariants</h2>
 *
 * <p>Every valid Fink vertex and edge mirrors its native label in the indexed
 * {@code lbl} property. Selection by {@code lbl} supports indexes. New
 * registration endpoint reuse validates both labels; existing cleanup and
 * correlation paths intentionally retain their historical {@code lbl}-only
 * selection so malformed-data compatibility and ordering do not change.</p>
 *
 * <p>{@code classifySource} owns one rollback-capable transaction for cleanup
 * and all replacement registrations. A nested {@code registerOCol} call joins
 * that operation and does not commit independently. A standalone
 * {@code registerOCol} call owns its commit call and rolls back failure only
 * when the attached context advertises rollback-capable transactions.
 * Temporary endpoint caches are operation-local, are published only after
 * successful initialization, and are cleared on both success and failure.</p>
 *
 * <h2>Traversal compatibility</h2>
 *
 * <p>Read-side Groovy recipes are expressed as standard, lambda-free Gremlin
 * bytecode where practical. Java mutation recipes also use host-side element
 * methods such as {@code Vertex.addEdge}; those operations require live
 * provider elements and are not made remote-safe merely by attaching a remote
 * traversal source.</p>
 */
package com.astrolabsoftware.FinkBrowser.Januser;
