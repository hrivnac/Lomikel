package com.Lomikel.Utils;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>ByteArray</code> presents an array of bytes as an object.
  * <p>This is a zero-copy wrapper, not an immutable byte value. The constructor
  * retains the caller's array, and {@link #bytes()} exposes that same mutable
  * array. Mutations through either reference are visible through the other.
  * Callers needing a stable snapshot should copy the array at the boundary
  * where that snapshot is required.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class ByteArray {
  
  /** Wrap an array without copying it.
    * @param bytes The caller-owned array, retained by reference. */
  public ByteArray(byte[] bytes) {
    _bytes = bytes;
    }
    
  /** Return the wrapped array without copying it.
    * @return The same mutable array supplied to the constructor; changes to it
    *         also change the contents visible through this wrapper. */
  public byte[] bytes() {
    return _bytes;
    }
    
  private byte[] _bytes;
  
  /** Logging . */
  private static Logger log = LogManager.getLogger(ByteArray.class);

  }
