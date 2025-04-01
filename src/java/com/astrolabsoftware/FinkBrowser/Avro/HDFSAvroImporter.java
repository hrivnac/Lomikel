package com.astrolabsoftware.FinkBrowser.Avro;

import com.Lomikel.Utils.Init;

// Lomikel
import com.Lomikel.Utils.LomikelException;

// Avro
import org.apache.avro.io.DatumReader;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.file.SeekableInput;
import org.apache.avro.mapred.FsInput;

// Hadoop
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;

// Tinker Pop
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.unfold;

// Janus Graph
import org.janusgraph.core.JanusGraph;

// Java
import java.io.IOException;

// Log4J
import org.apache.log4j.Logger;

/** <code>AvroImporter</code> imports <em>Avro</em> files from HDFS into <em>JanusGraph</em>.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HDFSAvroImporter extends AvroImporter {
        
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
    Init.init("HDFSAvroImporter");
    if (args.length != 8) {
      log.error("HDFSAvroImporter.exe.jar <JanusGraph properties> [<file>|<directory>] <hbaseUrl> <report limit> <commit limit> [create|reuse|drop] [alert|pca]");
      System.exit(-1);
      }
    try {
      HDFSAvroImporter importer = new HDFSAvroImporter(            args[0],
                                                       new Integer(args[4]),
                                                       new Integer(args[5]),
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
    * @param strategy    The creation strategy. <tt>dro If <tt>null<tt>, then considering as <tt>alert</tt>.p,replace,getOrCreate</tt>. 
    * @param fitsDir     The directory for FITS files. If <tt>null</tt>or empty, FITS are included in the Graph. Ignored if HBase url set.
    * @param hbaseUrl    The url for HBase table with full data as <tt>ip:port:table:schema</tt>. May be <tt>null</tt> or empty.
    * @param dataType    The data type, <tt>alert|pca</tt>. If <tt>null<tt>, then considering as <tt>alert</tt>.*/  
  public HDFSAvroImporter(String properties,
                          int    reportLimit,
                          int    commitLimit,
                          String strategy,
                          String fitsDir,
                          String hbaseUrl,
                          String dataType) {
    super(properties, reportLimit, commitLimit, strategy, fitsDir, hbaseUrl, dataType);
    }
        
  @Override
  public void processDir(String dirFN,
                         String fileExt) throws IOException {  
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
    timer("alerts created", n(), -1, -1);      
    log.info("" + i + " files loaded from " + dirFN);
    }
     
  @Override
  public void process(String fn) throws IOException, LomikelException {
    log.info("Loading " + fn);
    register(fn);
    _conf = new Configuration();
    _fs = FileSystem.get(_conf);
    Path path = new Path(fn);
    if (_fs.isDirectory(path)) {
      processDir(fn, "avro");
      return;
      }
    else if (!_fs.isFile(path)) {
      log.error("Not a file/directory: " + fn);
      return;
      }
    processFile(path);
    }
    
  /** Process <em>Avro</em> alert file .
     * @param path The data file.
     * @throws IOException If problem with file reading. */
  public void processFile(Path path) throws IOException {
    DatumReader<GenericRecord> datumReader = new GenericDatumReader<GenericRecord>();
    SeekableInput input = new FsInput(path, _conf);
    DataFileReader<GenericRecord> dataFileReader = new DataFileReader<GenericRecord>(input, datumReader);
    GenericRecord record = null;
    while (dataFileReader.hasNext()) {
      record = dataFileReader.next(record);
      processRecord(record);
      }
    dataFileReader.close();
    }     
    
  @Override
  protected void writeFits(String fn,
                           byte[] data) {
    try {
      FSDataOutputStream out = _fs.create(new Path(fitsDir() + "/" + fn));
      out.write(data);
      out.close();
      }
    catch (IOException e) {
      log.error("Cannot write " + fn, e);
      }
    }
    
  private Configuration _conf;
  
  private FileSystem _fs;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(HDFSAvroImporter.class);
                                                
  }
