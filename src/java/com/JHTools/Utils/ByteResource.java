package com.JHTools.Utils;

// Java
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;

/** <code>ByteResource</code> gives Resource as Byte Stream.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class ByteResource {
  
  /** Create.
    * @param resource The resource path. */    
  public ByteResource(String resource) {
    InputStream is  = Info.class.getClassLoader().getResourceAsStream(resource);
    try {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      int reads = is.read();      
      while(reads != -1){
        baos.write(reads);
        reads = is.read();
        }   
      _content = baos.toByteArray();
      }
    catch (IOException e) {
      System.err.println("Resource " + resource + " cannot be read !\n" + e);
      return;
      }
    }

  /** Write resource to a file.
    * @param fn The name of the file to write the resoirce to. */
  public void toFile(String fn) {
    try {
      FileOutputStream fos = new FileOutputStream(fn);
      fos.write(_content);
      }
    catch (IOException e) {
      System.err.println("Cannot write to file " + fn + " !\n" + e);
      }
    }
    
  /** Show the content. */
  public String toString() {
    return new String(_content);
    }
     
  private byte[] _content; 
                                                
  }
