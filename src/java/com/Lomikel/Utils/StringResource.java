package com.Lomikel.Utils;

// Java
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;

// Log4J
import org.apache.log4j.Logger;

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
    * @throws LomikelException If resource can't be read. */      
  public StringResource(String resource) throws LomikelException {
    try {
      InputStream       is  = StringResource.class.getClassLoader().getResourceAsStream(resource);
      InputStreamReader isr = new InputStreamReader(is);
      BufferedReader    br  = new BufferedReader(isr);
      StringBuffer buffer = new StringBuffer();
      while (br.ready()) {
        buffer.append(br.readLine() + "\n");
        }
      br.close();
      isr.close();
      is.close();
      _content = buffer.toString();
      }
    catch (IOException e) {
      throw new LomikelException("Resource " + resource + " cannot be read !", e);
      }
    }

  /** Write the contained {@link String} to a file.
    * @param fn The filename to write the content 
    * @throws LomikelException If resource can't be read. */      
  public void toFile(String fn) throws LomikelException {
    try {
      FileWriter fstream = new FileWriter(fn);
      BufferedWriter out = new BufferedWriter(fstream);
      out.write(_content);
      out.close();
      }
    catch (Exception e){
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
  private static Logger log = Logger.getLogger(StringResource.class);
                                                
  }
