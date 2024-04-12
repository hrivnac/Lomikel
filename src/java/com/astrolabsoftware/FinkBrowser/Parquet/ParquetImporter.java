package com.astrolabsoftware.FinkBrowser.Parquet;

import com.Lomikel.Utils.Init;
import com.Lomikel.Januser.JanusClient;
import com.Lomikel.Januser.GremlinRecipies;
import com.Lomikel.Utils.LomikelException;

// Parquet
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroup;
import org.apache.parquet.schema.GroupType;
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.*;

// Hadoop
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.Configuration;

// Tinker Pop
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Vertex;

// Janus Graph
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.attribute.Geoshape;

// Java
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Base64;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.JulianFields;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>ParquetImporter</code> imports <em>Parquet</em> files into <em>JanusGraph</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
// TBD: allow reset
// TBD: allow getOrCreate
public class ParquetImporter extends JanusClient {
        
  /** Import Parquet files or directory. 
    * @param args[0] The Janusgraph properties file. 
    * @param args[1] The Parquet file or directory with Parquet files.
    * @param args[2] The number of events to use for progress report (-1 means no report untill the end).
    * @param args[3] The number of events to commit in one step (-1 means commit only at the end).
    * @param args[4] The creation strategy. <tt>drop,replace,getOrCreate</tt>.
    * @throws LomikelException If anything goes wrong. */
   public static void main(String[] args) throws IOException {
    Init.init();
    if (args.length != 5) {
      log.error("ParquetImporter.exe.jar <JanusGraph properties> [<file>|<directory>] <report limit> <commit limit> [create|reuse|drop]");
      System.exit(-1);
      }
    try {
      ParquetImporter importer = new ParquetImporter(                args[0],
                                                     Integer.valueOf(args[2]),
                                                     Integer.valueOf(args[3]),
                                                                     args[4]);
      importer.timerStart();
      importer.process(args[1]);
      importer.commit();
      importer.close();
      }
    catch (LomikelException e) {
      log.fatal("Cannot import " + args[1] + " into " + args[0], e);
      System.exit(-1);
      }
    }
  
  /** Create with JanusGraph properties file.
    * @param properties  The file with the complete Janusgraph properties.
    * @param reportLimit The number of events to use for progress report (-1 means no report until the end).
    * @param commitLimit The number of events to commit in one step (-1 means commit only at the end).
    * @param strategy    The creation strategy. <tt>drop,replace,getOrCreate</tt>. */
  public ParquetImporter(String properties,
                         int    reportLimit,
                         int    commitLimit,
                         String strategy) {
    super(properties);
    log.info("Reporting after each " + reportLimit + " alerts");
    log.info("Committing after each " + commitLimit + " alerts");
    log.info("Using strategy: " + strategy);
    _reportLimit = reportLimit;
    _commitLimit = commitLimit;
    _create      = false;
    _reuse       = false;
    _replace     = false;
    _drop        = false;
    if (strategy.contains("create")) {
      _create = true;
      }
    if (strategy.contains("reuse")) {
      _reuse = true;
      }
    if (strategy.contains("replace")) {
      _replace = true;
      }
    if (strategy.contains("drop")) {
      _drop = true;
      }
    _gr = new GremlinRecipies(this);
    }
    

  /** Process directory with <em>Parquet</em> alert files.
    * @param dirFN The dirname of directiory with data file.
    * @param fileExt The file extention. 
    * @throws IOException           If problem with file reading.
    * @throws FileNotFoundException If problem with file founding. */
  public void processDir(String dirFN,
                         String fileExt) throws IOException, FileNotFoundException {  
    log.info("Loading directory " + dirFN);
    Path path = new Path(dirFN);
    Path p;
    int i = 0;
    for (FileStatus fileStatus : _fs.listStatus(path)) {
      p = fileStatus.getPath();
      if (_fs.isDirectory(p)) {
        processDir(dirFN + "/" + p.getName(), fileExt);
        }
      else if (p.getName().endsWith("." + fileExt)) {
        try {
          process(dirFN + "/" + p.getName());
          i++;
          }
        catch (IOException | LomikelException e) {
          log.error("Failed to process " + p, e);
          }
        }
      else {
        log.warn("Not " + fileExt + " file: " + p);
        }
      }
    timer("alerts created", _n, -1, -1);      
    log.info("" + i + " files loaded");
    }
     
