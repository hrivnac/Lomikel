package com.astrolabsoftware.FinkBrowser.Januser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.LomikelException;
import com.Lomikel.HBaser.HBaseClient;
import com.Lomikel.HBaser.HBaseSchema;
import com.Lomikel.Januser.JanusClient;
import com.Lomikel.Januser.GremlinRecipies;

// Tinker Pop
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Vertex;

// Janus Graph
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.attribute.Geoshape;

// HBase
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.Cell;

// Java
import java.util.Map;
import java.util.NavigableMap;

// Log4J
import org.apache.log4j.Logger;

/** <code>StructureCreator</code> generates the network of higher level entities
  * from the LSST {@link Alert}s.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class StructureCreator extends JanusClient {

  /** Create JanusGraph structures from the HBase database.
    * @param args[0]  The operation to perform: <tt>populate</tt>.
    * @param args[1]  The file with the complete JanusGraph properties.
    * @param args[2]  The HBase hostname.
    * @param args[3]  The HBase port.
    * @param args[4]  The HBase table to replicate in Graph.
    * @param args[5]  The HBase table schema name.
    * @param args[6]  The label of newly created Vertexes.
    * @param args[7]  The row key name.
    * @param args[8]  The key prefix to limit replication to.
    * @param args[9]  The key to start search from (in ms), may be blank.
    * @param args[10] The key to stop search at (in ms), may be blank.
    * @param args[11] The time start search from, may be 0.
    * @param args[12] The time stop search at, may be 0.
    * @param args[13] The maximal number of entries to process (-1 means all entries).
    * @param args[14] The number of entries to skip (-1 or 0 means no skipping).
    * @param args[15] The number of events to commit in one step (-1 means commit only at the end).
    * @param args[16] Whether remove all {@link Vertex}es with the define
    *                 label before populating or check for each one and only
    *                 create it if it doesn't exist yet.
    * @param args[17] Whether check the existence of the vertex before creating it.
    *                 (Index-based verification is disabled for speed.)
    * @param args[18] Whether fill all variables or just rowkey and lbl.
    *                 Overrides partialFill.
    * @param args[19] List of (coma separated) HBase columns to fill besides the default column.
    * @param args[20] List of (coma separated) geopoint property name and HBase columns representing lat and long in deg.
    * @throws LomikelException If anything goes wrong. */
  public static void main(String[] args) throws Exception {
    Init.init();
    if (args[0].trim().equals("populate")) {
      String failedKey = args[9];
      do {
        failedKey = new StructureCreator(args[1]).populateGraphFromHBase(            args[2],
                                                                         new Integer(args[3]),
                                                                                     args[4],
                                                                                     args[5],
                                                                                     args[6],
                                                                                     args[7],
                                                                                     args[8],
                                                                                     failedKey,
                                                                                     args[10],
                                                                         new Long(   args[11]),
                                                                         new Long(   args[12]),
                                                                         new Integer(args[13]),
                                                                         new Integer(args[14]),
                                                                         new Integer(args[15]),
                                                                                     args[16].equals("true"),
                                                                                     args[17].equals("true"),
                                                                                     args[18].equals("true"),
                                                                                     args[19],
                                                                                     args[20]);
        }
      while (!failedKey.equals(""));
      }                             
    else {
      System.err.println("Unknown function " + args[0] + ", try or populate");
      System.exit(-1);
      }
    } 
       
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param table    The HBase table. */
  public StructureCreator(String hostname,
                          String table) {
    super(hostname, table, false);
    }
   
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param table    The HBase table.
    * @param batch    Whether open graph for batch loading. */
  public StructureCreator(String  hostname,
                          String  table,
                          boolean batch) {
    super(hostname, table, batch);
    }
    
  /** Create with connection properties file.
    * @param properties The file with the complete properties. */
  public StructureCreator(String properties) {
    super(properties);
    }
      
  /** Populate JanusGraph from HBase table.
    * @param hbaseHost       The HBase hostname.
    * @param hbasePort       The HBase port.
    * @param hbaseTable      The HBase table to replicate in Graph.
    * @param tableSchema     The HBase table schema name.
    * @param label           The label of newly created Vertexes.
    * @param rowkey          The row key name.
    * @param keyPrefixSearch The key prefix to limit replication to.
    * @param keyStart        The key to start search from, may be blank.
    * @param keyStop         The key to stop search at, may be blank.
    * @param start           The time start search from (in ms), may be 0.
    * @param stop            The time stop search at (in ms), may be 0.
    * @param limit           The maximal number of entries to process (-1 means all entries).
    * @param skip            The number of entries to skip (-1 or 0 means no skipping).
    * @param commitLimit     The number of events to commit in one step (-1 means commit only at the end).
    * @param reset           Whether remove all {@link Vertex}es with the define
    *                        label before populating or check for each one and only
    *                        create it if it doesn't exist yet.
    * @param getOrCreate     Whether check the existence of the vertex before creating it.
    *                        (Index-based verification is disabled for speed.)
    * @param fullFill        Whether fill all variables or just rowkey and lbl.
    *                        Overrides partialFill.
    * @param partialFill     List of (coma separated) HBase columns to fill besides the default column.
    * @param geopoint        List of (coma separated) geopoint property name and HBase columns representing lat and long in deg.
    * @return                Blank if the population has been executed correctly, the last
    *                        sucessfull key otherwise.
    * @throws LomikelException If anything goes wrong. */
  // TBD: allow replacing, updating
  // TBD: read only rowkey if fullFill = false
  // TBD: handle binary columns
  public String populateGraphFromHBase(String  hbaseHost,
                                       int     hbasePort,
                                       String  hbaseTable,
                                       String  tableSchema,
                                       String  label,
                                       String  rowkey,
                                       String  keyPrefixSearch,
                                       String  keyStart,
                                       String  keyStop,
                                       long    start,
                                       long    stop,
                                       int     limit,
                                       int     skip,
                                       int     commitLimit,
                                       boolean reset,
                                       boolean getOrCreate,
                                       boolean fullFill,
                                       String  partialFill,
                                       String  geopoint) throws LomikelException {
    log.info("Populating Graph from " + hbaseTable + "(" + tableSchema + ")@" + hbaseHost + ":" + hbasePort);
    log.info("\tvertex labels: " + label);
    log.info("\t" + rowkey + " starting with " + keyPrefixSearch);
    log.info("\tlimit/skip/commitLimit: " + limit + "/" + skip + "/" + commitLimit);
    if (reset) {
      log.info("\tcleaning before population");
      }
    if (getOrCreate) {
      log.info("\tadd vertex only if non-existent");
      }
    else {
      log.info("\tadd vertex even if it already exists");
      }
    if (fullFill) {
      log.info("\tfilling all variables");
      }
    else if (partialFill != null && !partialFill.trim().equals("")) {
      log.info("\tfilling " + rowkey + ",lbl," + partialFill.trim());
      }
    else {
      log.info("\tfilling only " + rowkey + " and lbl");
      }
    if (geopoint != null && !geopoint.trim().equals("")) {
      log.info("\tfilling also geopoint " + geopoint.trim());
      }
    if (!keyStart.equals("")) {
      log.info("Staring at " + keyStart);
      }
    if (!keyStop.equals("")) {
      log.info("Stopping at " + keyStop);
      }
    if (start > 0) {
      log.info("Staring at " + start + "ms");
      }
    if (stop > 0) {
      log.info("Stopping at " + stop + "ms");
      }
    GremlinRecipies gr = new GremlinRecipies(this);
    timerStart();
    if (reset) {                        
      log.info("Cleaning Graph, vertexes: " + label);
      g().V().has("lbl", label).drop().iterate();
      }
    commit();
    log.info("Connection to HBase table");
    HBaseClient hc = new HBaseClient(hbaseHost, hbasePort);
    hc.connect(hbaseTable, tableSchema); 
    hc.setLimit(0);
    String searchS = "key:key:" + keyPrefixSearch + ":prefix";
    if (!keyStart.equals("")) {
      searchS += ",key:startKey:" + keyStart;
      }
    if (!keyStop.equals("")) {
      searchS += ",key:stopKey:" + keyStop;
      }
    hc.scan(null, searchS, "*", start, stop, false, false);
    ResultScanner rs = hc.resultScanner();
    HBaseSchema schema = hc.schema();
    log.info("Populating Graph");
    Vertex v;
    String key;
    String lastInsertedKey = null;
    String failedKey       = null;
    String family;
    String field;
    String column;
    String value;
    byte[] b;
    String[] cc;
    byte[] lat;
    byte[] lon;
    String[] latclm;
    String[] lonclm;
    int i = 0;
    //for (Result r : rs) {
    //  i++;
    //  key = Bytes.toString(r.getRow());
    //  for (Cell cell : r.listCells()) {
    //    family = Bytes.toString(cell.getFamilyArray(),    cell.getFamilyOffset(),    cell.getFamilyLength());
    //    column = Bytes.toString(cell.getQualifierArray(), cell.getQualifierOffset(), cell.getQualifierLength());
    //    value  = Bytes.toString(cell.getValueArray(),     cell.getValueOffset(),     cell.getValueLength());
    //    }
    //  timer(label + "s created", i, 100, commitLimit);
    //  } 
    NavigableMap<byte[], NavigableMap<byte[], byte[]>>	 resultMap;
    try {
      for (Result r : rs) {
        resultMap = r.getNoVersionMap();
        key = Bytes.toString(r.getRow());
        if (!key.startsWith("schema")) {
          if (failedKey == null) {
            failedKey = key;
            }
          i++;
          if (i <= skip) {
            continue;
            }
          if (limit > 0 && i > limit) {
            break;
            }
          if (getOrCreate) {
            v = gr.getOrCreate(label, rowkey, key).next();
            }
          else {
            v = g().addV(label).property(rowkey, key).property("lbl", label).next();
            }
          v.property("hbase", true);
          if (fullFill) {
            v.property("fullfill", true);
            for (Map.Entry<byte[], NavigableMap<byte[], byte[]>> entry : resultMap.entrySet()) {
              family = Bytes.toString(entry.getKey());
              if (!family.equals("b")) {
                for (Map.Entry<byte[], byte[]> e : entry.getValue().entrySet()) {
                  field = Bytes.toString(e.getKey());
                  column = family + ":" + field;
                  if (schema != null) {
                    value = schema.decode(column, e.getValue());
                    }
                  else {
                    value = Bytes.toString(e.getValue());
                    }
                  v.property(field, value);
                  }
                }
              }
            }
          else {
            v.property("fullfill", false);
            if (partialFill != null && !partialFill.trim().equals("")) { // TBD: optimize
              for (String clm : partialFill.split(",")) {
                cc = clm.trim().split(":");
                b = resultMap.get(Bytes.toBytes(cc[0])).get(Bytes.toBytes(cc[1]));
                v.property(cc[1], schema.decode(clm, b));
                }
              }
            }
          if (geopoint != null && !geopoint.trim().equals("")) { // TBD: optimize
            cc = geopoint.split(",");
            latclm = cc[1].split(":");
            lonclm = cc[2].split(":");
            lat = resultMap.get(Bytes.toBytes(latclm[0])).get(Bytes.toBytes(latclm[1]));
            lon = resultMap.get(Bytes.toBytes(lonclm[0])).get(Bytes.toBytes(lonclm[1]));
            v.property(cc[0], Geoshape.point(new Double(schema.decode(cc[1], lat)), new Double(schema.decode(cc[2], lon)) - 180));
            }
          }
        if (timer(label + "s created", i - 1, 100, commitLimit)) {
          rs.renewLease();
          lastInsertedKey = key;
          failedKey       = null;
          }
        }
      }
    catch (Exception e) {
      log.fatal("Failed while inserting " + i + "th vertex,\tlast inserted vertex: " + lastInsertedKey + "\tfirst uncommited vertex: " + failedKey, e);
      close();
      hc.close();
      return lastInsertedKey;
      }
    timer(label + "s created", i - 1, -1, -1);
    commit();
    close();
    hc.close();
    return "";
    }
    
  /** Logging . */
  private static Logger log = Logger.getLogger(StructureCreator.class);

  }
