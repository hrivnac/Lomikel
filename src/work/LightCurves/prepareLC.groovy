@Grab('com.xlson.groovycsv:groovycsv:1.3')

import com.xlson.groovycsv.CsvParser
import static com.xlson.groovycsv.CsvParser.parseCsv

import java.nio.file.Files
import java.nio.file.Paths

def csvFN    = args[0]
def curvesDN = args[1]

jdSizeLimit = 10

println("Creating Light Curves from " + csvFN + " in " + curvesDN)

def file = new File(csvFN)
def csvData = file.text
def rows = parseCsv(csvData)

rows.each {row -> def objectId = row["objectId"]
                  def maxclass = row["maxclass"]
                  def fidList  = row["collect_list(fid)".trim()]?.split(";") ?: []
                  def jdList   = row["collect_list(jd)".trim() ]?.split(";") ?: []
                  if (jdList.size() >= jdSizeLimit) {
                    def jdToMagpsfMaps = [:].withDefault {[]}                 
                    fidList.eachWithIndex {fid, index -> def jd = jdList[index] as double
                                                         jdToMagpsfMaps[fid] << jd
                                                         }
                    jdToMagpsfMaps.each {fid, jdListForFid -> def idxFileName = "${curvesDN}/${maxclass}_${fid}.idx"
                                                              def lstFileName = "${curvesDN}/${maxclass}_${fid}.lst"
                                                              Files.createDirectories(Paths.get("${curvesDN}/")) 
                                                              new File(idxFileName).append("$objectId\n")
                                                              new File(lstFileName).append(jdListForFid.join(" ") + "\n")
                                                              }
                    }
                  }
