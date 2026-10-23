package com.Lomikel.Utils;

// Java
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>StringResource</code> gives Resource as String.
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a>
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class StringResource {
    
  /** Create.
    * @param resource The resource path. 
    * @param cl       The {@link Class} defining the resourece.
    * @throws LomikelException If resource can't be read. */      
  public StringResource(String resource) throws LomikelException {
    this(resource, StringResource.class);
    }
    
  /** Create.
    * @param resource The resource path. 
    * @throws LomikelException If resource can't be read. */      
  public StringResource(String resource,
                        Class cl) throws LomikelException {
    try {
      InputStream       is  = cl.getClassLoader().getResourceAsStream(resource);
      if (is == null) {
        throw new LomikelException("Resource " + resource + " not found !");
        }
      try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
        StringBuilder buffer = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
          buffer.append(line).append("\n");
          }
        _content = buffer.toString();
        }
      }
    catch (IOException e) {
      throw new LomikelException("Resource " + resource + " cannot be read !", e);
      }
    }

  /** Write the contained {@link String} to a file.
    * @param fn The filename to write the content 
    * @throws LomikelException If resource can't be read. */      
  public void toFile(String fn) throws LomikelException {
    try (BufferedWriter out = Files.newBufferedWriter(Paths.get(fn), StandardCharsets.UTF_8)) {
      out.write(_content);
      }
    catch (Exception e) {
      throw new LomikelException("Cannot write into file " + fn + " !", e);
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
  private static Logger log = LogManager.getLogger(StringResource.class);
                                                
  }
