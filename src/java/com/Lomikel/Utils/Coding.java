package com.Lomikel.Utils;

// Java
import java.util.Base64;
import java.io.Serializable;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.io.IOException;
import java.lang.ClassNotFoundException;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>Coding</code> handles encoding and decoding of {@link String}s and {@link Object}.
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

  /** Encode {@link Object}.
    * @param o The {@link Object}.
    * @return  The encoded {@link String}.
    * @throws IOException If cannot be serialized. */
  public static String serialize(Serializable o) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(baos);
    oos.writeObject(o);
    oos.close();
    return Base64.getEncoder().encodeToString(baos.toByteArray()); 
    }
    
  /** Decode {@link Object}.
    * @param s The encoded {@link String}.
    * @return  The decoded {@link Object}. */
  public static Serializable deserialize(String s) throws IOException, ClassNotFoundException {
    byte[] data = Base64.getDecoder().decode(s);
    ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
    Serializable o = (Serializable)(ois.readObject());
    ois.close();
    return o;
    }
    
   /** Split compactified array of keys.
     * @param compactKey The compactified array of keys.
     * @return           The split keys. */
  public static String[] spltKey(String compactKey) {
    try {
      return (String[])deserialize(compactKey);
      }
    catch (IOException | ClassNotFoundException e) {
      log.error("Cannot split", e);
      return null;
      }
    }
  
  /** Compact array of keys.
    * @param keyArray The array of keys.
    * @return         The compactified arrays of keys. */
  public static String compactKey(String[] keyArray) {
    try {
      return serialize(keyArray);
      }
    catch (IOException e) {
      log.error("Cannot compact", e);
      return null;
      }
    }

  /** Logging . */
  private static Logger log = LogManager.getLogger(Coding.class);
    
  }
