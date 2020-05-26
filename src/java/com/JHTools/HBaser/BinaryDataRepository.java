package com.JHTools.HBaser;

// Java
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;
import java.io.File;
import java.nio.file.Files;

// Log4J
import org.apache.log4j.Logger;

/** <code>BinaryDataRepository</code> keeps temporary binary data.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class BinaryDataRepository {

  /** Create. */
	public BinaryDataRepository() {
	  _data = new HashMap<>();
	  }
	  
	/** Cler the repository. */
  public void clear() {
    _data.clear();
    }
	  
  /** Put in a <tt>byte[]</tt> entry.
    * @param id       The entry id.
    * @param content  The entry content. */
	public void put(String id,
	                byte[] content) {
	  _data.put(id, content);
	  }
	  
	/** Get a <tt>byte[]</tt> entry.
	  * @param  id      The entry id.
    * @return content The entry content. */
	public byte[] get(String id) {
	  return _data.get(id);
	  }
	  
	/** Get a <tt>byte[]</tt> entry, encoded in {@link Base64}.
	  * @param  id      The entry id.
    * @return content The entry content, encoded in {@link Base64}. */
	public String get64(String id) {
	  return Base64.getEncoder().encodeToString(_data.get(id));
	  }
	  
	@Override
	public String toString() {
	  String result = "BinaryDataRepository(" + _data.size() + ") = {";
	  for (Map.Entry entry : _data.entrySet()) {
      result += entry.getKey() + ",";
      }
	  result += "}";
	  return result;
	  }
	  
	private static Map<String, byte[]> _data;

  /** Logging . */
  private static Logger log = Logger.getLogger(BinaryDataRepository.class);
    
  }