  /** Process <em>Parquet</em> alert file or directory with files (recursive).
     * @param fn The filename of the data file
     *           or directory with files.
     * @throws IOException      If problem with file reading.
     * @throws LomikelException If anything wrong. */
  public void process(String fn) throws IOException, LomikelException {
    log.info("Loading " + fn);
    _conf = new Configuration();
    _fs = FileSystem.get(_conf);
    Path path = new Path(fn);
    if (_fs.isDirectory(path)) {
      processDir(fn, "parquet");
      return;
      }
    else if (!_fs.isFile(path)) {
      log.error("Not a file/directory: " + fn);
      return;
      }
    ParquetMetadata readFooter = ParquetFileReader.readFooter(_conf, path, ParquetMetadataConverter.NO_FILTER);
    MessageType schema = readFooter.getFileMetaData().getSchema();
    ParquetFileReader r = new ParquetFileReader(_conf, path, readFooter);
    PageReadStore pages = null;
    while (null != (pages = r.readNextRowGroup())) {
      final long rows = pages.getRowCount();
      log.info("Reading " + rows + " rows");      
      final MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
      final RecordReader<Group> recordReader = columnIO.getRecordReader(pages, new GroupRecordConverter(schema));
      String sTemp = "";
      Group g;
      int i = 0;
      while ((g = recordReader.read()) != null && ++i < rows) { // BUG?: i++ ?
        processGroup(g, "alert");
        timer("alerts processed", ++_n, _reportLimit, _commitLimit);      
        }
      }
    }   
    
