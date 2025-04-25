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

Configurator.initialize(null, "../src/java/log4j2.xml")
def log = LogManager.getLogger(this.class)

def conf = evaluate(new File("../src/work/LightCurves/conf.groovy").text)
log.info("Conf: " + conf)

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
def classes      = conf.classes
def merging      = conf.merging

def cvsPath = csvDN + "/all.csv"
def outputDir = Paths.get(curvesDN + "/lstm_data")
Files.createDirectories(outputDir)

// Test ND4J initialization
try {
  def testArray = Nd4j.create([1.0, 2.0] as double[])
  log.debug("ND4J initialized successfully: ${testArray}")
  def numClasses = 76
  def labelData = new double[numClasses]
  labelData[1] = 1.0
  def testLabelArray = Nd4j.create(labelData).reshape(1, numClasses)
  log.debug("Test label array created: ${testLabelArray}")
  log.debug("ND4J backend: ${Nd4j.getBackend()}")
  }
catch (Exception e) {
  log.fatal("ND4J initialization failed: ${e.message}", e)
  throw e
  }

// Load table
def options = CsvReadOptions.builder(cvsPath)
                            .header(true)
                            .maxCharsPerColumn(100000)
                            .sampleSize(1000)
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
def parseArray = {String arrayStr, String targetType -> try {
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
table.columnNames().each {colName -> if (arrayColumns.contains(colName)) {
                                       def newName = newColumnNames[arrayColumns.indexOf(colName)]
                                       switch (newName) {
                                         case "fid":
                                         case "class": newColumns << StringColumn.create(newName)
                                         break
                                         case "magpsf":
                                         case "jd": newColumns << DoubleColumn.create(newName)
                                         break
                                         }
                                       }
                                     else {
                                       newColumns << StringColumn.create(colName)
                                       }
                                     }
def explodedTable = Table.create(newColumns)
def errors = []
table.forEach {row -> try {
                        def arrays = [
                          fid: parseArray(row.getString("collect_list(fid)"), "STRING"),
                          magpsf: parseArray(row.getString("collect_list(magpsf)"), "DOUBLE"),
                          jd: parseArray(row.getString("collect_list(jd)"), "DOUBLE"),
                          class: parseArray(row.getString("collect_list(class)"), "STRING")
                          ]
                        def lengths = arrays.values().collect {it.size()}
                        if (lengths.unique().size() > 1) {
                          throw new RuntimeException("Array lengths differ: $lengths")
                          }
                        if (!arrays.fid.every { it in fid_values }) {
                          throw new RuntimeException("Invalid fid values: ${arrays.fid}. Expected: $fid_values")
                          }
                        def arrayLength = lengths[0]
                        (0..<arrayLength).each {idx -> def newRow = explodedTable.appendRow()
                                                       table.columnNames().each {colName -> if (arrayColumns.contains(colName)) {
                                                                                              def newName = newColumnNames[arrayColumns.indexOf(colName)]
                                                                                              switch (newName) {
                                                                                                case "fid":
                                                                                                case "class": newRow.setString(newName, arrays[newName][idx])
                                                                                                break
                                                                                                case "magpsf":
                                                                                                case "jd": newRow.setDouble(newName, arrays[newName][idx])
                                                                                                break
                                                                                                }
                                                                                              }
                                                                                            else {
                                                                                              newRow.setString(colName, row.getString(colName))
                                                                                              }
                                                                                            }
                                                                                          }
                        }
                      catch (Exception e) {
                        errors << "Error processing objectId ${row.getString('objectId')}: ${e.message}"
                       }
  }

// Log explosion errors
if (errors) {
  log.warn("Explosion errors:")
  errors.each {log.warn("\t" + it)}
  }

// Verify exploded table
log.info("Exploded table structure: ${explodedTable.structure()}")
log.info("Exploded table shape: ${explodedTable.shape()}")
if (!explodedTable.columnNames().contains("objectId")) {
  log.fatal("Error: objectId column missing")
  return
  }

// Convert objectId to StringColumn if TextColumn
if (explodedTable.column("objectId") instanceof TextColumn) {
  def stringCol = StringColumn.create("objectId", explodedTable.column("objectId").asList())
  explodedTable.replaceColumn("objectId", stringCol)
  log.info("Converted objectId from TextColumn to StringColumn")
  }

// Debug maxclass and fid values
def maxclassValues = explodedTable.stringColumn("maxclass").asSet().findAll {it != null && !it.isEmpty()}
log.info("maxclass unique values: $maxclassValues")
log.info("maxclass null count: ${explodedTable.stringColumn('maxclass').count {it == null || it.isEmpty()}}")
log.info("fid unique values in data: ${explodedTable.stringColumn('fid').asSet()}")
log.info("Expected fid values: $fid_values")

// Validate classes
if (classes) {
  def missingClasses = classes.findAll {!maxclassValues.contains(it)}
  if (missingClasses) {
    log.warn("Warning: Some specified classes not found in data: $missingClasses")
    }
  explodedTable = explodedTable.where(explodedTable.stringColumn("maxclass").isIn(classes))
  log.info("Restricted to classes: $classes")
  log.info("Table after class restriction: ${explodedTable.shape()}")
  if (explodedTable.rowCount() == 0) {
    log.fatal("Error: No data remains after class restriction")
    return
    }
  }

// Apply class merging
def classMapping = [:]
if (merging) {
  merging.each {mergedClass, originalClasses -> originalClasses.each {origClass -> classMapping[origClass] = mergedClass}}
  // Validate merging classes
  def relevantClasses = explodedTable.stringColumn("maxclass").asSet()
  def applicableMappings = classMapping.findAll {origClass, mergedClass -> origClass in relevantClasses}
  if (applicableMappings) {
    log.info("Applying class mapping: $applicableMappings")
    def newMaxclass = StringColumn.create("maxclass", explodedTable.stringColumn("maxclass").collect {classMapping[it] ?: it})
    explodedTable.replaceColumn("maxclass", newMaxclass)
    log.info("Table after class merging: ${explodedTable.shape()}")
    }
  else {
    log.info("No applicable class mappings for current data: $classMapping")
    }
  }

// Filter objectIds with sufficient data for all fid values
def validIds
try {
  def groupByTable = explodedTable.splitOn(explodedTable.column("objectId"))
  log.info("Grouped ${groupByTable.size()} objectIds for validIds filtering")
  validIds = groupByTable.collect {g -> def gTable = g.asTable()
                                        def maxclass = g.getString(0, "maxclass")
                                        def isValid = fid_values.every {fid -> def fidTable = gTable.where(gTable.stringColumn("fid").isEqualTo(fid))
                                                                               def jd = fidTable.doubleColumn("jd").asList().findAll {it != null && !it.isNaN()}
                                                                               def jdUnique = jd.toSet().toList()
                                                                               fidTable.rowCount() > 0 && jdUnique.size() >= 2
                                                                               }
                                        isValid && maxclass && !maxclass.isEmpty() ? g.getString(0, "objectId") : null
                                        }.findAll {it}
  }
catch (Exception e) {
  log.info("validIds grouping error: ${e.message}")
  throw e
  }
explodedTable = explodedTable.where(explodedTable.stringColumn("objectId").isIn(validIds))
log.info("Filtered to ${explodedTable.rowCount()} rows with sufficient data for all fid values: $fid_values")
if (explodedTable.rowCount() == 0) {
  log.info("Error: No data remains after filtering for sufficient fid data")
  return
  }

// Validate labelSet
def labelSet = explodedTable.stringColumn("maxclass").asSet().findAll {it != null && !it.isEmpty()}.toList()
def numClasses = labelSet.size()
if (numClasses == 0) {
  log.info("Error: No valid maxclass values found. Cannot create labels.")
  return
  }
def labelToIndex = labelSet.withIndex().collectEntries {label, idx -> [label, idx]}
log.info("Label classes: $labelSet")
log.info("Number of classes: $numClasses")
                    
// Validate oidSet
def oidSet = explodedTable.stringColumn("objectId").asSet().findAll {it != null && !it.isEmpty()}.toList()
def numOids = oidSet.size()
if (numOids == 0) {
  log.info("Error: No valid objectId values found. Cannot create oids.")
  return
  }
def oidToIndex = oidSet.withIndex().collectEntries {oid, idx -> [oid, idx]}
log.info("Number of objectIds: $numOids")

// Group by objectId
def grouped
try {
  grouped = explodedTable.splitOn(explodedTable.column("objectId"))
  log.info("Grouped ${grouped.size()} objectIds using splitOn")
  }
catch (Exception e) {
  log.info("splitOn error: ${e.message}")
  throw e
  }

// Process light curves
def sequences = []
def labels    = []
def oids      = []
def groupCount = grouped.size()
def processed = 0
grouped.each {group -> processed++
                       if (processed % 1000 == 0) {
                         log.info("Processed $processed/$groupCount objectIds")
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
                         fid_values.each {fid -> def fidTable = group.asTable().where(group.stringColumn("fid").isEqualTo(fid))
                                                 def jdRaw  = fidTable.doubleColumn("jd"    ).asList().findAll {it != null && !it.isNaN()}
                                                 def magRaw = fidTable.doubleColumn("magpsf").asList().findAll {it != null && !it.isNaN()}
                                                 fidData[fid] = [jd: jdRaw, mag: magRaw]
                                                 }       
                         // Validate data for each fid
                         fid_values.each {fid -> def data = fidData[fid]
                                                 if (data.jd.size() == 0 || data.mag.size() == 0) {
                                                   throw new RuntimeException("Empty data for fid=$fid: jd size=${data.jd.size()}, mag size=${data.mag.size()}")
                                                   }
                                                 if (data.jd.size() != data.mag.size()) {
                                                   throw new RuntimeException("Mismatched sizes for fid=$fid: jd=${data.jd.size()}, mag=${data.mag.size()}")
                                                   }
                                                 }        
                         // Sort and remove duplicates for jd
                         def processData = {jdList, magList, fid -> if (!jdList || !magList) {
                                                                      throw new RuntimeException("Null input arrays for fid=$fid")
                                                                      }
                                                                    def pairs = [jdList, magList].transpose()
                                                                    def groupedByJd = pairs.groupBy {
                                                                                        it[0]}.collectEntries {jd, jdGroup -> def mags = jdGroup.collect { it[1] }.findAll {it != null && !it.isNaN()}
                                                                                        [jd, mags.empty ? Double.NaN : mags.sum() / mags.size()]
                                                                                        }
                                                                    def sorted = groupedByJd.findAll {k, v -> k != null && !v.isNaN()}.toSorted {a, b -> a.key <=> b.key}
                                                                    [sorted.collect {it.key}, sorted.collect { it.value }]
                                                                    }                         
                         def processedFidData = [:]
                         fid_values.each {fid ->  def (jd, mag) = processData(fidData[fid].jd, fidData[fid].mag, fid)
                                                  if (jd.size() < 2) {
                                                    throw new RuntimeException("Insufficient data after deduplication for fid=$fid: jd size=${jd.size()}")
                                                    }
                                                  processedFidData[fid] = [jd: jd, mag: mag]
                                                  }
        
                         // Create commonJd within overlapping range
                         def minJd = fid_values.collect {processedFidData[it].jd.min()}.max()
                         def maxJd = fid_values.collect {processedFidData[it].jd.max()}.min()
                         if (minJd == null || maxJd == null || minJd >= maxJd) {
                           throw new RuntimeException("Invalid or non-overlapping jd range: minJd=$minJd, maxJd=$maxJd")
                           }
        
                         // Sample commonJd
                         def step = (maxJd - minJd) / (maxSeqLength - 1)
                         def commonJd = (0..<maxSeqLength).collect {minJd + it * step}
        
                        // Interpolate magpsf for each fid
                        def interpolator = new LinearInterpolator()
                        def magInterps = [:]
                        fid_values.each {fid -> def jd = processedFidData[fid].jd
                                                def mag = processedFidData[fid].mag
                                                try {
                                                  def poly = interpolator.interpolate(jd as double[], mag as double[])
                                                  magInterps[fid] = commonJd.collect { poly.value(it) }
                                                  }
                                                catch (Exception e) {
                                                  throw new RuntimeException("Interpolation failed for fid=$fid: ${e.message}")
                                                  }
                                                }        
                        // Create sequence
                        def sequence = (0..<commonJd.size()).collect {idx -> def row = fid_values.collect {fid -> magInterps[fid][idx]}
                                                                             row.every {it != null && !it.isNaN()} ? row : null
                                                                             }.findAll {it != null}                       
                        if (sequence.empty) {
                          throw new RuntimeException("No valid data after interpolation")
                          }                       
                        def magValues = sequence.flatten().findAll {it != null && !it.isNaN()}
                        if (magValues.empty) {
                          throw new RuntimeException("No valid magnitude values for normalization")
                          }
                        def minMag = magValues.min()
                        def maxMag = magValues.max()
                        if (minMag == maxMag) {
                          throw new RuntimeException("Cannot normalize: minMag equals maxMag ($minMag)")
                          }
                        sequence = sequence.collect {row -> row.collect {v -> (v - minMag) / (maxMag - minMag)}}                        
                        if (sequence.size() < maxSeqLength) {
                          sequence += [[0.0] * fid_values.size()] * (maxSeqLength - sequence.size())
                          }
                        else if (sequence.size() > maxSeqLength) {
                          sequence = sequence[0..<maxSeqLength]
                          }                    
                        // Create sequence and label arrays
                        def seqArray
                        try {
                          seqArray = Nd4j.create(sequence as double[][])
                          log.debug("Created sequence array for $objectId: shape=${seqArray.shapeInfoToString()}")
                          }
                        catch (Exception e) {
                          throw new RuntimeException("Failed to create sequence array: ${e.message}")
                          }
                        def labelIdx = labelToIndex[maxclass]
                        def labelArray
                        def oidArray
                        try {
                          labelArray = Nd4j.create([labelIdx as int   ])
                          oidArray   = Nd4j.create(objectId)
                          log.debug("Created label array for $objectId, maxclass='$maxclass', idx=$labelIdx: shape=${labelArray.shapeInfoToString()}")
                          }
                        catch (Exception e) {
                          throw new RuntimeException("Failed to create label array for maxclass '$maxclass': ${e.message}")
                          }
                        sequences << seqArray
                        labels    << labelArray
                        oids      << oidArray
                        }
                      catch (Exception e) {
                        errors << "Error processing light curve for objectId ${group.rowCount() > 0 ? group.getString(0, 'objectId') : 'unknown'}: ${e.message}"
                        }
                      }

// Log processing errors
if (errors) {
  log.warn("Processing errors:")
  errors.each {log.warn("\t" + it)}
  }

// Validate sequences and labels
log.info("Generated ${sequences.size()} sequences and ${labels.size()} labels")
if (sequences.size() != labels.size()) {
  log.fatal("Error: Mismatched sequences (${sequences.size()}) and labels (${labels.size()})")
  return
  }
if (sequences.empty) {
  log.fatal( "Error: No valid sequences generated")
  return
  }
if (sequences.size() < groupCount) {
  log.warn("Warning: Processed ${sequences.size()} out of ${groupCount} objectIds. Check processing errors.")
  }

// Split data into train and test
def random = new Random(42)
def indices = (0..<sequences.size()).toList()
Collections.shuffle(indices, random)
def trainSize = (sequences.size() * trainRate).toInteger()
def trainIndices = indices[0..<trainSize]
def testIndices = indices[trainSize..<sequences.size()]
log.info("Splitting data: ${trainIndices.size()} train sequences, ${testIndices.size()} test sequences")

// Save sequences for DL4J
def trainFeatureDir = outputDir.resolve("train/features")
def trainLabelDir   = outputDir.resolve("train/labels")
def trainOidDir     = outputDir.resolve("train/oids")
def trainJdDir      = outputDir.resolve("train/jds")
def testFeatureDir  = outputDir.resolve("test/features")
def testLabelDir    = outputDir.resolve("test/labels")
def testOidDir      = outputDir.resolve("test/oids")
def testJdDir       = outputDir.resolve("test/jds")
Files.createDirectories(trainFeatureDir)
Files.createDirectories(trainLabelDir)
Files.createDirectories(trainOidDir)
Files.createDirectories(trainJdDir)
Files.createDirectories(testFeatureDir)
Files.createDirectories(testLabelDir)
Files.createDirectories(testOidDir)
Files.createDirectories(testJdDir)

// Save train and test files with continuous indexing
def trainSeqCounter = 0
def testSeqCounter = 0
sequences.eachWithIndex {seq, idx -> try {
                                       if (seq == null) {
                                         throw new RuntimeException("Null sequence at index $idx")
                                         }
                                       def isTrain = idx in trainIndices
                                       def featureDir = isTrain ? trainFeatureDir : testFeatureDir
                                       def labelDir   = isTrain ? trainLabelDir   : testLabelDir
                                       def oidDir     = isTrain ? trainOidDir     : testOidDir
                                       def seqIndex   = isTrain ? trainSeqCounter++ : testSeqCounter++
                                       def seqPath    = featureDir.resolve("seq_${seqIndex}.csv")
                                       def seqData    = (0..<seq.rows()).collect {t -> def row = seq.getRow(t)
                                                                                       row ? row.toDoubleVector().join(",") : ""
                                                                                       }.findAll { it }.join("\n")
                                       if (seqData.empty) {
                                         throw new RuntimeException("Empty sequence data at index $idx")
                                         }
                                       Files.writeString(seqPath, seqData)
                                       log.debug("Saved sequence $seqIndex to $seqPath")
                                       def labelArray = labels[idx]
                                       def oidArray   = oids[  idx]
                                       if (labelArray == null) {
                                         throw new RuntimeException("Null label at index $idx")
                                         }
                                       if (oidArray == null) {
                                         throw new RuntimeException("Null oid at index $idx")
                                         }
                                       def labelPath = labelDir.resolve("label_${seqIndex}.csv")
                                       def oidPath   = oidDir.resolve(  "oid_${seqIndex}.csv")
                                       def labelData = labelArray.getInt(0).toString()
                                       def oidData   = oidArray.getString(0)
                                       Files.writeString(labelPath, labelData)
                                       Files.writeString(oidPath,   oidData)
                                       log.debug("Saved label,oid$seqIndex to $labelPath,$oidPath")
                                       }
                                     catch (Exception e) {
                                       errors << "Error saving sequence/label/oid $idx: ${e.message}"
                                       e.printStackTrace()
                                       }
                                     }

// Log saving errors
if (errors.find {it.contains("saving sequence/label")}) {
  log.warn("Saving errors:")
  errors.findAll {it.contains("saving sequence/label")}.each {log.warn("\t" + it)}
  }
else {
  log.info("Successfully saved ${sequences.size()} sequence and label files (${trainIndices.size()} train, ${testIndices.size()} test)")
  }

// Save iterator config
def config = [
  trainFeatureDir: trainFeatureDir.toString(),
  trainLabelDir: trainLabelDir.toString(),
  testFeatureDir: testFeatureDir.toString(),
  testLabelDir: testLabelDir.toString(),
  numClasses: numClasses,
  maxSeqLength: maxSeqLength,
  fidValues: fidValues,
  trainRate: trainRate,
  classes: classes,
  merging: merging,
  trainSize: trainIndices.size(),
  testSize: testIndices.size()
  ]
try {
  Files.writeString(Paths.get(csvDN + "/iterator_config.json"), groovy.json.JsonOutput.toJson(config))
  log.info("Iterator config saved to ${csvDN}/iterator_config.json")
  }
catch (Exception e) {
  log.error("Error saving iterator config: ${e.message}")
  }
