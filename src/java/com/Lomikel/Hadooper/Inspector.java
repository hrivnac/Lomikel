package com.Lomikel.Hadooper;

import com.Lomikel.Utils.Init;
import com.Lomikel.Utils.Info;
import com.Lomikel.Utils.StringResource;
import com.Lomikel.Utils.DateTimeManagement;
import com.Lomikel.Utils.LomikelException;

// Hadoop
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.MapFile;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

// Java
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Vector;
import java.util.Map;
import java.util.HashMap;

// Log4J
import org.apache.log4j.Logger;

/** <code>Inspector</code> reads HDFS files.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Inspector {
                              
  /** Read/search HDFS files.
    * @param action The type of file or reauired action: <tt>count, time, map, mapkey, mapkeylimits, seq, csv, txt</tt>. 
    * @param fn     The file or directory (for {@link MapFile}s.
    * @param min    The first row to show (negative gives 0).
    * @param max    The last row to show (0 or negative gives maximum value). */  
  public String inspect(String action,
                        String fn,
                        int    min,
                        int    max) throws Exception {
    String result = "";
    if (min <= 0) {
      min = 0;
      }
    if (max <= 0) {
      max = Integer.MAX_VALUE;
      }
    Configuration config = new Configuration();
    if (action.equals("count")) {
      MapFile.Reader reader = new MapFile.Reader(FileSystem.get(config), fn, config);
      WritableComparable key = (WritableComparable)reader.getKeyClass().newInstance();
      Writable value = (Writable)reader.getValueClass().newInstance();
      long i = 0;
      while (reader.next(key, value)) {
        i++;
        }
      reader.close();
      result += "nol = " + i + "\n";
      }
    else if (action.equals("time")) {
      long t = FileSystem.get(config).getFileStatus(new Path(fn)).getModificationTime();
      result += "modified: " + DateTimeManagement.time2String(t) + "\n";
      }
    else if (action.equals("map")) {
      MapFile.Reader reader = new MapFile.Reader(FileSystem.get(config), fn, config);
      WritableComparable key = (WritableComparable)reader.getKeyClass().newInstance();
      Writable value = (Writable)reader.getValueClass().newInstance();
      int i = 0;
      StringBuffer res = new StringBuffer();
      while (reader.next(key, value) && i <= max) {
        if (i >= min) {
          res.append(key + ":\t" + value + "\n");
          }
        i++;
        }
      result = res.toString();
      reader.close();
      }
    else if (action.equals("mapkey")) {
      MapFile.Reader reader = new MapFile.Reader(FileSystem.get(config), fn, config);
      WritableComparable key = (WritableComparable)reader.getKeyClass().newInstance();
      Writable value = (Writable)reader.getValueClass().newInstance();
      int i = 0;
      while (reader.next(key, value) && i <= max) {
        if (i >= min) {
          result += key + "\n";
          }
        i++;
        }
      reader.close();
      }
    else if (action.equals("mapkeylimits")) {
      MapFile.Reader reader = new MapFile.Reader(FileSystem.get(config), fn, config);
      WritableComparable key = (WritableComparable)reader.getKeyClass().newInstance();
      Writable value = (Writable)reader.getValueClass().newInstance();
      int i = 0;
      reader.next(key, value);
      result += "first key: " + key + "\n";
      reader.finalKey(key);
      result += "last  key: " + key + "\n";
      reader.close();
      }
    else if (action.equals("seq")) {
      SequenceFile.Reader reader = new SequenceFile.Reader(FileSystem.get(config), new Path(fn), config);
      WritableComparable key = (WritableComparable)reader.getKeyClass().newInstance();
      Writable value = (Writable)reader.getValueClass().newInstance();
      int i = 0;
      while (reader.next(key, value) && i <= max) {
        if (i >= min) {
          result += key + ":\t" + value + "\n";
          }
        i++;
        }
      reader.close();     
      }
	  else if (action.equals("csv")) {
      SequenceFile.Reader reader = new SequenceFile.Reader(FileSystem.get(config), new Path(fn), config);
      WritableComparable key = (WritableComparable)reader.getKeyClass().newInstance();
      Writable value = (Writable)reader.getValueClass().newInstance();
      int i = 0;
      while (reader.next(key, value) && i <= max) {
        if (i >= min) {
         result += key + ":\t" + value;
          }
        i++;
        }
      reader.close();     
      }
    else if (action.equals("txt")) {
      FileSystem fs = FileSystem.get(new Configuration());
      BufferedReader br = new BufferedReader(new InputStreamReader(fs.open(new Path(fn))));
      String line = br.readLine();
      int i = 0;
      while (line != null && i <= max) {
        if (i >= min) {
          result += line + "\n";
          }
        i++;
        line = br.readLine();
        }   
      }
    return result;
    }
                                       
  /** Logging . */
  private static Logger log = Logger.getLogger(Inspector.class);

  }