  /** Process {@link Group} and create contained {@link Vertex}es with the
    * specified label. Runs recursively.
    * @param g   The {@link Group} to process.
    * @param lbl The label for created {@link Vertex}es.
    * @return    The created {@link Vertex}es. */
  private List<Vertex> processGroup(Group  g,
                                    String lbl) {
    lbl = reLabel(lbl);
    //log.info(lbl);
    SimpleGroup sg;
    GroupType type;
    String[] jt;
    int n;
    String name = null;
    String edgeName;
    boolean bypass = false;
    Vertex v = null;
    List<Vertex>              vertices = new ArrayList<>();
    Map<String, List<Vertex>> vs       = new TreeMap<>();
    Map<String, String>       props    = new TreeMap<>();
    if (g instanceof SimpleGroup) {
      sg = (SimpleGroup)g;
      type = sg.getType();
      n = type.getFieldCount();
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < g.getFieldRepetitionCount(i); j++) {
          jt = type.getType(i).toString().split(" "); // optionality, type, name, ...
          //log.info(lbl + ": " + type.getType(i).toString());
          switch (jt[1]) {
            case "int32":
              props.put(jt[2], "" + g.getInteger(i, j));
              break;
            case "int64":
              props.put(jt[2], "" + g.getLong(i, j));
              break;
            case "int96":
              props.put(jt[2], "" + int96toTimestamp(g.getInt96(i, j).getBytes()));
              break;
            case "float":
              props.put(jt[2], "" + g.getFloat(i, j));
              break;
            case "double":
              props.put(jt[2], "" + g.getDouble(i, j));
              break;
            case "binary":
              if (jt.length == 4 && jt[3].equals("(STRING)")) {
                props.put(jt[2], g.getString(i, j));
                }
              else {
                props.put(jt[2], Base64.getEncoder().encodeToString(g.getBinary(i, j).getBytes()));
                }
              break;
            case "group":
              name = type.getFieldName(i);
              if (name.equals("list")) {
                bypass = true;
                registerVertices(vs, lbl, processGroup(g.getGroup(i, j).getGroup(0, 0), lbl));
                }
              else {
                registerVertices(vs, name, processGroup(g.getGroup(i, j), name));
                }
              break;
            default:
              log.error("Uncovered  type of " + type.getType(i).toString());
            }
          }
        }
      if (bypass) {
        for (Map.Entry<String, List<Vertex>> vv : vs.entrySet()) {
          for (Vertex vvv : vv.getValue()) {
            vertices.add(vvv);
            }
          }
        }
      else {
        v = vertex(props, lbl, IDS.get(lbl));
        for (Map.Entry<String, List<Vertex>> vv : vs.entrySet()) {
          name = reLabel(vv.getKey());
          edgeName = RELATIONS.get(name);
          if (edgeName != null) {
            for (Vertex vvv : vv.getValue()) {
              _gr.addEdge(v, vvv, edgeName);
              }
            }
          else {
            log.error("Unknown relation for " + vv.getKey());
            }
          }
        vertices.add(v);
        }
      }
    else {
      log.info("Uncovered group " + g.getClass());
      }
    return vertices;
    }  
    
  /** Create or drop a {@link Vertex} according to chosen strategy.
    * @param props     The {@link Map} of values.
    * @param label     The {@link Vertex} label.
    * @param property  The name of {@link Vertex} property.
    *                  If <tt>null</tt> strategy is ignored and {@link Vertex} is created.
    * @return          The created {@link Vertex} or <tt>null</tt>. */
  private Vertex vertex(Map<String, String> props,
                        String              label,
                        String              property) {
    label = reLabel(label);
    Vertex v = null;
    // Not unique Vertex
    if (property == null) {
      if (_drop) {
        log.error("Cannot drop " + label + " of " + props); 
        }
      else  {
        log.debug("Creating: " + label);
        v = g().addV(label).property("lbl", label).next();
        }
      }
    // Unique Vertex
    else {
      if (_drop || _replace) {
        log.debug("Dropping " + label + ": " + property + " = " + props.get(property));
        _gr.drop(label, property, props.get(property), true);
        }
      if (_reuse) {
        log.info("Getting " + label + ": " + property + " = " + props.get(property));
        v = _gr.getOrCreate(label, property, props.get(property)).next();
        }
      if (_create || _replace) {
        log.debug("Creating " + label + ": " + property + " = " + props.get(property));
        v = g().addV(label).property("lbl", label).property(property, props.get(property)).next();
        }
      }
    // Fill properties
    if (property != null) {
      props.remove(property);
      }
    for (Map.Entry<String, String> prop : props.entrySet()) {
      try {
        v.property(prop.getKey(), prop.getValue());
        }
      catch (IllegalArgumentException e) {
        log.error("Unknown property: " + prop.getKey() + " = " + prop.getValue());
        }
      if (props.get("dec") != null && props.get("ra") != null) {
        v.property("direction", Geoshape.point(Double.valueOf(props.get("dec").toString()), Double.valueOf(props.get("ra").toString()) - 180));
        }
      }
    return v;
    }
    
  /** Register {@link Vertex}es in a {@link Map} according their label.
    * @param map The {@link Map} of all registered {@link Vertex}es, organised according their label.
    * @param key The label of new {@link Vertex}es/
    * @param vertices The {@link List} of new {@link Vertex}es to be added. */
  private void registerVertices(Map<String, List<Vertex>> map,
                                String                    key,
                                List<Vertex>              vertices) {
    if (!map.containsKey(key)) {
      map.put(key, new ArrayList<Vertex>());
      }
    for (Vertex vertex : vertices) {
      map.get(key).add(vertex);
      }
    }
    
  /** Change label.
    * @param label The original label.
    * @return      The changed label. */
  private String reLabel(String label) {
    if (VERTEXES.containsKey(label)) {
      label = VERTEXES.get(label);
      }
    return label;
    }

  /** Transform timestamp from int96 to {@link LocalDateTime}.
    * @param bytes The timestamp of int96 bytes.
    * @return      The timestamp converted to {@link LocalDateTime}. */
  private LocalDateTime int96toTimestamp(byte[] bytes) {
    int julianDay = 0;
    int index = bytes.length;
    while (index > 8) {
      index--;
      julianDay <<= 8;
      julianDay += bytes[index] & 0xFF;
      }
    long nanos = 0;
    while (index > 0) {
      index--;
      nanos <<= 8;
      nanos += bytes[index] & 0xFF;
      }
    LocalDateTime timestamp = LocalDate.MIN.with(JulianFields.JULIAN_DAY, julianDay)
                                           .atTime(LocalTime.NOON)
                                           .plusNanos(nanos);
    return timestamp;
    }
  
  private static Map<String, String> VERTEXES;
  
  private static Map<String, String> RELATIONS;
  
  private static Map<String, String> IDS;
    
  private Configuration _conf;
  
  private FileSystem _fs;
    
  private GremlinRecipies _gr;
  
  private int _n = 0;
  
  private int _reportLimit;
  
  private int _commitLimit;
  
  private boolean _create;
  
  private boolean _reuse;
  
  private boolean _replace;
  
  private boolean _drop;
            
  static {
    VERTEXES = new TreeMap<>();
    VERTEXES.put("prv_candidates",   "prv_candidate");
    VERTEXES.put("cutoutScience",    "Science");
    VERTEXES.put("cutoutTemplate",   "Template");
    VERTEXES.put("cutoutDifference", "Difference");
    RELATIONS = new TreeMap<>();
    RELATIONS.put("candidate",        "has");
    RELATIONS.put("prv_candidate",    "has");
    RELATIONS.put("mulens",           "has");
    RELATIONS.put("Science",          "cutout");
    RELATIONS.put("Template",         "cutout");
    RELATIONS.put("Difference",       "cutout");
    IDS = new TreeMap<>();
    IDS.put("alert", "objectId");
    }
    
  /** Logging . */
  private static Logger log = LogManager.getLogger(ParquetImporter.class);
                                                
  }
