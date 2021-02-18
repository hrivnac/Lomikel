package com.Lomikel.Utils;

// Log4J
import org.apache.log4j.Logger;

/** <code>ByteArray</code> presents Array of bytes as an Object.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class ByteArray {
  
  /** TBD */
  public ByteArray(byte[] bytes) {
    _bytes = bytes;
    }
    
  /** TBD */
  public byte[] bytes() {
    return _bytes;
    }
    
  private byte[] _bytes;
  
  /** Logging . */
  private static Logger log = Logger.getLogger(ByteArray.class);

  }
