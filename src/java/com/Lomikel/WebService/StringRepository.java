package com.Lomikel.WebService;

// Java
import java.util.Map;
import java.util.HashMap;

// Log4J
import org.apache.log4j.Logger;

/** <code>StringRepository</code> communicates {@link String} data between applets..
  * @opt attributes
  * @opt operations
  * @opt types
  * @opt visibility
  * @author <a href="mailto:Julius.Hrivnac@cern.ch">J.Hrivnac</a> */
public class StringRepository {
    
  /** Add data.
    * @param name    The name of the data.
    * @param content The data content. */
  public void add(String name,
                  String content) {
    _data.put(name, content);
    }
    
  /** Give data.
    * @param name The name of the data.
    * @return     The data content. */
  public String get(String name) {
    return _data.get(name);
    }
  
  /** Give data and remove them.
    * @param name The name of the data.
    * @return     The data content. */
  public String getAndRemove(String name) {
    String content = _data.get(name);
    _data.remove(name);
    return content;
    }

  /** Clear the repository. */
  public void clear() {
    _data.clear();
    }
    
  private Map<String, String> _data = new  HashMap<>();
  
  /** Logging . */
  private static Logger log = Logger.getLogger(StringRepository.class);

  }
