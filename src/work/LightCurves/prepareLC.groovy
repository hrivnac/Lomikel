@Grab('tech.tablesaw:tablesaw-core:0.43.1')
@Grab('org.apache.commons:commons-math3:3.6.1')
@Grab('org.deeplearning4j:deeplearning4j-core:1.0.0-beta6')
@Grab('org.nd4j:nd4j-native-platform:1.0.0-beta6')
@Grab('org.nd4j:nd4j-native:1.0.0-beta6')
@Grab('ch.qos.logback:logback-classic:1.2.11')
@Grab('org.slf4j:slf4j-api:1.7.30')
@Grab('org.apache.logging.log4j:log4j-api:2.24.3')
@Grab('org.apache.logging.log4j:log4j-core:2.24.3')

// Tablesaw
import tech.tablesaw.api.Table
import tech.tablesaw.api.DoubleColumn
import tech.tablesaw.api.IntColumn
import tech.tablesaw.api.StringColumn
import tech.tablesaw.api.TextColumn
import tech.tablesaw.io.csv.CsvReadOptions

// Apache Commons
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator
import org.nd4j.linalg.factory.Nd4j
import org.nd4j.linalg.api.ndarray.INDArray

// DL4J
import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator
import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader
import org.datavec.api.split.FileSplit

// Java
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Random

// Log
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configurator;

// -----------------------------------------------------------------------------

// Initialise

Configurator.initialize(null, "../src/java/log4j2.xml");
def log = LogManager.getLogger(this.class);

def conf = evaluate(new File("../src/work/LightCurves/conf.groovy").text)
log.info("Conf: " + conf);

def csvDN        = conf.csvDN
def curvesDN     = conf.curvesDN
def jdMinSize    = conf.jdMinSize
def jdSize       = conf.jdSize
def blockSize    = conf.blockSize
def normalize    = conf.normalize
def reduce       = conf.reduce
def fidValues    = conf.fidValues;fid_values = fidValues
def trainRate    = conf.trainRate
def maxSeqLength = conf.jdSize

def cvsPath = csvDN + "/all.csv"
def outputDir = Paths.get(curvesDN + "/lstm_data")
Files.createDirectories(outputDir)

// Test ND4J initialization
try {
  def testArray = Nd4j.create([1.0, 2.0] as double[])
  log.info("ND4J initialized successfully: ${testArray}")
  def numClasses = 76
  def labelData = new double[numClasses]
  labelData[1] = 1.0
  def testLabelArray = Nd4j.create(labelData).reshape(1, numClasses)
  log.info("Test label array created: ${testLabelArray}")
  log.info("ND4J backend: ${Nd4j.getBackend()}")
  }
catch (Exception e) {
  log.fatal("ND4J initialization failed: ${e.message}", e)
  throw e
  }

// Load table
def options = CsvReadOptions.builder(cvsPath)
                            .header(true)
                            .maxCharsPerColumn(100000)
                            .sampleSize(10000)
                            .build()
def table
try {
  table = Table.read().usingOptions(options)
  log.info("Original table structure: ${table.structure()}")
  log.info("Original table shape: ${table.shape()}")
  }
catch (Exception e) {
  log.fatal("Error loading CSV: ${e.message}")
  throw e
  }

// Define array columns and their new names
def arrayColumns = ["collect_list(fid)", "collect_list(magpsf)", "collect_list(jd)", "collect_list(class)"]
def newColumnNames = ["fid", "magpsf", "jd", "class"]

// Parse semicolon-separated arrays
def parseArray = {String arrayStr, String targetType ->
  try {
    arrayStr = arrayStr?.trim()
    if (!arrayStr) return []
    def values = arrayStr.split(";").collect {it.trim()}
    switch (targetType) {
      case "STRING": return values
      case "DOUBLE": return values.collect {it == "null" || it.isEmpty() ? Double.NaN : it.toDouble()}
      default: throw new IllegalArgumentException("Unknown type: $targetType")
      }
    }
  catch (Exception e) {
    throw new RuntimeException("Parsing error: ${e.message}")
    }
  }

