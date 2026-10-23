package com.Lomikel.Utils;

// Java
import java.io.File;
import java.io.FileInputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>StringFile</code> gives File as String.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class StringFile {
    
  /** Create.
    * @param fn The file path.
    * @throws LomikelException If file can't be read. */      
  public StringFile(String fn) throws LomikelException {
    this(new File(fn));
    }
    
  /** Create.
    * Reads UTF-8 text. CR, LF, and CRLF line endings become LF; each line
    * receives a trailing LF, including an unterminated final line.
    * An empty file stays empty. Resources are closed even if reading fails.
    * @param file The file.
    * @throws LomikelException If file can't be read. */      
  public StringFile(File file) throws LomikelException {
    if (file.exists()) {
      StringBuilder buffer = new StringBuilder();
      try (BufferedReader reader = new BufferedReader(
             new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
        String line;
        while ((line = reader.readLine()) != null) {
          buffer.append(line).append('\n');
          }
        _content = buffer.toString();
        }
      catch (FileNotFoundException e) {
        throw new LomikelException("File " + file.getPath() + " not found !", e);
        }
      catch (IOException e) {
        throw new LomikelException("File " + file.getPath() + " cannot be read !", e);
        }
      }
    }  
    
  /** Give the contained {@link String}.
    * @return The contained {@link String}.
    *         <tt>null</tt>, if non-existent file. */
  public String content() {
    return _content;
    }
    
  /** Give the contained {@link String}.
    * @return The contained {@link String}.
    *         Empty, if non-existent file. */
  @Override
  public String toString() {
    return _content == null ? "" : _content;
    }
     
  private String _content = null;   

  /** Logging . */
  private static Logger log = LogManager.getLogger(StringFile.class);
                                                
  }
