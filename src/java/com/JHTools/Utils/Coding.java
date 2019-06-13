package com.JHTools.Utils;

// Java
import java.util.Base64;

/** <code>Coding</code> handles encoding and decoding of {@link String}s.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class Coding {
    
  /** Encode string.
    * @param s The string.
    * @return The encoded string. */
  public static String encode(String s) {
    return Base64.getEncoder().encodeToString(s.getBytes());
    }
    
  /** Decode REST string.
    * @param s The encoded string.
    * @return The decoded string. */
  public static String decode(String s) {
    return new String(Base64.getDecoder().decode(s));
    }
    
  }
