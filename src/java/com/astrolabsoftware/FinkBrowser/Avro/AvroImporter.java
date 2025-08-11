package com.astrolabsoftware.FinkBrowser.Avro;

import com.Lomikel.Utils.Init;

// Lomikel
import com.Lomikel.Januser.JanusClient;
import com.Lomikel.Januser.GremlinRecipies;
import com.Lomikel.Utils.LomikelException;

// Avro
import org.apache.avro.Schema.Field;
import org.apache.avro.Schema.Type;
import org.apache.avro.io.DatumReader;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericData.Array;
import org.apache.avro.generic.GenericDatumReader;

// Tinker Pop
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;
import org.apache.tinkerpop.gremlin.structure.Vertex;

// Janus Graph
import org.janusgraph.core.JanusGraph;
import org.janusgraph.core.attribute.Geoshape;

// Java
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;
import java.util.Arrays;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.Base64;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.FileSystems;

// Log4J
import org.apache.log4j.Logger;

/** <code>AvroImporter</code> imports <em>Avro</em> files into <em>JanusGraph</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class AvroImporter extends JanusClient {
        
  /** Import Avro files or directory. 
    * @param args[0] The Janusgraph properties file. 
    * @param args[1] The Avro file or directory with Avro files.
    * @param args[2] The directory for FITS files. If <tt>null</tt> or empty, FITS are included in the Graph. Ignored if HBase url set.
    * @param args[3] The url for HBase table with full data as <tt>ip:port:table:schema</tt>. May be <tt>null</tt> or empty.
    * @param args[4] The number of events to use for progress report (-1 means no report untill the end).
    * @param args[5] The number of events to commit in one step (-1 means commit only at the end).
    * @param args[6] The creation strategy. <tt>create,drop,replace,skip</tt>.
    * @param args[7] The data type, <tt>alert|pca</tt>. If <tt>null<tt>, then considering as <tt>alert</tt>.
    * @throws LomikelException If anything goes wrong. */
   public static void main(String[] args) throws IOException {
    Init.init("AvroImporter");
    if (args.length != 8) {
      log.error("AvroImporter.exe.jar <JanusGraph properties> [<file>|<directory>] <hbaseUrl> <report limit> <commit limit> [create|reuse|drop] [alert|pca]");
      System.exit(-1);
      }
    try {
      AvroImporter importer = new AvroImporter(                args[0],
                                               Integer.valueOf(args[4]),
                                               Integer.valueOf(args[5]),
                                                               args[6],
                                                               args[2],
                                                               args[3],
                                                               args[7]);
      importer.timerStart();                    
      importer.process(args[1]);
      if (!importer.skip()) {
        importer.commit();
        }
      importer.close();
      }
    catch (LomikelException e) {
      log.fatal("Cannot import " + args[1] + " into " + args[0], e);
      System.exit(-1);
      }
    }
  
  /** Create with JanusGraph properties file.
    * @param properties  The file with the complete Janusgraph properties.
    * @param reportLimit The number of events to use for progress report (-1 means no report untill the end).
    * @param commitLimit The number of events to commit in one step (-1 means commit only at the end).
    * @param strategy    The creation strategy. <tt>drop,replace,getOrCreate</tt>.
    * @param fitsDir     The directory for FITS files. If <tt>null</tt> or empty, FITS are included in the Graph. Ignored if HBase url set.
    * @param hbaseUrl    The url for HBase table with full data as <tt>ip:port:table:schema</tt>. May be <tt>null</tt> or empty.
    * @param dataType    The data type, <tt>alert|pca</tt>.  If <tt>null<tt>, then considering as <tt>alert</tt>.*/  
  public AvroImporter(String properties,
                      int    reportLimit,
                      int    commitLimit,
                      String strategy,
                      String fitsDir,
                      String hbaseUrl,
                      String dataType) {
    super(properties);
    if (fitsDir != null && fitsDir.trim().equals("")) {
      fitsDir = null;
      }
    if (hbaseUrl != null && hbaseUrl.trim().equals("")) {
      hbaseUrl = null;
      }
    log.info("Reporting after each " + reportLimit + " alerts");
    log.info("Committing after each " + commitLimit + " alerts");
    log.info("Using strategy: " + strategy);
    log.info("Importing " + dataType + "s");
    if (fitsDir == null) {
      log.info("Writing FITS into Graph");
      }
    else {
      log.info("Writing FITS into: " + fitsDir);
      }
    if (hbaseUrl != null) {
      log.info("Connecting to data from: " + hbaseUrl);
      }
    log.info("Importing at " + _date);
    _reportLimit = reportLimit;
    _commitLimit = commitLimit;
    _fitsDir     = fitsDir;
    _hbaseUrl    = hbaseUrl;
    _dataType    = dataType;
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
    if (strategy.contains("skip")) {
      _skip = true;
      }
    _gr = new GremlinRecipies(this);
    }
        
  /** Process directory with <em>Avro</em> alert files (recursive).
    * @param dirFN The dirname of directiory with data file.
    * @param fileExt The file extention.
    * @throws IOException      If problem with file reading. */
  public void processDir(String dirFN,
                         String fileExt) throws IOException {  
    log.info("Loading directory " + dirFN);
    File dir = new File(dirFN);
    int i = 0;
    for (String dataFN : dir.list()) {
      if (new File(dirFN + "/" + dataFN).isDirectory()) {
        processDir(dirFN + "/" + dataFN, "avro");
        }
      else if (dataFN.endsWith("." + fileExt)) {
        try {
          process(dirFN + "/" + dataFN);
          i++;
          }
        catch (IOException | LomikelException e) {
          log.error("Failed to process " + dirFN + "/" + dataFN, e);
          }
        }
      else {
        log.warn("Not " + fileExt + " file: " + dataFN);
        }
      }
    timer("alerts/pcas created", _n, -1, -1);      
    log.info("" + i + " files loaded");
    }
     
  /** Process <em>Avro</em> alert file or directory with files (recursive).
     * @param fn The filename of the data file
     *           or directory with files.
     * @throws IOException      If problem with file reading.
     * @throws LomikelException If anything wrong. */
  public void process(String fn) throws IOException, LomikelException {
    log.info("Loading " + fn);
    register(fn);
    File file = new File(fn);
    if (file.isDirectory()) {
      processDir(fn, "avro");
      return;
      }
    else if (!file.isFile()) {
      log.error("Not a file/directory: " + fn);
      return;
      }
    processFile(file);
    }
    
  /** Register <em>Import</em> {@link Vertex}.
     * @param fn The filename of the data file
     *           or directory with files. */
  public void register(String fn) {
    if (_top) {
      _topFn = fn;
      now(); 
      Vertex import1 = g().addV("Import").property("lbl", "Import").property("importSource", fn).property("importDate", _date).next();
      Vertex imports = g().V().has("lbl", "site").has("title", "IJCLab").out().has("lbl", "Imports").next();
      _gr.addEdge(imports, import1, "has"); 
      commit();
      }
    _top = false;
    }
    
  /** Process <em>Avro</em> alert file .
     * @param file The data file.
     * @throws IOException If problem with file reading. */
  public void processFile(File file) throws IOException {
    DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>();
    DataFileReader<GenericRecord> dataFileReader = new DataFileReader<GenericRecord>(file, datumReader);
    GenericRecord record = null;
    while (dataFileReader.hasNext()) {
      record = dataFileReader.next(record);
      processRecord(record);
      }
    dataFileReader.close();
    } 
    
  /** Process {@link GenericRecord} of the requested type.
    * @param record The {@link GenericRecord} to be rocessed. */
  public void processRecord(GenericRecord record) {
    if (_dataType.equals("alert")) {
      processAlert(record);
      }
    else if (_dataType.equals("pca")) {
      processPCA(record);
      }
    else {
      log.error("Unknown data type: " + _dataType);
      }
    }
   
  /** Process <em>Avro</em> alert.
    * @param record The full alert {@link GenericRecord}.
    * @return       The created {@link Vertex}. */
  public Vertex processAlert(GenericRecord record) {
    _n++;
    Map<String, String> values = getSimpleValues(record, getSimpleFields(record,
                                                                         COLUMNS_ALERT,
                                                                         new String[]{"objectId",
                                                                                      "candidate",
                                                                                      "prv_candidates",
                                                                                      "cutoutScience",
                                                                                      "cutoutTemplate",
                                                                                      "cutoutDifference"}));
    Vertex v = vertex(record, "alert", null, "create");
    if (v != null) {
      String objectId = record.get("objectId").toString();
      for (Map.Entry<String, String> entry : values.entrySet()) {
        try {
          v.property(entry.getKey(), entry.getValue());
          }
        catch (IllegalArgumentException e) {
          log.error("Cannot add property: " + entry.getKey() + " = " + entry.getValue(), e);
          }
        }
      v.property("objectId",     objectId);
      v.property("alertVersion", VERSION);
      v.property("importDate",   _date);
      String ss;
      processGenericRecord((GenericRecord)(record.get("candidate")),
                           "candidate",
                           "candid",
                           true,
                           v,
                           "has",
                           COLUMNS_CANDIDATE,
                           objectId);
      Vertex vv = vertex(record, "prv_candidates", null, "create");
      _gr.addEdge(v, vv, "has");    
      Array a = (Array)record.get("prv_candidates");
      if (a != null) {
        for (Object o : a) {
          _nPrvCandidates++;
          processGenericRecord((GenericRecord)o,
                               "prv_candidate",
                               "candid",
                               true,
                               vv,
                               "holds",
                               COLUMNS_CANDIDATE,
                               objectId);
          } 
        }
      processCutout(record, v, objectId, _jd); // _jd taken from candidate
      }
    else {
      log.error("Failed to create alert from " + record);
      }
    timer("alerts processed", _n, _reportLimit, _commitLimit); 
    return v;
    }
   
  /** Process <em>Avro</em> PCA.
    * Only create PCAs for already existing sources.
    * Do not verify if the PCA already exists (so multiple
    * PCAs fvor one source may be created.
    * @param record The full PCA {@link GenericRecord}.
    * @return       The created {@link Vertex}. */
  public Vertex processPCA(GenericRecord record) {
    _n++;
    String objectId = record.get("objectId").toString();
    Vertex v = g().addV("PCA").property("lbl", "PCA").property("objectId", objectId).next();
    Array<Double> array = (Array<Double>)(record.get("pca"));
    Iterator<Double> it = array.iterator();
    short pcai = 0;
    while (it.hasNext()) {
      v.property(PCAS[pcai++], it.next());
      }
    v.property("importDate", _date);      
    timer("pcas processed", _n, _reportLimit, _commitLimit); 
    return v;
    }
   
  /** Process <em>Avro</em> {@link GenericRecord}.
    * @param record       The {@link GenericRecord} to process.
    * @param name         The name of new {@link Vertex}.
    * @param idName       The name of the unique identifying field.
    * @param tryDirection Whether try created <em>Direction</em> property from <em>ra,dec</em> fields.
    * @param mother       The mother {@link Vertex}.
    * @param edgerName    The name of the edge to the mother {@link Vertex}.
    * @param fields       The list of fields to fill. All fields are filled if <code>null</code>
    * @param objectId     The <em>objectId</em> of the containing source.
    * @return             The created {@link Vertex}. */
  private Vertex processGenericRecord(GenericRecord record,
                                      String        name,
                                      String        idName,
                                      boolean       tryDirection,
                                      Vertex        mother,
                                      String        edgeName,
                                      List<String>  fields,
                                      String        objectId) {
    String[] idNameA;
    if (idName == null) {
      idNameA= new String[]{};
      }
    else {
      idNameA = new String[]{idName};
      }
    Map<String, String> values = getSimpleValues(record, getSimpleFields(record, fields, idNameA));
    Vertex v = vertex(record, name, idName, "create");
    if (v == null) {
      return v;
      }
    for (Map.Entry<String, String> entry : values.entrySet()) {
      //log.debug("\t" + entry.getKey() + " = " + entry.getValue());
      v.property(entry.getKey(), entry.getValue());
      }
    if (record.get("dec") != null && record.get("ra") != null) {
      v.property("direction", Geoshape.point(Double.valueOf(record.get("dec").toString()), Double.valueOf(record.get("ra").toString()) - 180));
      }
    _gr.addEdge(mother, v, edgeName);
    if (hbaseUrl() != null) {
      _jd = record.get("jd").toString();
      _gr.attachDataLink(v,
                        "Candidate data",
                        "HBase",
                        _hbaseUrl,
                        "return client.scan('" + objectId + "_" + _jd + "', null, '*', 0, true, true)");
      }
    return v;
    }
    
  /** Process <em>Avro</em> cutout.
    * @param record       The {@link GenericRecord} to process.
    * @param mother       The {@link Vertex} to attach to. 
    * @param jd           The <em>jd</em> of the corresponding candidate.
    */
  private void processCutout(GenericRecord record,
                             Vertex        mother,
                             String        objectId,
                             String        jd) {
    Vertex v = vertex(record, "cutout", null, "create");
    GenericRecord r;
    String fn;
    String key = objectId + "_" + jd;
    byte[] data;
    for (String s : new String[]{"Science", "Template", "Difference"}) { 
      r = (GenericRecord)(record.get("cutout" + s));
      fn = r.get("fileName").toString();
      data = ((ByteBuffer)(r.get("stampData"))).array();
      if (hbaseUrl() != null) {
        _gr.attachDataLink(v,
                       s + " fits",
                       "HBase",
                       _hbaseUrl,
                       "x=client.scan('" + key + "', null, 'b:cutout" + s + "_stampData', 0, false, false).get('" + key + "').get('b:cutout" + s + "_stampData');y=client.repository().get(x);java.util.Base64.getEncoder().encodeToString(y)");
        }
      else if (fitsDir() == null) {
        v.property("cutout" + s + "Fn", fn);
        v.property("cutout" + s,        Base64.getEncoder().encodeToString(data));
        }
      else {
        v.property("cutout" + s + "Fn", "file:" + fitsDir() + "/" + fn);
        writeFits(fn, data);
        }
      }
    _gr.addEdge(mother, v, "has");
    }

  /** Register part of {@link GenericRecord} in <em>HBase</em>.
    * @param record  The {@link GenericRecord} to be registered in <em>HBase</em>.
    * @param fields  The fields to be mapped. */
  private Map<String, String> getSimpleValues(GenericRecord record,
                                              List<String>  fields) {
    Map<String, String> content = new TreeMap<>();
    for (String s : fields) {
      Object o = record.get(s);
      if (o instanceof ByteBuffer) {
        content.put(s, new String(((ByteBuffer)o).array()));
        }
      else if (o != null) {
        content.put(s, o.toString());
        }
      }
    return content;
    }
    
  /** Get {@link Field}s corresponding to simple types
    * and having non-<code>null</code> values.
    * @param record The {@link GenericRecord} to use.
    * @param keeps  The {@link GenericRecord} to report.
    *               Report all if <tt>null</tt>.
    * @param avoids The array of fields names not to report.
    *               Cannot cancel <em>keeps</em> argument.
    * @return       The list of coressponding fields. */
  private List<String> getSimpleFields(GenericRecord record,
                                       List<String>  keeps,
                                       String[]      avoids) {
    List<String> fields = new ArrayList<>();
    Type type;
    String name;
    boolean veto;
    for (Field field : record.getSchema().getFields()) {
      type = field.schema().getType();
      name = field.name();
      veto = false;
      if (keeps == null) {
        }
      else if (!keeps.contains(name)) {
        veto = true;
        }
      else {
        for (String avoid : avoids) {
          if (name.equals(avoid) || record.get(name) == null) {
            veto = true;
            }
          }
        }
      if (!veto) {
        if (type == Type.BOOLEAN ||
            type == Type.DOUBLE  ||
            type == Type.FLOAT   ||
            type == Type.LONG    ||
            type == Type.INT     ||
            type == Type.UNION   ||
            type == Type.STRING  ||
            type == Type.BYTES   ||
            type == Type.ARRAY) {
            fields.add(name);
          }
        else {
          log.warn("Skipped: " + name + " of " + type);
          }
        }
      }
    return fields;
    }
    
  /** Create or drop a {@link Vertex} according to chosen strategy.
    * @param record    The full {@link GenericRecord}.
    * @param label     The {@link Vertex} label.
    * @param property  The name of {@link Vertex} property.
    *                  If <tt>null</tt> strategy is ignored and {@link Vertex} is created.
    * @param strategy  The creation strategy: <tt>drop, replace, reuse, skip, create</tt>.
    *                  If anything else, the global strategy b  is used.
    * @return          The created {@link Vertex} or <tt>null</tt>. */
  private Vertex vertex(GenericRecord record,
                        String        label,
                        String        property,
                        String        strategy) {
    if (strategy == null) {
      strategy = "";
      }
    strategy        = strategy.trim();
    boolean drop    = strategy.equals("drop");
    boolean skip    = strategy.equals("skip");   
    boolean create  = strategy.equals("create"); 
    boolean reuse   = strategy.equals("reuse");  
    boolean replace = strategy.equals("replace");
    Vertex v = null;
    // Do nothing
    if (skip) {
      return v;
      }
    // Not unique Vertex
    if (property == null) {
      if (drop) {
        return v;
        }
      else {
        //log.debug("Creating: " + label);
        return g().addV(label).property("lbl", label).next();
        }
      }
    // Unique Vertex
    if (drop || replace) {
      //log.debug("Dropping " + label + ": " + property + " = " + record.get(property));
      _gr.drop(label, property, record.get(property), true);
      }
    if (reuse) {
      //log.info("Getting " + label + ": " + property + " = " + record.get(property));
      v = _gr.getOrCreate(label, property, record.get(property)).next(); // TBD: check uniqueness
      }
    if (create || replace) {
      //log.debug("Creating " + label + ": " + property + " = " + record.get(property));
      v = g().addV(label).property("lbl", label).property(property, record.get(property)).next();
      }
    return v;
    }
        
  /** Write FITS file.
    * @param fn   The FITS file name.
    * @param data The FITS file content. */
  protected void writeFits(String fn,
                           byte[] data) {
    try {
      Files.write(FileSystems.getDefault().getPath(fitsDir() + "/" + fn), data);
      }
    catch (IOException e) {
      log.error("Cannot write " + fn, e);
      }
    }
    
  /** The directory for FITS files.
    * @return The FITS file directory. */
  protected String fitsDir() {
    return _fitsDir;
    } 
    
  /** The data HBase table url.
    * @return The data HBase table url. */
  protected String hbaseUrl() {
    return _hbaseUrl;
    } 
    
  /** Give number of created alerts/pcas.
    * @return The number of created alerts/pcas. */
  protected int n() {
    return _n;
    }
    
  /** Tell, whether import shpuld be skipped.
    * @return Whether import shpuld be skipped. */
  protected boolean skip() {
    return _skip;
    }
    
  @Override
  public void close() { 
    g().V().has("lbl", "Import").has("importSource", _topFn).has("importDate", _date).property("complete", true).property("n", _n).next();
    commit();
    log.info("Import statistics:");
    log.info("\talerts/pcas: " + _n);
    log.info("\tprv_candidates: " + _nPrvCandidates);
    log.info("Imported at " + _date);
    super.close();
    }
    
  /** Set new {@link Date}. */
  protected void now() {
    _date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()).toString();
    }
   
  private static List<String> COLUMNS_ALERT = Arrays.asList(new String[] {"cdsxmatch",
                                                                          "mulens",
                                                                          "objectId",
                                                                          "rf_kn_vs_nonkn",
                                                                          "rf_snia_vs_nonia",
                                                                          "roid",
                                                                          "snn_snia_vs_nonia",
                                                                          "snn_sn_vs_all",
                                                                          "tracklet"
                                                                          });
   private static List<String> COLUMNS_CANDIDATE = Arrays.asList(new String[] {"candid",
                                                                               "classtar",
                                                                               "diffmaglim",
                                                                               "distnr",
                                                                               "distpsnr1",
                                                                               "DR3Name",
                                                                               "drb",
                                                                               "e_Plx",
                                                                               "fid",
                                                                               "field",
                                                                               "gcvs",
                                                                               "isdiffpos",
                                                                               "jd",
                                                                               "jdendhist",
                                                                               "jdstarthist",
                                                                               "maggaia",
                                                                               "magnr",
                                                                               "magpsf",
                                                                               "magzpsci",
                                                                               "ndethist",
                                                                               "neargaia",
                                                                               "nid",
                                                                               "Plx",
                                                                               "rb",
                                                                               "rcid",
                                                                               "sgscore1",
                                                                               "sigmagnr",
                                                                               "sigmapsf",
                                                                               "ssdistnr",
                                                                               "ssmagnr",
                                                                               "ssnamenr",
                                                                               "vsx"});
   
  private GremlinRecipies _gr;
  
  private String _jd;
  
  private int _reportLimit;
  
  private int _commitLimit;
  
  private String _fitsDir;
  
  private String _hbaseUrl;
  
  private String _dataType;
  
  private boolean _create;
  
  private boolean _reuse;
  
  private boolean _replace;
  
  private boolean _drop;
  
  private boolean _skip;
  
  private int _n = 0;
  
  private int _nPrvCandidates = 0;
  
  private String _date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()).toString();
  
  private boolean _top = true;
  
  private String _topFn;
  
  private static String[] PCAS = new String[]{"pca00",
                                              "pca01",
                                              "pca02",
                                              "pca03",
                                              "pca04",
                                              "pca05",
                                              "pca06",
                                              "pca07",
                                              "pca08",
                                              "pca09",
                                              "pca10",
                                              "pca11",
                                              "pca12",
                                              "pca13",
                                              "pca14",
                                              "pca15",
                                              "pca16",
                                              "pca17",
                                              "pca18",
                                              "pca19",
                                              "pca20",
                                              "pca21",
                                              "pca22",
                                              "pca23",
                                              "pca24"};
     
  private static String VERSION = "ztf-3.2";
    
  /** Logging . */
  private static Logger log = Logger.getLogger(AvroImporter.class);
                                                
  }

