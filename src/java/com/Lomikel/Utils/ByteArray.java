package com.Lomikel.Utils;

// Log4J
import org.apache.log4j.Logger;

/** <code>ByteArray</code> presents array of bytes as an Object.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class ByteArray {
  
  /** Create.
    * @param bytes The array of bytes to contain. */
  public ByteArray(byte[] bytes) {
    _bytes = bytes;
    }
    
  /** Give the content.
    * @return The content. */
  public byte[] bytes() {
    return _bytes;
    }
    
  private byte[] _bytes;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(ByteArray.class);

  }
