import com.Lomikel.Januser.JanusClient
import com.astrolabsoftware.FinkBrowser.Januser.FinkGremlinRecipiesG
import com.astrolabsoftware.FinkBrowser.Januser.Classifier
import com.Lomikel.Utils.Timer;

// TinkerPop
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.id
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.values
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.addV
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.select
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.constant
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.inV
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.addE
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.V
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.has

// SQL
import java.sql.Timestamp

// Log
import org.apache.logging.log4j.Logger
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.core.config.Configurator

// ------------------------------------------------------------
// 0) setup
// ------------------------------------------------------------
Configurator.initialize(null, '../src/java/log4j2.xml')
logg = LogManager.getLogger(this.class)

logg.info("Processing NewTags")

jobImportDate = new Timestamp(System.currentTimeMillis())

timer = new Timer("entries", 100, 5);

// temporary defaults until they arrive on NewTag
defaultSurvey     = 'LSST'
defaultFlavor     = ''
defaultClassifier = 'FINK'

jc = new JanusClient("/opt/janusgraph-1/conf/gremlin-server/CC.properties")
gr = new FinkGremlinRecipiesG(jc)
g = gr.g()
graph = g.getGraph()

// ------------------------------------------------------------
// 1) collect all not-yet-imported NewTag vertices
//    grouped as: objectId -> cls -> [tagVertexIds, mjds]
// ------------------------------------------------------------
newTags = g.V().
    has('lbl', 'NewTag').
    not(has('processed', true)).
    limit(100000).
    project('tagId', 'objectId', 'cls', 'mjd').
      by(id()).
      by(values('objectId')).
      by(values('cls')).
      by(values('mjd')).
    toList();[]
    

logg.info("Processing ${newTags.size()} new tags")

grouped = [:].withDefault { [:].withDefault { [tagIds: [], mjds: []] } }

newTags.each { row ->
    def objectId = row.objectId
    def cls      = row.cls
    def mjd      = row.mjd
    def tagId    = row.tagId

    grouped[objectId][cls].tagIds << tagId
    grouped[objectId][cls].mjds   << mjd
}

// ------------------------------------------------------------
// 2) helpers: upsert object vertex, OCol vertex, edge
// ------------------------------------------------------------
getOrCreateObject = { objectId ->
    g.V().has('lbl', 'object').has('objectId', objectId).fold().
      coalesce(
        unfold(),
        addV('object').
          property('lbl', 'object').
          property('objectId', objectId).
          property('importDate', jobImportDate)
      ).next()
}

getOrCreateOCol = { cls, survey, flavor, classifier ->
    g.V().has('lbl', 'OCol').
      has('cls', cls).
      has('survey', survey).
      has('flavor', flavor).
      has('classifier', classifier).
      fold().
      coalesce(
        unfold(),
        addV('OCol').
          property('lbl', 'OCol').
          property('cls', cls).
          property('survey', survey).
          property('flavor', flavor).
          property('classifier', classifier)
      ).next()
}

getOrCreateDeepcontains = { ocolV, objectV ->
    g.V(ocolV).outE('deepcontains').where(inV().hasId(objectV.id())).fold().
      coalesce(
        unfold(),
        addE('deepcontains').from(V(ocolV)).to(V(objectV)).
          property('lbl', 'deepcontains').
          property('instances', []).
          property('weights', []).
          property('weight', 0.0d)
      ).next()
}

// ------------------------------------------------------------
// 3) process each objectId
//    - ensure object vertex
//    - ensure all needed OCol vertices
//    - merge new mjds into edge.instances
//    - rebuild edge.weights as 1.0 per instance
//    - normalize edge.weight across all classes for this object
// ------------------------------------------------------------
timer.start()
grouped.each { objectId, clsMap ->

    //logg.debug("processing ${objectId}")

    def objectV = getOrCreateObject(objectId)

    // read all existing deepcontains edges into this object
    def existingByCls = [:]
    g.V(objectV).
      inE('deepcontains').as('e').
      outV().has('lbl', 'OCol').as('ocol').
      project('edge', 'cls', 'instances', 'weights').
        by(select('e')).
        by(select('ocol').values('cls')).
        by(select('e').coalesce(values('instances'), constant([]))).
        by(select('e').coalesce(values('weights'), constant([]))).
      toList().
      each { rec ->
          existingByCls[rec.cls] = rec
      }

    // union of classes already present + newly seen for this object
    def allClasses = ([] as Set)
    allClasses.addAll(existingByCls.keySet())
    allClasses.addAll(clsMap.keySet())

    // merged data per cls for this object
    def merged = [:]

    allClasses.each { cls ->

        def survey     = defaultSurvey
        def flavor     = defaultFlavor
        def classifier = defaultClassifier

        def ocolV = getOrCreateOCol(cls, survey, flavor, classifier)
        def edge  = getOrCreateDeepcontains(ocolV, objectV)

        // existing values from edge
        def oldInstances = []
        if (existingByCls.containsKey(cls) && existingByCls[cls].instances != null) {
            oldInstances = existingByCls[cls].instances as List
        }

        // new values from NewTag
        def newInstances = []
        if (clsMap.containsKey(cls)) {
            newInstances = clsMap[cls].mjds as List
        }

        // merge and sort
        def mergedInstances = []
        mergedInstances.addAll(oldInstances)
        mergedInstances.addAll(newInstances)
        mergedInstances = mergedInstances.unique().sort()

        // currently all per-instance weights are 1.0
        def mergedWeights = mergedInstances.collect { 1.0d }

        merged[cls] = [
            ocolV    : ocolV,
            edge     : edge,
            instances: mergedInstances,
            weights  : mergedWeights,
            sumWeight: mergedWeights.sum(0.0d)
        ]
    }

    // normalize edge.weight for this object so all outgoing OCol->object
    // deepcontains weights sum to 1
    double totalWeight = merged.values().sum { it.sumWeight ?: 0.0d } as double

    merged.each { cls, data ->
        double normalizedWeight = totalWeight > 0.0d ? (data.sumWeight as double) / totalWeight : 0.0d

        g.E(data.edge.id()).
          property('instances', data.instances).
          property('weights',   data.weights).
          property('weight',    normalizedWeight).
          iterate()

        //logg.debug("\tcls=${cls}, count=${data.instances.size()}, weight=${normalizedWeight}")
    }

    // touch object importDate as part of this job
    g.V(objectV).property('importDate', jobImportDate).iterate()
    
    
    
    if (timer.report()) {
      gr.commit();
      }
    

}

// ------------------------------------------------------------
// 4) mark processed NewTag vertices as imported
// ------------------------------------------------------------
processedTagIds = newTags.collect { it.tagId }

if (!processedTagIds.isEmpty()) {
    g.V(processedTagIds).
      property('processed', true).
      iterate()
}
gr.commit()
logg.info("done at importDate=${jobImportDate}")

classifiers = new Classifier[]{Classifier.instance('FINK', 'LSST', '')}
gr.generateCorrelations(classifiers)