// Explode the table
def newColumns = []
table.columnNames().each { colName ->
    if (arrayColumns.contains(colName)) {
        def newName = newColumnNames[arrayColumns.indexOf(colName)]
        switch (newName) {
            case "fid": case "class": newColumns << StringColumn.create(newName); break
            case "magpsf": case "jd": newColumns << DoubleColumn.create(newName); break
        }
    } else {
        newColumns << StringColumn.create(colName)
    }
}
def explodedTable = Table.create(newColumns)

def errors = []
table.forEach { row ->
    try {
        def arrays = [
            fid: parseArray(row.getString("collect_list(fid)"), "STRING"),
            magpsf: parseArray(row.getString("collect_list(magpsf)"), "DOUBLE"),
            jd: parseArray(row.getString("collect_list(jd)"), "DOUBLE"),
            class: parseArray(row.getString("collect_list(class)"), "STRING")
        ]
        def lengths = arrays.values().collect { it.size() }
        if (lengths.unique().size() > 1) {
            throw new RuntimeException("Array lengths differ: $lengths")
        }
        if (!arrays.fid.every { it in fidValues }) {
            throw new RuntimeException("Invalid fid values: ${arrays.fid}. Expected: $fidValues")
        }
        def arrayLength = lengths[0]
        (0..<arrayLength).each { idx ->
            def newRow = explodedTable.appendRow()
            table.columnNames().each { colName ->
                if (arrayColumns.contains(colName)) {
                    def newName = newColumnNames[arrayColumns.indexOf(colName)]
                    switch (newName) {
                        case "fid": case "class": newRow.setString(newName, arrays[newName][idx]); break
                        case "magpsf": case "jd": newRow.setDouble(newName, arrays[newName][idx]); break
                    }
                } else {
                    newRow.setString(colName, row.getString(colName))
                }
            }
        }
    } catch (Exception e) {
        errors << "Error processing objectId ${row.getString('objectId')}: ${e.message}"
    }
}

// Log explosion errors
if (errors) {
    println "Explosion errors:"
    errors.each { println it }
}

// Verify exploded table
println "Exploded table structure: ${explodedTable.structure()}"
println "Exploded table shape: ${explodedTable.shape()}"
if (!explodedTable.columnNames().contains("objectId")) {
    println "Error: objectId column missing"
    return
}

// Convert objectId to StringColumn if TextColumn
if (explodedTable.column("objectId") instanceof TextColumn) {
    def stringCol = StringColumn.create("objectId", explodedTable.column("objectId").asList())
    explodedTable.replaceColumn("objectId", stringCol)
    println "Converted objectId from TextColumn to StringColumn"
}

// Debug objectId column type
println "objectId column type: ${explodedTable.column('objectId').type()}"

// Debug maxclass and fid values
def maxclassValues = explodedTable.stringColumn("maxclass").asList()
def fidValuesInData = explodedTable.stringColumn("fid").asSet()
println "maxclass unique values: ${maxclassValues.toSet()}"
println "maxclass null count: ${maxclassValues.count { it == null || it.isEmpty() }}"
println "maxclass sample (first 10): ${maxclassValues.take(10)}"
println "fid unique values in data: $fidValuesInData"
println "Expected fid values: $fidValues"

// Filter objectIds with sufficient data for all fid values
def validIds
try {
    def groupByTable = explodedTable.splitOn(explodedTable.column("objectId"))
    println "Grouped ${groupByTable.size()} objectIds for validIds filtering"
    validIds = groupByTable.collect { g ->
        def gTable = g.asTable()
        def maxclass = g.getString(0, "maxclass")
        def isValid = fidValues.every { fid ->
            def fidTable = gTable.where(gTable.stringColumn("fid").isEqualTo(fid))
            def jd = fidTable.doubleColumn("jd").asList().findAll { it != null && !it.isNaN() }
            def jdUnique = jd.toSet().toList()
            fidTable.rowCount() > 0 && jdUnique.size() >= 2
        }
        isValid && maxclass && !maxclass.isEmpty() ? g.getString(0, "objectId") : null
    }.findAll { it }
} catch (Exception e) {
    println "validIds grouping error: ${e.message}"
    throw e
}
explodedTable = explodedTable.where(explodedTable.stringColumn("objectId").isIn(validIds))
println "Filtered to ${explodedTable.rowCount()} rows with sufficient data for all fid values: $fidValues"

