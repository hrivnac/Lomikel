package com.Lomikel.HBaser;

// Java
import java.util.Map;
import java.util.HashMap;
import java.util.Base64;

// Log4J
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/** <code>BinaryDataRepository</code> keeps temporary binary data.
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class BinaryDataRepository {

  /** Create. */
	public BinaryDataRepository() {
	  log.info("Storing binary data in static memory");
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
	  return Base64.getEncoder().encodeToString(get(id));
	  }
	  
	@Override
	public String toString() {
	  StringBuffer resultB = new StringBuffer("BinaryDataRepository(" + _data.size() + ") = {");
	  for (Map.Entry entry : _data.entrySet()) {
      resultB.append(entry.getKey())
             .append(",");
      }
	  resultB.append("}");
	  return resultB.toString();
	  }
	  
	private static Map<String, byte[]> _data;

  /** Logging . */
  private static Logger log = LogManager.getLogger(BinaryDataRepository.class);
    
  }
