package com.Lomikel.Utils;

// Java
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

// Log4J
import org.apache.log4j.Logger;

/** <code>StringFile</code> gives File as String.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class StringFile {
    
  /** Create.
    * @param fn The file path.
    * @throws CommonException If file can't be read. */      
  public StringFile(String fn) throws CommonException {
    this(new File(fn));
    }
    
  /** Create.
    * @param file The file.
    * @throws CommonException If file can't be read. */      
  public StringFile(File file) throws CommonException {
    if (file.exists()) {
      FileInputStream     fis = null;
      BufferedInputStream bis = null;
      DataInputStream     dis = null;
      StringBuffer buffer = new StringBuffer();
      try {
        fis = new FileInputStream(file);
        bis = new BufferedInputStream(fis);
        dis = new DataInputStream(bis);
        while (dis.available() != 0) {
          buffer.append(dis.readLine() + "\n");
          }
        fis.close();
        bis.close();
        dis.close();
        _content = buffer.toString();
        }
      catch (FileNotFoundException e) {
        throw new CommonException("File " + file.getPath() + " not found !", e);
        }
      catch (IOException e) {
        throw new CommonException("File " + file.getPath() + " cannot be read !", e);
        }
      }
    }

  /** Give the contained {@link String}.
    * @return The contained {@link String}.
    *         Empty, if non-existent file. */
  @Override
  public String toString() {
    return _content;
    }
     
  private String _content = "";   

  /** Logging . */
  private static Logger log = Logger.getLogger(StringFile.class);
                                                
  }
