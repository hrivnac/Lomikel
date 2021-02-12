package com.Lomikel.Januser;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Info;
import com.Lomikel.HBaser.HBaseClient;
import com.Lomikel.HBaser.Schema;

// Tinker Pop
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.structure.Property;
import org.apache.tinkerpop.gremlin.structure.VertexProperty;
import org.apache.tinkerpop.gremlin.structure.Direction;

// Janus Graph
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.JanusGraphFactory;

// HBase
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName ;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.filter.Filter;  
import org.apache.hadoop.hbase.filter.FilterList;  
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;  
import org.apache.hadoop.hbase.filter.RowFilter;  
import org.apache.hadoop.hbase.filter.PrefixFilter;  
import org.apache.hadoop.hbase.filter.CompareFilter.CompareOp;
import org.apache.hadoop.hbase.filter.BinaryPrefixComparator;
import org.apache.hadoop.hbase.filter.BinaryComparator;
import org.apache.hadoop.hbase.filter.RegexStringComparator;
import org.apache.hadoop.hbase.filter.SubstringComparator;
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter;
import org.apache.hadoop.hbase.filter.MultiRowRangeFilter.RowRange;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.Cell;

// Java
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NavigableMap;
import java.util.Properties;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

// Log4J
import org.apache.log4j.Logger;

