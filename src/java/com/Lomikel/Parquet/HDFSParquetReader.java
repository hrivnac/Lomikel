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
import org.apache.log4j.Logger;

/** <code>HDFSParquetReader</code> reads <em>Parquet</em> files from HDFS.
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class HDFSParquetReader extends ParquetReader {
        
  /** TBD */
  public HDFSParquetReader() throws IOException, FileNotFoundException {
    super();
    _conf = new Configuration();
    _fs = FileSystem.get(_conf);
    processDir("/user/julien.peloton/graph_index_0000001.parquet");
    }

  /** Process directory with <em>Parquet</em> alert files.
    * @param dirFN The dirname of directiory with data file.
    * @throws IOException           If problem with file reading.
    * @throws FileNotFoundException If problem with file founding. */
  public void processDir(String dirFN) throws IOException, FileNotFoundException {  
    log.info("Loading directory " + dirFN);
    Path path = new Path(dirFN);
    Path p;
    int i = 0;
    for (FileStatus fileStatus : _fs.listStatus(path)) {
      p = fileStatus.getPath();
      if (_fs.isDirectory(p)) {
        processDir(dirFN + "/" + p.getName());
        }
      else {
        //try {
          //process(dirFN + "/" + p.getName());
          i++;
        //  }
        //catch (LomikelException e) {
        //  log.error("Failed to process " + p, e);
        //  }
        }
      }
    log.info("" + i + " files loaded");
    }
    
  private Configuration _conf;
  
  private FileSystem _fs;
    
  /** Logging . */
  private static Logger log = Logger.getLogger(HDFSParquetReader.class);
                                                
  }
