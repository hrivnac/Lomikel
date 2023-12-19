package com.Lomikel.Parquet;

import com.Lomikel.Utils.Init;
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

// Java
import java.io.File;
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

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.format.converter.ParquetMetadataConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.Type;

// Log4J
import org.apache.log4j.Logger;

/** <code>ParquetReader</code> reads <em>Parquet</em> files.
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class ParquetReader {
        
  /** Create reader.
    * @param url               The {@link FileSystem} url as <code>hdfs://ip:port<code>.
    * @throws IOException      If problem with file reading.
    * @throws LomikelException If anything wrong. */
  public ParquetReader(String url) throws IOException, LomikelException {
    _conf = new Configuration();
    _conf.set("fs.defaultFS", url);
    _fs = FileSystem.get(_conf);
    }
     
  /** Process <em>Parquet</em> alert file or directory with files (recursive).
     * @param fn                The filename of the data file
     *                          or directory with files.
     * @param ext               The extention of the parquet data file.
     * @throws IOException      If problem with file reading.
     * @throws LomikelException If anything wrong. */
  public void process(String fn,
                      String ext) throws IOException, LomikelException {
    log.info("Loading " + fn);
    Path path = new Path(fn);
    if (_fs.isDirectory(path)) {
      processDir(fn, "parquet");
      return;
      }
    else if (!_fs.isFile(path)) {
      log.error("Not a file/directory: " + fn);
      return;
      }
    processFile(path);
    }
    
  /** Process directory with <em>Avro</em> alert files (recursive).
    * @param dirFn        The dirname of directiory with data file.
    * @param fileExt      The extention of the parquet data file.
    * @throws IOException If problem with file reading. */
  public void processDir(String dirFn,
                         String fileExt) throws IOException {  
    log.info("Loading directory " + dirFn);
    Path path = new Path(dirFn);
    Path p;
    int i = 0;
    for (FileStatus fileStatus : _fs.listStatus(path)) {
      p = fileStatus.getPath();
      if (_fs.isDirectory(p)) {
        processDir(dirFn + "/" + p.getName(), fileExt);
        }
      else if (p.getName().endsWith("." + fileExt)) {
        try {
          process(dirFn + "/" + p.getName(), fileExt);
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
    log.info("" + i + " files loaded");
    }
    
  /** Process <em>Parquet</em> file .
     * @param path         The data file.
     * @throws IOException If problem with file reading. */
  public void processFile(Path path) throws IOException {
    log.info("Loading file " + path);
    ParquetMetadata readFooter = ParquetFileReader.readFooter(_conf, path, ParquetMetadataConverter.NO_FILTER);
    MessageType schema = readFooter.getFileMetaData().getSchema();
    ParquetFileReader r = new ParquetFileReader(_conf, path, readFooter);
    PageReadStore pages = null;
    Map<String, List<String>> props = new TreeMap<>();
    while (null != (pages = r.readNextRowGroup())) {
      final long rows = pages.getRowCount();
      log.info("Reading " + rows + " rows");      
      final MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
      final RecordReader<Group> recordReader = columnIO.getRecordReader(pages, new GroupRecordConverter(schema));
      String sTemp = "";
      Group g;
      int i = 0;
      while ((g = recordReader.read()) != null && ++i < rows) {
        processGroup(g);
        }
      }
    } 
    
  /** Process {@link Group}. Runs recursively.
    * @param g     The {@link Group} to process. */
  private void processGroup(Group                     g) {
    SimpleGroup sg;
    GroupType type;
    String[] jt;
    int n;
    String name = null;
    if (g instanceof SimpleGroup) {
      sg = (SimpleGroup)g;
      type = sg.getType();
      n = type.getFieldCount();
      for (int i = 0; i < n; i++) {
        for (int j = 0; j < g.getFieldRepetitionCount(i); j++) {
          jt = type.getType(i).toString().split(" "); // optionality, type, name, ...
          switch (jt[1]) {
            case "int32":
              addToList(jt[2], "" + g.getInteger(i, j));
              break;
            case "int64":
              addToList(jt[2], "" + g.getLong(i, j));
              break;
            case "int96":
              addToList(jt[2], "" + int96toTimestamp(g.getInt96(i, j).getBytes()));
              break;
            case "float":
              addToList(jt[2], "" + g.getFloat(i, j));
              break;
            case "double":
              addToList(jt[2], "" + g.getDouble(i, j));
              break;
            case "binary":
              if (jt.length == 4 && jt[3].equals("(STRING)")) {
                addToList(jt[2], g.getString(i, j));
                }
              else {
                addToList(jt[2], Base64.getEncoder().encodeToString(g.getBinary(i, j).getBytes()));
                }
              break;
            case "group":
              name = type.getFieldName(i);
              if (name.equals("list")) {
                processGroup(g.getGroup(i, j).getGroup(0, 0));
                }
              else {
                processGroup(g.getGroup(i, j));
                }
              break;
            default:
              log.error("Uncovered  type of " + type.getType(i).toString());
            }
          }
        }
      }
    }

  /** Add value to {@link Map} of values.
    * @param name  The name of value to add to the {@link List} of its values.
    * @param value The value to add to the {@link List} of values.*/
  private void addToList(String                    name,
                         String                    value) {
    List<String> list;
    if (_props.containsKey(name)) {
      list = _props.get(name);
      }
    else {
      list = new ArrayList<>();
      }
    list.add(value);
    _props.put(name, list);
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
 
  /** Give all read properties.
    * @return The read properties as name -&gt; list of values. */
  public Map<String, List<String>> props() {
    return _props;
    }
    
  private Map<String, List<String>> _props = new TreeMap<>();       
  
  private static Configuration _conf;
  
  private static FileSystem _fs;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(ParquetReader.class);
                                                
  }