// Validate labelSet
def labelSet = explodedTable.stringColumn("maxclass").asSet().findAll { it != null && !it.isEmpty() }.toList()
def numClasses = labelSet.size()
if (numClasses == 0) {
    println "Error: No valid maxclass values found. Cannot create labels."
    return
}
def labelToIndex = labelSet.withIndex().collectEntries { label, idx -> [label, idx] }
println "Label classes: $labelSet"
println "Number of classes: $numClasses"

// Group by objectId
def grouped
try {
    grouped = explodedTable.splitOn(explodedTable.column("objectId"))
    println "Grouped ${grouped.size()} objectIds using splitOn"
} catch (Exception e) {
    println "splitOn error: ${e.message}"
    throw e
}



// Process light curves
def sequences = []
def labels = []
def groupCount = grouped.size()
def processed = 0
grouped.each { group ->
    processed++
    if (processed % 1000 == 0) {
        println "Processed $processed/$groupCount objectIds"
    }
    try {
        if (group.rowCount() == 0) {
            throw new RuntimeException("Empty group")
        }
        def objectId = group.getString(0, "objectId")
        def maxclass = group.getString(0, "maxclass")
        if (!maxclass || maxclass.isEmpty() || !(maxclass in labelSet)) {
            throw new RuntimeException("Invalid or missing maxclass: $maxclass")
        }
        
        // Process each fid value
        def fidData = [:]
        fid_values.each { fid ->
            def fidTable = group.asTable().where(group.stringColumn("fid").isEqualTo(fid))
            def jdRaw = fidTable.doubleColumn("jd").asList().findAll { it != null && !it.isNaN() }
            def magRaw = fidTable.doubleColumn("magpsf").asList().findAll { it != null && !it.isNaN() }
            fidData[fid] = [jd: jdRaw, mag: magRaw]
        }
        
        // Validate data for each fid
        fid_values.each { fid ->
            def data = fidData[fid]
            if (data.jd.size() == 0 || data.mag.size() == 0) {
                throw new RuntimeException("Empty data for fid=$fid: jd size=${data.jd.size()}, mag size=${data.mag.size()}")
            }
            if (data.jd.size() != data.mag.size()) {
                throw new RuntimeException("Mismatched sizes for fid=$fid: jd=${data.jd.size()}, mag=${data.mag.size()}")
            }
        }
        
        // Sort and remove duplicates for jd
        def processData = { jdList, magList, fid ->
            if (!jdList || !magList) {
                throw new RuntimeException("Null input arrays for fid=$fid")
            }
            def pairs = [jdList, magList].transpose()
            def groupedByJd = pairs.groupBy { it[0] }.collectEntries { jd, jdGroup ->
                def mags = jdGroup.collect { it[1] }.findAll { it != null && !it.isNaN() }
                [jd, mags.empty ? Double.NaN : mags.sum() / mags.size()]
            }
            def sorted = groupedByJd.findAll { k, v -> k != null && !v.isNaN() }.toSorted { a, b -> a.key <=> b.key }
            [sorted.collect { it.key }, sorted.collect { it.value }]
        }
        
        def processedFidData = [:]
        fid_values.each { fid ->
            def (jd, mag) = processData(fidData[fid].jd, fidData[fid].mag, fid)
            if (jd.size() < 2) {
                throw new RuntimeException("Insufficient data after deduplication for fid=$fid: jd size=${jd.size()}")
            }
            processedFidData[fid] = [jd: jd, mag: mag]
        }
        
        // Create commonJd within overlapping range
        def minJd = fid_values.collect { processedFidData[it].jd.min() }.max()
        def maxJd = fid_values.collect { processedFidData[it].jd.max() }.min()
        if (minJd == null || maxJd == null || minJd >= maxJd) {
            throw new RuntimeException("Invalid or non-overlapping jd range: minJd=$minJd, maxJd=$maxJd")
        }
        
        // Sample commonJd
        def step = (maxJd - minJd) / (maxSeqLength - 1)
        def commonJd = (0..<maxSeqLength).collect { minJd + it * step }
        
        // Interpolate magpsf for each fid
        def interpolator = new LinearInterpolator()
        def magInterps = [:]
        fid_values.each { fid ->
            def jd = processedFidData[fid].jd
            def mag = processedFidData[fid].mag
            try {
                def poly = interpolator.interpolate(jd as double[], mag as double[])
                magInterps[fid] = commonJd.collect { poly.value(it) }
            } catch (Exception e) {
                throw new RuntimeException("Interpolation failed for fid=$fid: ${e.message}")
            }
        }
        
        // Create sequence
        def sequence = (0..<commonJd.size()).collect { idx ->
            def row = fid_values.collect { fid -> magInterps[fid][idx] }
            row.every { it != null && !it.isNaN() } ? row : null
        }.findAll { it != null }
        
        if (sequence.empty) {
            throw new RuntimeException("No valid data after interpolation")
        }
        
        def magValues = sequence.flatten().findAll { it != null && !it.isNaN() }
        if (magValues.empty) {
            throw new RuntimeException("No valid magnitude values for normalization")
        }
        def minMag = magValues.min()
        def maxMag = magValues.max()
        if (minMag == maxMag) {
            throw new RuntimeException("Cannot normalize: minMag equals maxMag ($minMag)")
        }
        sequence = sequence.collect { row ->
            row.collect { v -> (v - minMag) / (maxMag - minMag) }
        }
        
        if (sequence.size() < maxSeqLength) {
            sequence += [[0.0] * fid_values.size()] * (maxSeqLength - sequence.size())
        } else if (sequence.size() > maxSeqLength) {
            sequence = sequence[0..<maxSeqLength]
        }
        
        // Create sequence and label arrays
        def seqArray
        try {
            seqArray = Nd4j.create(sequence as double[][])
            println "Created sequence array for $objectId: shape=${seqArray.shapeInfoToString()}"
        } catch (Exception e) {
            throw new RuntimeException("Failed to create sequence array: ${e.message}")
        }
        
        def labelIdx = labelToIndex[maxclass]
        def labelArray
        try {
            labelArray = Nd4j.create([labelIdx as double])
            println "Created label array for $objectId, maxclass='$maxclass', idx=$labelIdx: shape=${labelArray.shapeInfoToString()}"
        } catch (Exception e) {
            throw new RuntimeException("Failed to create label array for maxclass '$maxclass': ${e.message}")
        }
        
        sequences << seqArray
        labels << labelArray
        
    } catch (Exception e) {
        errors << "Error processing light curve for objectId ${group.rowCount() > 0 ? group.getString(0, 'objectId') : 'unknown'}: ${e.message}"
    }
}

