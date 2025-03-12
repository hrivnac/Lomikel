@Grab('com.xlson.groovycsv:groovycsv:1.3')

import com.xlson.groovycsv.CsvParser
import static com.xlson.groovycsv.CsvParser.parseCsv

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction

import java.nio.file.Files
import java.nio.file.Paths

// Log
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

def csvFN    = "LightCurves.csv"
def curvesDN = "../data"

jdLength = 60
jdSize   = 50
fidSelection = '1'

log = LogManager.getLogger(this.class);

log.info("Creating Light Curves from " + csvFN + " in " + curvesDN)

def file = new File(csvFN)
def csvData = file.text
def rows = parseCsv(csvData, separator:',', quoteChar:'"')
def fileRowCount = [:].withDefault {0}

rows.each {row -> def objectId   = row["objectId"]
                  def maxclass   = row["maxclass"]
                  def fidList    = row["collect_list(fid)".trim()   ]?.split(";")             ?: []
                  def jdList     = row["collect_list(jd)".trim()    ]?.split(";")             ?: []
                  def magpsfList = row["collect_list(magpsf)".trim()]?.split(";")*.toDouble() ?: []
                  def jdToMagpsfMaps = [:].withDefault {[]}                 
                  fidList.eachWithIndex {fid, index -> def jd     = jdList[index] as double
                                                       def magpsf = magpsfList[index]
                                                       jdToMagpsfMaps[fid] << [jd, magpsf]
                                                       }
                  jdToMagpsfMaps.each {fid, dataPoints -> if (fid == fidSelection) {
                                                            dataPoints = dataPoints.collectEntries {[(it[0]):it[1]]}.entrySet().toList()
                                                            dataPoints.sort {it.key}
                                                            dataPoints  = dataPoints.collect {[it.key, it.value]} 
                                                            def jds     = dataPoints.collect {it[0]} as double[]
                                                            def magpsfs = dataPoints.collect {it[1]} as double[]
                                                            double minJD = jds.min()
                                                            double maxJD = jds.max()
                                                            def normalizedJDs = (0..<jdLength).collect {i -> minJD + i * (maxJD - minJD) / (jdLength - 1)} as double[]
                                                            def interpolator = new LinearInterpolator()
                                                            def splineFunction = interpolator.interpolate(jds, magpsfs)
                                                            def normalizedMagpsfs = normalizedJDs.collect {jd -> splineFunction.value(jd)} as double[]
                                                            def maxclassFixed = maxclass.replaceAll("/", "_")
                                                            def idxFile = new File("${curvesDN}/${maxclassFixed}_${fid}.idx")
                                                            def lstFile = new File("${curvesDN}/${maxclassFixed}_${fid}.lst")                     
                                                            def jdFile  = new File("${curvesDN}/${maxclassFixed}_${fid}.jd" )                     
                                                            idxFile.append("$objectId\n")
                                                            lstFile.append(normalizedMagpsfs.collect {sprintf("%.6f", it)}.join(" ") + "\n")
                                                            jdFile.append(normalizedJDs.collect {sprintf("%.6f", it)}.join(" ") + "\n")
                                                            fileRowCount[idxFile.path] += 1
                                                            fileRowCount[lstFile.path] += 1
                                                            fileRowCount[jdFile.path ] += 1
                                                            }
                                                          }
                  }
                  
                  
def nFiles = 0; 
fileRowCount.each {filePath, rowCount -> def fl = new File(filePath)
                                         if (rowCount < jdSize) {
                                           log.debug("Deleting ${filePath} (only ${rowCount} lines)")
                                           fl.delete()
                                           }
                                         else if (rowCount > jdSize) {
                                           log.info("Truncating ${filePath} to ${jdSize} lines (from ${rowCount} lines)")
                                           def lines = fl.readLines().take(jdSize)
                                           fl.text = lines.join("\n") + "\n"
                                           nFiles++;
                                           }
                                         else {
                                           nFiles++;
                                           }
                                         }  
log.info("" + (nFiles / 3) + " curves extracted");                                         