/** <code>JanusClient</code> provides connection to Janus Graph.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class JanusClient {

  /** Extract implicite schema.
    * @param args[0] The operation: <tt>extract,populate</tt>.
    * @param args[1] TBD.
    * @throws Exception If fails. */ 
  public static void main(String[] args) throws Exception {
    Init.init();
    if (args[0].trim().equals("extract")) {
      new JanusClient(args[1],
                      args[2]).createMetaSchema();
      }
    else if (args[0].trim().equals("populate")) {
      String failedKey = args[9];
      do {
        failedKey = new JanusClient(args[1]).populateGraph(            args[2],
                                                           new Integer(args[3]),
                                                                       args[4],
                                                                       args[5],
                                                                       args[6],
                                                                       args[7],
                                                                       args[8],
                                                                       failedKey,
                                                                       args[10],
                                                           new Integer(args[11]),
                                                           new Integer(args[12]),
                                                           new Integer(args[13]),
                                                                       args[14].equals("true"),
                                                                       args[15].equals("true"),
                                                                       args[16].equals("true"));
        }
      while (!failedKey.equals(""));
      }                             
    else {
      System.err.println("Unknown function " + args[0] + ", try extract or populate");
      System.exit(-1);
      }
    System.exit(0);
    } 
    
  /** Create with default parameters. */
  public JanusClient() {
    this(Info.zookeeper(), Info.hbase_table());
    }
   
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param table    The HBase table. */
  public JanusClient(String hostname,
                     String table) {
    this(hostname, table, false);
    }
   
  /** Create with connection parameters.
    * @param hostname The HBase hostname.
    * @param table    The HBase table.
    * @param batch    Whether open graph for batch loading. */
  public JanusClient(String  hostname,
                     String  table,
                     boolean batch) {
    _hostname = hostname;
    _table    = table;
    _batch    = batch;
    Init.init();
    open();
    }
    
  /** Create with connection properties file.
    * @param properties The file with the complete properties. */
  public JanusClient(String properties) {
    Init.init();
    open(properties);
    }
    
  /** Open graph with already set parameters. */
  public void open() {
    log.info("Opening " + _table + "@" + _hostname);
    if (_batch) {
      log.info("\tas batch");
      }
    _graph = JanusGraphFactory.build()
                              .set("storage.backend",       "hbase")
                              .set("storage.hostname",      _hostname)
                              .set("storage.hbase.table",   _table)
                              .set("storage.batch-loading", _batch)
                              .open();
    _g = _graph.traversal();
    log.info("Connected");
    }
    
  /** Open graph with file-based properties.
    * @param properties The file with the complete properties. */
  public void open(String properties) {
    log.info("Opening " + properties);
    Properties p = new Properties();
    try {
      p.load(new FileInputStream(properties));
      }
    catch (IOException e) {
      log.error("Properties " + properties + " cannot be loaded", e);
      }
    _hostname = p.getProperty("storage.hostname");
    _table    = p.getProperty("storage.hbase.table");
    _batch = p.containsKey("storage.batch-loading") && p.getProperty("storage.batch-loading").equals("true");
    if (_batch) {
      log.info("\tas batch");
      }
    _graph = JanusGraphFactory.open(properties);
    _g = _graph.traversal();
    log.info("Connected");
    }
    
  /** Commit transaction. */
  public void commit() {
    _graph.tx().commit();
    log.info("Commited");
    }
    
  /** Close graph. */
  public void close() {
    _graph.close();
    log.info("Closed");
    }
    
  /** Reopen graph (commit, close, open). */
  public void reopen() {
    commit();
    close();
    open();
    }
    
  /** Give {@link GraphTraversalSource}.
    * @return {@link GraphTraversalSource}. */
  public GraphTraversalSource g() {
    return _g;
    }
    
  /** Extract implicite schema. */
  public void createMetaSchema() {
    log.info("Cleaning MetaGraph");
    g().V().hasLabel("MetaGraph").drop().iterate();
    g().E().hasLabel("MetaGraph").drop().iterate();
    commit();
    Map<String, Set<String>> vMap  = new HashMap<>();
    Map<String, Set<String>> eMap  = new HashMap<>();
    Map<String, String>      evMap = new HashMap<>();
    Set<String> vSet;
    Set<String> eSet;
    Property<Vertex> vP;
    Property<Edge>   eP;
    log.info("Scanning Vertexes");
    for (Vertex v : g().V().toList()) {
      vSet = new HashSet<>();
      vMap.put(v.label(), vSet);
      for (Iterator<VertexProperty<Vertex>> i = v.properties(); i.hasNext();) { 
        vP = i.next();
        vSet.add(vP.key());
        }
      }
    log.info("Scanning Edges");
    for (Edge e : g().E().toList()) {
      eSet = new HashSet<>();
      eMap.put(e.label(), eSet);
      evMap.put(e.label(), e.outVertex().label() + " " + e.inVertex().label());
      for (Iterator<Property<Edge>> i = e.properties(); i.hasNext();) { 
        eP = i.next();
        eSet.add(eP.key());
        }
      }
    Vertex v;
    for (Map.Entry<String, Set<String>> entry : vMap.entrySet()) {
      log.info("Adding Vertex " + entry.getKey());
      try {
        v = g().addV("MetaGraph").next();
        v.property("MetaLabel", entry.getKey());
        for (String p : entry.getValue()) {
          v.property(p, "");
          }
        }
      catch (Exception e) {
        log.error("... failed");
        }
      }
    String[] vvS;
    Edge e;
    for (Map.Entry<String, String> entry : evMap.entrySet()) {
      log.info("Adding Edge " + entry.getKey() + " : " + entry.getValue());
      vvS = entry.getValue().split(" ");
      v = g().V().has("MetaGraph", "MetaLabel", vvS[0]).next();
      e = v.addEdge("MetaGraph", g().V().has("MetaGraph", "MetaLabel", vvS[1]).next());
      e.property("MetaLabel", entry.getKey());
      for (String p : eMap.get(entry.getKey())) {
        e.property(p, "");
        }
      }
    commit();
    close();
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
    * @param limit           The maximal number of entries to process (-1 means all entries).
    * @param skip            The number of entries to skip (-1 or 0 means no skipping).
    * @param commitLimit     The number of events to commit in one step (-1 means commit only at the end).
    * @param reset           Whether remove all {@link Vertex}es with the define
    *                        label before populating or check for each one and only
    *                        create it if it doesn't exist yet.
    * @param getOrCreate     Whether check the existence of the vertex before creating it.
    *                        (Index-based verification is disabled for speed.)
    * @param fullFill        Whether fill all variables or just rowkey and lbl.
    * @return                Blank if the population has been executed correctly, the last
    *                        sucessfull key otherwise.
    * @throws IOException If anything goes wrong. */
  // TBD: allow replacing, updating
  // TBD: read only rowkey if fullFill = false
  // TBD: handle binary columns
  public String populateGraph(String  hbaseHost,
                              int     hbasePort,
                              String  hbaseTable,
                              String  tableSchema,
                              String  label,
                              String  rowkey,
                              String  keyPrefixSearch,
                              String  keyStart,
                              String  keyStop,
                              int     limit,
                              int     skip,
                              int     commitLimit,
                              boolean reset,
                              boolean getOrCreate,
                              boolean fullFill) throws IOException {
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
    else {
      log.info("\tfilling only " + rowkey + " and lbl");
      }
    if (!keyStart.equals("")) {
      log.info("Staring at " + keyStart);
      }
    if (!keyStop.equals("")) {
      log.info("Stopping at " + keyStop);
      }
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
    hc.scan(null, searchS, "*", 0, false, false);
    ResultScanner rs = hc.resultScanner();
    Schema schema = hc.schema();
    log.info("Populating Graph");
    Vertex v;
    String key;
    String lastInsertedKey = null;
    String failedKey       = null;
    String family;
    String field;
    String column;
    String value;
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
            v = getOrCreate(label, rowkey, key);
            }
          else {
            v = g().addV(label).property(rowkey, key).property("lbl", label).next();
            }
          if (fullFill) {
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
        
  /** Get a {@link Vertex}, create it if necessary.
    * @param label         The {@link Vertex} label.
    * @param propertyName  The name of {@link Vertex} property.
    * @param propertyValue The value of {@link Vertex} property.
    * @return              The created {@link Vertex}. */
  // TBD: allow replacing
  // TBD: check if it is really only one
  public Vertex getOrCreate(String label,
                            String propertyName,
                            Object propertyValue) {
     List<Vertex> vertexes = g().V().has("lbl", label)
                                    .has(propertyName, propertyValue)
                                    .fold()
                                    .coalesce(unfold(), 
                                              g().addV(label)
                                                 .property("lbl", label)
                                                 .property(propertyName, propertyValue)).toList();
     if (vertexes.size() > 1) {
       log.warn("" + vertexes.size() + " vertices found, only the first one returned");
       }
     else if (vertexes.size() == 0) {
       log.error("No vertex found");
       return null;
       }
     return vertexes.get(0);
     }
    
  /** Add an {@link Edge} between two {@link Vertex}s,
    * unless it exists.
    * @param v1       The first {@link Vertex}.
    * @param v2       The second {@link Vertex}.
    * @param relation The {@link Edge} name. */
  public void addEdge(Vertex v1,
                      Vertex v2,
                      String relation) {
    if (!checkEdge(v1, v2, relation)) {
      v1.addEdge(relation, v2);
      }
    }
    
  /** Check whether an {@link Edge} exists.
    * @param v1       The source {@link Vertex}.
    * @param v2       The destination {@link Vertex}.
    * @param relation The {@link Edge} name.
    * @return         Whether this {@link Edge} exists. */
  public boolean checkEdge(Vertex v1,
                           Vertex v2,
                           String relation) {
    _found = false;
    if (v1.vertices(Direction.OUT, relation).hasNext()) {
      v1.vertices(Direction.OUT, relation).forEachRemaining(v -> {
                                             if (v.equals(v2)) {
                                               _found = true;
                                               }
                                             });
      }
    return _found;
    }

  /** Start timer. */
  protected void timerStart() {
    _t = System.currentTimeMillis();
    }
    
  /** Timer snapshot. Report, commit.
    * @param msg           The message to use for loggiong.
    * @param i             The call number.
    * @param modulus       The <em>mod</em> to specify report frequency.
    * @param modulusCommit The <em>mod</em> to specify commit frequency.
    * @return              If any action has been commited. */
  protected boolean timer(String msg,
                          int    i,
                          int    modulus,
                          int    modulusCommit) {
    if (i == 0) {
      return false;
      }
    if (modulus > -1 && i%modulus != 0) {
      return false;
      }
    long dt = (System.currentTimeMillis() - _t) / 1000;
    if (dt == 0) {
      dt = 1;
      }
    log.info("" + i + " " + msg + " in " + dt + "s, freq = " + (i / dt) + "Hz");
    if (modulusCommit > -1 && i%modulusCommit == 0) {
	    commit();
      }
    return true;
    }    
    
  private String _table;
  
  private String _hostname;
  
  private boolean _batch;
    
  private boolean _found;  
    
  private JanusGraph _graph;
  
  private GraphTraversalSource _g;

  long _t = 0;

  /** Logging . */
  private static Logger log = Logger.getLogger(JanusClient.class);

  }