// Log processing errors
if (errors) {
    println "Processing errors:"
    errors.each { println it }
}

// Validate sequences and labels
println "Generated ${sequences.size()} sequences and ${labels.size()} labels"
if (sequences.size() != labels.size()) {
    println "Error: Mismatched sequences (${sequences.size()}) and labels (${labels.size()})"
    return
}
if (sequences.empty) {
    println "Error: No valid sequences generated"
    return
}
if (sequences.size() < groupCount) {
    println "Warning: Processed ${sequences.size()} out of ${groupCount} objectIds. Check processing errors."
}

// Split data into train and test
def random = new Random(42) // Fixed seed for reproducibility
def indices = (0..<sequences.size()).toList()
Collections.shuffle(indices, random)
def trainSize = (sequences.size() * trainRate).toInteger()
def trainIndices = indices[0..<trainSize]
def testIndices = indices[trainSize..<sequences.size()]
println "Splitting data: ${trainIndices.size()} train sequences, ${testIndices.size()} test sequences"

// Save sequences for DL4J
def trainFeatureDir = outputDir.resolve("train/features")
def trainLabelDir = outputDir.resolve("train/labels")
def testFeatureDir = outputDir.resolve("test/features")
def testLabelDir = outputDir.resolve("test/labels")
Files.createDirectories(trainFeatureDir)
Files.createDirectories(trainLabelDir)
Files.createDirectories(testFeatureDir)
Files.createDirectories(testLabelDir)

