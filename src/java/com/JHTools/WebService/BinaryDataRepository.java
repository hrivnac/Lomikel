package com.JHTools.WebService;

// Java
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;

// Log4J
import org.apache.log4j.Logger;

/** <code>BinaryDataRepository</code> keeps temporary binary data.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class BinaryDataRepository {

  /** TBD */
	public BinaryDataRepository() {
	  _data = new HashMap<>();
	  }
	  
  /** TBD */
	public void put(String id,
	                byte[] content) {
	  _data.put(id, content);
	  }
	  
  /** TBD */
	public byte[] get(String id) {
	  return _data.get(id);
	  }
	  
  /** TBD */
	public String get64(String id) {
	  return Base64.getEncoder().encodeToString(_data.get(id));
	  }
	  
	/** TBD */
	public String toString() {
	  String result = "BinaryDataRepository(" + _data.size() + ") = {";
	  for (Map.Entry entry : _data.entrySet()) {
      result += entry.getKey() + ",";
      }
	  result += "}";
	  return result;
	  }
	  
  /** TBD */
	private static Map<String, byte[]> _data;

  /** Logging . */
  private static Logger log = Logger.getLogger(BinaryDataRepository.class);
    
  }