// Save train and test files with continuous indexing
def trainSeqCounter = 0
def testSeqCounter = 0
sequences.eachWithIndex { seq, idx ->
    try {
        if (seq == null) {
            throw new RuntimeException("Null sequence at index $idx")
        }
        def isTrain = idx in trainIndices
        def featureDir = isTrain ? trainFeatureDir : testFeatureDir
        def labelDir = isTrain ? trainLabelDir : testLabelDir
        def seqIndex = isTrain ? trainSeqCounter++ : testSeqCounter++
        def seqPath = featureDir.resolve("seq_${seqIndex}.csv")
        def seqData = (0..<seq.rows()).collect { t ->
            def row = seq.getRow(t)
            row ? row.toDoubleVector().join(",") : ""
        }.findAll { it }.join("\n")
        if (seqData.empty) {
            throw new RuntimeException("Empty sequence data at index $idx")
        }
        Files.writeString(seqPath, seqData)
        println "Saved sequence $seqIndex to $seqPath"
        
        def labelArray = labels[idx]
        if (labelArray == null) {
            throw new RuntimeException("Null label at index $idx")
        }
        def labelPath = labelDir.resolve("label_${seqIndex}.csv")
        def labelData = labelArray.getInt(0).toString()
        Files.writeString(labelPath, labelData)
        println "Saved label $seqIndex to $labelPath"
    } catch (Exception e) {
        errors << "Error saving sequence/label $idx: ${e.message}"
        e.printStackTrace()
    }
}

// Log saving errors
if (errors.find { it.contains("saving sequence/label") }) {
    println "Saving errors:"
    errors.findAll { it.contains("saving sequence/label") }.each { println it }
} else {
    println "Successfully saved ${sequences.size()} sequence and label files (${trainIndices.size()} train, ${testIndices.size()} test)"
}

// DL4J Iterators for train and test
try {
    // Train iterator
    def trainFeatureReader = new CSVSequenceRecordReader()
    trainFeatureReader.initialize(new FileSplit(trainFeatureDir.toFile()))
    def trainLabelReader = new CSVSequenceRecordReader()
    trainLabelReader.initialize(new FileSplit(trainLabelDir.toFile()))

    def trainIterator = new SequenceRecordReaderDataSetIterator(
        trainFeatureReader, trainLabelReader, batchSize, numClasses, true,
        SequenceRecordReaderDataSetIterator.AlignmentMode.EQUAL_LENGTH
    )

    println "Train iterator created with ${trainIndices.size()} sequences, $numClasses classes"
    trainIterator.each { dataSet ->
        println "Train batch: features=${dataSet.features.shapeInfoToString()}, labels=${dataSet.labels.shapeInfoToString()}"
    }

    // Test iterator
    def testFeatureReader = new CSVSequenceRecordReader()
    testFeatureReader.initialize(new FileSplit(testFeatureDir.toFile()))
    def testLabelReader = new CSVSequenceRecordReader()
    testLabelReader.initialize(new FileSplit(testLabelDir.toFile()))

    def testIterator = new SequenceRecordReaderDataSetIterator(
        testFeatureReader, testLabelReader, batchSize, numClasses, true,
        SequenceRecordReaderDataSetIterator.AlignmentMode.EQUAL_LENGTH
    )

    println "Test iterator created with ${testIndices.size()} sequences, $numClasses classes"
    testIterator.each { dataSet ->
        println "Test batch: features=${dataSet.features.shapeInfoToString()}, labels=${dataSet.labels.shapeInfoToString()}"
    }
} catch (Exception e) {
    println "Error creating iterator: ${e.message}"
    e.printStackTrace()
}

// Save iterator config
def config = [
    trainFeatureDir: trainFeatureDir.toString(),
    trainLabelDir: trainLabelDir.toString(),
    testFeatureDir: testFeatureDir.toString(),
    testLabelDir: testLabelDir.toString(),
    batchSize: batchSize,
    numClasses: numClasses,
    maxSeqLength: maxSeqLength,
    fid_values: fid_values,
    trainRate: trainRate
]
try {
    Files.writeString(Paths.get("iterator_config.json"), groovy.json.JsonOutput.toJson(config))
    println "Iterator config saved"
} catch (Exception e) {
    println "Error saving iterator config: ${e.message}"
}